package io.github.xianreallyhotzzh.dsh.domain.agent.service.run;

import io.github.xianreallyhotzzh.dsh.domain.agent.contract.outbound.AgentEventListener;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.Phase;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.AgentCancelCause;
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
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionEvent;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.TurnEndReason;
import io.github.xianreallyhotzzh.dsh.domain.session.event.service.SessionEventFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 核心 Agent 驱动器（L04 事件版：Phase 三态 + turn 循环 + 取消/查询）。
 * <p>
 * 执行流程（与 vendor 的 send/wakeDriver/kick/turn/step 骨架一致）：
 * 1. 收到收件箱输入后唤醒；
 * 2. 打开回合（turn/start），逐步（step）流式调用 LLM，每步的事实都以会话事件落账；
 * 3. 直到模型不再请求工具后关闭回合，循环处理直到收件箱为空。
 * <p>
 * 阶段状态机（Phase）：Idle → Running（对话回合）/ Maintenance（维护任务）→ Idle；
 * 运行/维护中收到中断（abort）后，等待中的唤醒（wakeRequested）在回到空闲时补发。
 * <p>
 * L04 子集未含：工具续步循环（L06）、流式 sink 直通（L05）、系统提示词与压缩（L08）、
 * token 上限受控续写（L08，本课 MaxTokens 直接结束回合）。
 * <p>
 * LLM 上游失败不向调用方抛异常：收敛为一条「⚠️ LLM 调用失败」的助手消息
 * （有部分文本时保留已收到的内容），回合以 turn/end(Error) 落账收尾。
 */
public class ReactLoopAgent implements AgentRun {

    private static final Logger log = LoggerFactory.getLogger(ReactLoopAgent.class);

