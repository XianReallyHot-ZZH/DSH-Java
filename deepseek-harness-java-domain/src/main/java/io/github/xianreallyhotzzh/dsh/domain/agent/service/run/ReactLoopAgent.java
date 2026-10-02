package io.github.xianreallyhotzzh.dsh.domain.agent.service.run;

import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.AgentStatus;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.InboxTarget;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.Inbox;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.FinishReason;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.GenerateOptions;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;
import io.github.xianreallyhotzzh.dsh.domain.llm.service.BlockAssembler;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.MessageSource;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 核心 Agent 驱动器（L03 单回合单步子集）。
 * <p>
 * 执行流程（与 vendor 的 send/kick/turn/step 骨架一致）：
 * 1. 收到收件箱消息后唤醒驱动器；
 * 2. 打开回合，流式调用 LLM 并在内部阻塞消费为完整结果；
 * 3. 回合结束回到 Idle。
 * <p>
 * 多步扩展点（保留位，随后续课回归 vendor 形态）：
 * Phase 三态与 wakeRequested 重入（L04）、step 内工具调用续步循环（L06）、
 * 受控续写与上下文预算裁剪（L08）、流式 sink 直通（L05）。
 * <p>
 * LLM 上游失败不向调用方抛异常：收敛为一条「⚠️ LLM 调用失败」的助手消息
 * （有部分文本时保留已收到的内容），HTTP 信封保持 00000。
 */
public class ReactLoopAgent implements AgentRun {

    private static final Logger log = LoggerFactory.getLogger(ReactLoopAgent.class);