    /** 驱动器线程池：守护线程，避免空转线程阻止 JVM 退出（与 vendor 的差异见 lesson 文档）。 */
    private static final ExecutorService DRIVER = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "react-loop-driver");
        thread.setDaemon(true);
        return thread;
    });

    /** 单回合步数上限：防御模型/工具互相触发造成的无限循环（工具是 L06 的生产者）。 */
    private static final long MAX_STEPS_PER_TURN = 50;

    private final String id;
    private volatile AgentOptions options;
    private final String cwd;
    private final SessionLog session;
    private final Inbox inbox;
    private final ILlmRuntimePort llm;
    private final AgentEventListener events;

    // ── 阶段状态 ──────────────────────────────────────────────
    private volatile Phase phase;
    private final AtomicReference<CompletableFuture<Void>> activityDone =
            new AtomicReference<>(CompletableFuture.completedFuture(null));

    // ── turn 计数器 ────────────────────────────────────────────
    private volatile long turnCount = 0;

    // ── 释放标记 ────────────────────────────────────────────
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public ReactLoopAgent(
            String id,
            AgentOptions options,
            String cwd,
            SessionLog session,
            Inbox inbox,
            ILlmRuntimePort llm
    ) {
        this(id, options, cwd, session, inbox, llm, null);
    }

    public ReactLoopAgent(
            String id,
            AgentOptions options,
            String cwd,
            SessionLog session,
            Inbox inbox,
            ILlmRuntimePort llm,
            AgentEventListener events
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.options = Objects.requireNonNull(options, "options");
        this.cwd = Objects.requireNonNull(cwd, "cwd");
        this.session = Objects.requireNonNull(session, "session");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.llm = Objects.requireNonNull(llm, "llm");
        this.events = events != null ? events : new NoopAgentEventListener();
        this.phase = new Phase.Idle(0);
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

    /** 热更新模型、token 上限等运行参数；已存在的会话日志不变，新参数自下一个 step 生效。 */
    @Override
    public void updateOptions(AgentOptions options) {
        this.options = Objects.requireNonNull(options, "options");
    }

    @Override
    public SessionLog session() {
        return session;
    }

    /** 对外可观测状态：只有 Running 算 RUNNING（维护任务不算对话占用）。 */
    @Override
    public synchronized AgentStatus status() {
        return (phase instanceof Phase.Running) ? AgentStatus.RUNNING : AgentStatus.IDLE;
    }

    /**
     * 取消当前活动并按需清空待处理消息。
     * <p>执行流程：keepInbox=false 清空收件箱（以拼接事件落账）→ 给 Running/Maintenance
     * 阶段设置 abort 标记 → 发出 agent/cancelled 事件。正在执行的 LLM 请求会在安全点停止。
     * <p>数据示例：用户点击「停止」，cause=UserCancel，keepInbox=false，当前回合以
     * turn/end(Aborted) 落账后状态回到 IDLE。
     */
    @Override
    public synchronized void cancel(AgentCancelCause cause, boolean keepInbox) {
        log.info("[cancel] Agent={} 收到取消请求，cause={} keepInbox={}", id, cause, keepInbox);
        if (!keepInbox) {
            inbox.clear();
        }
        if (phase instanceof Phase.Running r) {
            r.abort().set(true);
        } else if (phase instanceof Phase.Maintenance m) {
            m.abort().set(true);
            if (!keepInbox) {
                m.wakeRequested().set(false);
            }
        }
        events.emit("agent/cancelled", Map.of("cause", cause, "keepInbox", keepInbox));
    }

    /** 返回当前活动完成后的 Future；空闲状态下返回已完成 Future。 */
    @Override
    public CompletableFuture<Void> whenIdle() {
        return activityDone.get().thenApply(v -> (Void) null);
    }

    /**
     * 把一条消息写入收件箱，并按需唤醒驱动器。
     * <p>若 Agent 正在取消中，wakeup 消息会改排到 NEXT_TURN，避免与当前回合收尾竞争。
     */
    @Override
    public synchronized void send(Message message, InboxTarget target, boolean wakeup) {
        boolean wasAborting = false;
        if (phase instanceof Phase.Running r) {
            wasAborting = r.abort().get();
            if (wakeup && wasAborting) {
                // 唤醒后若仍处于中止态，则重新归类到下一轮处理
                target = InboxTarget.NEXT_TURN;
            }
        }
        inbox.append(target, message);
        if (wakeup) {
            wakeDriver(wasAborting);
        }
    }

    @Override
    public CompletableFuture<Void> dispose() {
        disposed.set(true);
        cancel(new AgentCancelCause.Disposed(), false);
        return whenIdle();
    }

    // ── 维护相位 ──────────────────────────────────────────────

    /**
     * 在 Agent 空闲时执行压缩、重建等维护任务（消费方随 L08 压缩引擎接入）。
     * <p>执行流程：要求 Idle → 切换到 Maintenance → 执行任务 → 无论成败恢复 Idle；
     * 维护期间收到唤醒请求时，完成后会重新驱动消息循环。
     */
    @Override
    public <T> CompletableFuture<T> runMaintenance(
            java.util.function.Function<AtomicBoolean, CompletableFuture<T>> task
    ) {
        Phase.Maintenance maintenance;
        synchronized (this) {
            if (!(phase instanceof Phase.Idle)) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("runMaintenance requires idle phase"));
            }
            maintenance = new Phase.Maintenance(turnCount);
            phase = maintenance;
        }
        AtomicBoolean abort = new AtomicBoolean(false);
        CompletableFuture<T> work = task.apply(abort);
        CompletableFuture<T> result = work.whenComplete((v, err) -> {
            synchronized (ReactLoopAgent.this) {
                phase = new Phase.Idle(turnCount);
                if (maintenance.wakeRequested().get() && inbox.hasPending()) {
                    wakeDriver(false);
                }
            }
        });
        // 记录当前 activity 的完成状态
        activityDone.set(result.thenApply(v -> (Void) null));
        return result;
    }

    // ── 驱动器 ───────────────────────────────────────────────────

    /**
     * 唤醒驱动器。
     * <p>
     * 执行流程：
     * 1. 如果当前空闲则立即开始处理；
     * 2. 如果正在运行或维护，则记录唤醒请求；
     * 3. 等待当前活动结束后由 kick 的收尾逻辑重放唤醒。
     */
    private synchronized void wakeDriver(boolean wakeAfterAbort) {
        if (disposed.get()) return;
        if (phase instanceof Phase.Idle) {
            if (!inbox.hasPending()) return;
            Phase.Running running = new Phase.Running(turnCount);
            phase = running;
            CompletableFuture<Void> activity = kick(running);
            activityDone.set(activity);
        } else if (phase instanceof Phase.Running r) {
            r.wakeRequested().set(true);
        } else if (phase instanceof Phase.Maintenance m) {
            m.wakeRequested().set(true);
        }
    }

    /**
     * 持续处理回合，直到收件箱为空。
     * <p>
     * 执行流程：
     * 1. 异步启动 turn 循环；
     * 2. 逐轮消费收件箱中的消息；
     * 3. 收件箱清空后进入空闲状态；期间收到过唤醒请求且仍有待处理消息时递归重入。
     */
    private CompletableFuture<Void> kick(Phase.Running running) {
        return CompletableFuture.runAsync(() -> {
            log.info("[kick] Agent={} 驱动器启动，开始消费收件箱（pending={}）", id, inbox.hasPending());
            try {
                while (true) {
                    if (running.abort().get()) {
                        log.info("[kick] Agent={} 检测到取消信号，停止 turn 循环", id);
                        break;
                    }
                    if (!inbox.hasPending()) {
                        log.info("[kick] Agent={} 收件箱已空，turn 循环自然结束", id);
                        break;
                    }
                    boolean shouldContinue = turn(running);
                    if (!shouldContinue) break;
                }
            } finally {
                synchronized (ReactLoopAgent.this) {
                    boolean wasAborted = running.abort().get();
                    turnCount = running.turn();
                    phase = new Phase.Idle(turnCount);
                    log.info("[kick] Agent={} 驱动器停机，共完成 {} 轮 turn，aborted={}",
                            id, turnCount, wasAborted);
                    if (!wasAborted && running.wakeRequested().get() && inbox.hasPending()) {
                        Phase.Running next = new Phase.Running(turnCount);
                        phase = next;
                        log.info("[kick] Agent={} 检测到 wake 请求且收件箱仍有待处理消息，递归重入驱动器", id);
                        // 递归重入驱动器，继续处理后续 turn
                        CompletableFuture<Void> activity = kick(next);
                        activityDone.set(activity);
                    }
                }
            }
        }, DRIVER);
    }

    // ── Turn 处理 ─────────────────────────────────────────────────────

    /**
     * 执行一个回合。
     * <p>
     * 执行流程：
     * 1. 打开回合（turn/start 事件）；
     * 2. 逐步执行 step（step/start、消息、step/end 事件逐段落账）；
     * 3. step 返回 null 表示续步继续（工具结果需要模型再看一眼，L06 起有生产者）；
     * 4. 回合结束以 turn/end(原因) 落账，返回是否继续下一回合。
     */
    private boolean turn(Phase.Running running) {
        // 打开 turn / start
        long turn = running.turn() + 1;
        running = new Phase.Running(running.abort(), turn, 0, running.wakeRequested());
        // 更新阶段引用
        synchronized (this) { phase = running; }
        session.append(SessionEventFactory.turnStart());
        log.info("[turn] ▶ Agent={} Turn#{} 开始（model={}）", id, turn, options.model());

        // 仅 turn 的第一个 step 需要从收件箱取用户消息；
        // 工具调用完成后的后续 step 应直接进入下一步（模型需要看到工具结果），
        // 不能因为收件箱为空就退出循环（续步生产者是 L06 的工具执行）。
        boolean midTurnContinuation = false;

        try {
            while (true) {
                if (running.step() >= MAX_STEPS_PER_TURN) {
                    log.warn("[turn] ⚠ Agent={} Turn#{} 达到单轮步数上限 {}，强制结束", id, turn, MAX_STEPS_PER_TURN);
                    session.append(SessionEventFactory.turnEnd(
                            new TurnEndReason.Error("max steps per turn exceeded", "MAX_STEPS")));
                    return false;
                }
                if (running.abort().get()) {
                    log.info("[turn] ⚠ Agent={} Turn#{} 检测到取消信号，中止 turn", id, turn);
                    session.append(SessionEventFactory.turnEnd(
                            new TurnEndReason.Aborted("aborted")));
                    return false;
                }

                // 步骤前：抢占收件箱消息（仅 turn 的第一步需要）
                List<Message> claimed = List.of();
                if (!midTurnContinuation) {
                    claimed = inbox.claim(InboxTarget.NEXT_TURN, (int) turn);
                    if (claimed.isEmpty() && !inbox.hasPending()) {
                        // 没有消息，当前 turn 完成
                        break;
                    }
                }

                long step = running.step() + 1;
                running = new Phase.Running(running.abort(), turn, step, running.wakeRequested());
                synchronized (this) { phase = running; }

                // 步骤开始
                session.append(SessionEventFactory.stepStart(turn, step));

                // 追加从收件箱抢占到的用户消息
                for (Message msg : claimed) {
                    session.append(SessionEventFactory.userMessage(turn, step, msg));
                }

                // 执行当前 step
                TurnEndReason stepResult = step(running, turn, step);

                // 步骤结束
                session.append(SessionEventFactory.stepEnd(turn, step));

                // 空值表示「继续执行下一步」（例如工具调用已完成，模型应继续推理）
                if (stepResult == null) {
                    midTurnContinuation = true;
                    log.debug("[turn] Agent={} Turn#{} Step#{} 工具调用完成，续步继续推理（midTurnContinuation）",
                            id, turn, step);
                    continue;
                }

                // 检查当前 turn 是否应该结束
                if (stepResult instanceof TurnEndReason.Completed) {
                    log.info("[turn] ◀ Agent={} Turn#{} Step#{} 完成（模型无需更多工具调用）", id, turn, step);
                    session.append(SessionEventFactory.turnEnd(new TurnEndReason.Completed()));
                    return true;
                } else if (stepResult instanceof TurnEndReason.MaxTokens) {
                    // L04：token 上限截断直接结束回合；受控续写（≤4 次）是 L08 的增量
                    log.info("[turn] ◀ Agent={} Turn#{} Step#{} 输出被 token 上限截断，结束 turn", id, turn, step);
                    session.append(SessionEventFactory.turnEnd(new TurnEndReason.MaxTokens()));
                    return true;
                } else if (stepResult instanceof TurnEndReason.Blocked) {
                    log.warn("[turn] ⚠ Agent={} Turn#{} Step#{} 被阻塞（Blocked）", id, turn, step);
                    session.append(SessionEventFactory.turnEnd(new TurnEndReason.Blocked()));
                    return false;
                } else if (stepResult instanceof TurnEndReason.Aborted) {
                    log.info("[turn] ⚠ Agent={} Turn#{} Step#{} 被中止（Aborted）", id, turn, step);
                    session.append(SessionEventFactory.turnEnd(stepResult));
                    return false;
                } else if (stepResult instanceof TurnEndReason.Error) {
                    log.error("[turn] ✗ Agent={} Turn#{} Step#{} 执行出错：{}",
                            id, turn, step, ((TurnEndReason.Error) stepResult).message());
                    session.append(SessionEventFactory.turnEnd(stepResult));
                    return false;
                }
            }

            // 当前 turn 在没有工具调用的情况下完成
            log.info("[turn] ◀ Agent={} Turn#{} 自然完成（收件箱无更多待处理消息）", id, turn);
            session.append(SessionEventFactory.turnEnd(new TurnEndReason.Completed()));
            return true;

        } catch (Exception e) {
            log.error("[turn] ✗ Agent={} Turn#{} 发生未捕获异常：{}", id, turn, e.getMessage(), e);
            session.append(SessionEventFactory.turnEnd(
                    new TurnEndReason.Error(e.getMessage() != null ? e.getMessage() : e.toString(), "UNKNOWN")));
            return false;
        }
    }

    // ── Step 处理 ─────────────────────────────────────────────────────

    /**
     * 执行一个 step。
     * <p>
     * 执行流程：
     * 1. 首步记录请求头/上下文事件；
     * 2. 流式调用 LLM（分片先攒批，流结束统一落账），等待期间响应取消；
     * 3. 组装助手消息（assistant/message 事件）并返回 step 结束原因。
     * <p>
     * 工具调用分支（L06）：模型请求工具 → 执行 → 返回 null 续步。本课无工具，
     * finish 非 Stop 即 Completed。
     */
    private TurnEndReason step(Phase.Running running, long turn, long step) {
        log.info("[step] ▷ Agent={} Turn#{} Step#{} 开始执行（model={}）", id, turn, step, options.model());
        try {
            // 1. 构建请求参数
            GenerateOptions generateOptions = buildRequest();

            // 2. 记录请求头与上下文（仅首步或日志缺失时；系统提示词比对随 L08 加入）
            if (step == 1 || session.requestHeader() == null) {
                session.append(SessionEventFactory.requestHeader(
                        Map.of("provider", options.provider(), "model", options.model()),
                        Map.of(),
                        null,   // system：系统提示词组装器随 L08 接入
                        List.of()
                ));
                session.append(SessionEventFactory.requestContext(
                        options.provider(), options.model(), null));
            }

            // 3. 流式调用 LLM —— 订阅端口发布器，阻塞消费为完整结果
            BlockAssembler assembler = new BlockAssembler();
            List<SessionEvent> chunkEvents = new ArrayList<>();

            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> streamError = new AtomicReference<>();
            AtomicBoolean aborted = new AtomicBoolean(false);

            llm.stream(generateOptions).subscribe(new Flow.Subscriber<>() {
                Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(StreamChunk chunk) {
                    if (running.abort().get()) {
                        aborted.set(true);
                        subscription.cancel();
                        return;
                    }
                    assembler.push(chunk);
                    if (chunk instanceof StreamChunk.ReasoningDelta rd) {
                        chunkEvents.add(SessionEventFactory.assistantChunk(turn, step, rd.text(), "reasoning-delta"));
                    }
                    if (chunk instanceof StreamChunk.TextDelta td) {
                        chunkEvents.add(SessionEventFactory.assistantChunk(turn, step, td.text(), "text-delta"));
                    }
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

            // 等待流式结束，并周期检查取消信号（安全点）
            while (!done.await(100, TimeUnit.MILLISECONDS)) {
                if (running.abort().get()) {
                    aborted.set(true);
                    break;
                }
            }

            // 分片事件批量落账（攒批避免逐条同步开销，L09 持久化镜像同样受益）
            if (!chunkEvents.isEmpty()) {
                session.appendBatch(chunkEvents);
            }

            if (aborted.get()) {
                log.info("[step] ⚠ Agent={} Turn#{} Step#{} 流式输出期间被取消", id, turn, step);
                return new TurnEndReason.Aborted("aborted during stream");
            }

            if (streamError.get() != null) {
                Throwable err = streamError.get();
                log.error("[step] ✗ Agent={} Turn#{} Step#{} LLM 流式调用失败：{}",
                        id, turn, step, err.getMessage());
                return new TurnEndReason.Error(
                        err.getMessage() != null ? err.getMessage() : err.toString(),
                        "STREAM_ERROR");
            }

            // 4. 组装 assistant 消息
            FinishReason finish = assembler.finish();
            MessageSource.ModelMessageSource source = new MessageSource.ModelMessageSource(
                    options.provider(), options.model());
            Message assistantMsg = assembler.message(source);

            // 5. 检查结束原因 —— 先处理错误场景：软化为可见的错误助手消息，
            //    避免前端拿到空白结果（API Key 无效、网络异常时流里往往没有文本）
            if (finish instanceof FinishReason.Error err) {
                log.error("[step] ✗ Agent={} Turn#{} Step#{} LLM 返回错误：code={} message={}",
                        id, turn, step, err.failure().code(), err.failure().message());
                String errText = extractText(assistantMsg);
                Message toRecord = assistantMsg;
                if (errText == null || errText.isBlank()) {
                    toRecord = Message.createAssistant(List.of(new TextBlock(
                            "⚠️ LLM 调用失败: " + err.failure().message())), source);
                }
                session.append(SessionEventFactory.assistantMessage(turn, step, toRecord, assembler.usage()));
                return new TurnEndReason.Error(err.failure().message(), err.failure().code());
            }

            // 非错误场景下，记录 assistant/message（surface event）
            session.append(SessionEventFactory.assistantMessage(turn, step, assistantMsg, assembler.usage()));

            if (finish instanceof FinishReason.MaxTokens) {
                log.info("[step] ↻ Agent={} Turn#{} Step#{} 输出达到 token 上限", id, turn, step);
                return new TurnEndReason.MaxTokens();
            }
            if (finish instanceof FinishReason.Aborted) {
                log.info("[step] ⚠ Agent={} Turn#{} Step#{} 流被中止", id, turn, step);
                return new TurnEndReason.Aborted("stream aborted");
            }

            // 6. 无工具调用（L06 前工具表恒空）：模型给出最终回复，回合完成
            log.info("[step] ◁ Agent={} Turn#{} Step#{} 完成，无工具调用", id, turn, step);
            return new TurnEndReason.Completed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("[step] ⚠ Agent={} Turn#{} Step#{} 被中断", id, turn, step);
            return new TurnEndReason.Aborted("interrupted");
        } catch (Exception e) {
            log.error("[step] ✗ Agent={} Turn#{} Step#{} 执行异常：{}", id, turn, step, e.getMessage(), e);
            return new TurnEndReason.Error(
                    e.getMessage() != null ? e.getMessage() : e.toString(),
                    "STEP_ERROR");
        }
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
     * <p>历史即表面投影（deriveMessages）。上下文预算裁剪与受控续写注入是 L08 的增量，
     * 系统提示词与工具 schema 分别随 L08 / L06 接入。
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

    // ── 空实现监听器 ────────────────────────────────────────────

    /** 事件监听空实现（组合根未提供监听器时使用；消费方随 L06/L14 加入）。 */
    private static final class NoopAgentEventListener implements AgentEventListener {
        @Override
        public void emit(String name, Object payload) {
        }

        @Override
        public CompletableFuture<Void> serial(String name, Object payload) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public <T> CompletableFuture<T> waterfall(String name, Object payload, CompletableFuture<T> defaultAction) {
            return defaultAction;
        }
    }
}