    /** 驱动器线程池：守护线程，避免空转线程阻止 JVM 退出（与 vendor 的差异见 lesson 文档）。 */
    private static final ExecutorService DRIVER = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "react-loop-driver");
        thread.setDaemon(true);
        return thread;
    });

    private final String id;
    private final AgentOptions options;
    private final String cwd;
    private final SessionLog session;
    private final Inbox inbox;
    private final ILlmRuntimePort llm;

    private volatile boolean running;
    private volatile long turnCount = 0;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicReference<CompletableFuture<Void>> activityDone =
            new AtomicReference<>(CompletableFuture.completedFuture(null));

    public ReactLoopAgent(
            String id,
            AgentOptions options,
            String cwd,
            SessionLog session,
            Inbox inbox,
            ILlmRuntimePort llm
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.options = Objects.requireNonNull(options, "options");
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        this.session = Objects.requireNonNull(session, "session");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    // ── AgentRun 接口实现 ──────────────────────────────────────

    @Override
    public String id() {
        return id;
    }

    @Override
    public AgentOptions options() {
        return options;
    }

    @Override
    public SessionLog session() {
        return session;
    }

    @Override
    public synchronized AgentStatus status() {
        return running ? AgentStatus.RUNNING : AgentStatus.IDLE;
    }

    /**
     * 把一条消息写入收件箱，并按需唤醒驱动器。
     * <p>数据示例：message="你好"，target=NEXT_TURN，wakeup=true，空闲态下立即开启一个回合。
     */
    @Override
    public synchronized void send(Message message, InboxTarget target, boolean wakeup) {
        inbox.append(target, message);
        if (wakeup) {
            wakeDriver();
        }
    }

    /** 返回当前活动完成后的 Future；空闲状态下返回已完成 Future。 */
    @Override
    public CompletableFuture<Void> whenIdle() {
        return activityDone.get();
    }

    @Override
    public CompletableFuture<Void> dispose() {
        disposed.set(true);
        return whenIdle();
    }

    // ── 驱动器 ───────────────────────────────────────────────────

    /**
     * 唤醒驱动器：空闲则立即驱动，运行中则直接返回（本回合结束后由 kick 循环
     * 检查收件箱继续；L04 引入 wakeRequested 显式标记与 Maintenance 相位）。
     */
    private synchronized void wakeDriver() {
        if (disposed.get()) return;
        if (running) return;
        if (!inbox.hasPending()) return;
        running = true;
        CompletableFuture<Void> activity = kick();
        activityDone.set(activity);
    }

    /**
     * 持续处理回合，直到收件箱为空。
     * <p>L03 场景下一次请求只投递一条消息，即单回合；多回合连续消费的骨架在此就位。
     */
    private CompletableFuture<Void> kick() {
        return CompletableFuture.runAsync(() -> {
            log.info("[kick] Agent={} 驱动器启动，开始消费收件箱", id);
            try {
                while (true) {
                    if (disposed.get()) break;
                    if (!inbox.hasPending()) {
                        log.info("[kick] Agent={} 收件箱已空，turn 循环自然结束（共 {} 轮）", id, turnCount);
                        break;
                    }
                    turn();
                }
            } finally {
                synchronized (ReactLoopAgent.this) {
                    running = false;
                }
            }
        }, DRIVER);
    }

    // ── 回合处理 ─────────────────────────────────────────────────

    /**
     * 执行一个回合：抢占用户消息 → 单次 step → 记录助手回复。
     * <p>step 内的多步循环（工具调用续步）是 L06 的扩展点；本课一个回合恰好一步。
     */
    private void turn() {
        long turn = turnCount + 1;
        turnCount = turn;
        log.info("[turn] ▶ Agent={} Turn#{} 开始（model={}）", id, turn, options.model());
        try {
            List<Message> claimed = inbox.claim(InboxTarget.NEXT_TURN, (int) turn);
            for (Message message : claimed) {
                session.append(message);
            }
            Message assistant = step(turn);
            session.append(assistant);
            log.info("[turn] ◀ Agent={} Turn#{} 完成", id, turn);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[turn] ⚠ Agent={} Turn#{} 被中断", id, turn);
        } catch (Exception e) {
            // 回合内异常不冒泡到 Future：记日志后结束本回合（L04 以 turnEnd/Error 事件落账）
            log.error("[turn] ✗ Agent={} Turn#{} 发生未捕获异常：{}", id, turn, e.getMessage(), e);
        }
    }

    // ── 步处理 ─────────────────────────────────────────────────

    /**
     * 执行一个 step：构建请求参数 → 流式调用 LLM 并阻塞消费为完整结果 → 组装助手消息。
     * <p>流式在此是内部实现细节：分片实时到达，但调用方（Collect 节点）拿到的是
     * 阻塞后的完整回合结果；增量直通（streamDeltaSink）是 L05 的扩展点。
     */
    private Message step(long turn) throws InterruptedException {
        GenerateOptions generateOptions = buildRequest();
        BlockAssembler assembler = new BlockAssembler();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> streamError = new AtomicReference<>();

        llm.stream(generateOptions).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamChunk chunk) {
                assembler.push(chunk);
            }

            @Override
            public void onError(Throwable throwable) {
                streamError.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        done.await();

        MessageSource.ModelMessageSource source = new MessageSource.ModelMessageSource(
                options.provider(), options.model());

        // 防御分支：端口契约承诺异常收敛为 Error Finish，正常不会走到这里
        if (streamError.get() != null) {
            log.error("[step] ✗ Agent={} Turn#{} LLM 流异常收尾：{}", id, turn, streamError.get().getMessage());
            return Message.createAssistant(List.of(new TextBlock(
                    "⚠️ LLM 调用失败: " + streamError.get().getMessage())), source);
        }

        FinishReason finish = assembler.finish();
        Message assistant = assembler.message(source);

        if (finish instanceof FinishReason.Error err) {
            // API Key 无效、上游 5xx 等场景流里没有任何文本——替换成可见的错误消息，
            // 避免前端拿到空白结果；已有部分内容时保留已收到的片段。
            String errText = extractText(assistant);
            if (errText == null || errText.isBlank()) {
                log.error("[step] ✗ Agent={} Turn#{} LLM 返回错误：code={} message={}",
                        id, turn, err.failure().code(), err.failure().message());
                return Message.createAssistant(List.of(new TextBlock(
                        "⚠️ LLM 调用失败: " + err.failure().message())), source);
            }
        }
        return assistant;
    }

    // ── 请求构建 ─────────────────────────────────────────

    /** 采样温度：优先系统属性 harness.agent.temperature，其次环境变量；空值或非法数字表示请求中省略。 */
    private static final Double SAMPLING_TEMPERATURE = parseTemperature(
            System.getProperty("harness.agent.temperature",
                    System.getenv("HARNESS_AGENT_TEMPERATURE"))
    );

    private static Double parseTemperature(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构建单次 LLM 请求参数。
     * <p>系统提示词组装器、上下文预算裁剪与受控续写分别是 L08 前后的扩展点，
     * 本课历史即全部会话消息。
     */
    private GenerateOptions buildRequest() {
        return new GenerateOptions(
                options.provider(),
                options.model(),
                options.reasoningEffort(),
                session.deriveMessages(),
                null,               // system：随系统提示词组装器接入
                null,               // tools：L06 工具内核接入
                SAMPLING_TEMPERATURE,
                options.maxTokens(),
                null,               // stop
                session.sessionId(),
                null,               // purpose：L08 压缩/意图接入
                options.baseUrl(),
                options.apiKey(),
                options.protocol()
        );
    }

    // ── 辅助 ─────────────────────────────────────────────

    private static String extractText(Message message) {
        StringBuilder sb = new StringBuilder();
        for (var block : message.content()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text());
            }
        }
        return sb.toString();
    }
}
