package io.github.xianreallyhotzzh.dsh.domain.agent.service.run;

import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.AgentCancelCause;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.AgentStatus;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.InboxTarget;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.Inbox;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.LoggingInboxNotifications;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.LlmAdapter;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.FinishReason;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.GenerateOptions;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.LlmErrorCodes;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.LlmFailure;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.MessageSource;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionEvent;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionHeader;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.TurnEndReason;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * L04 驱动器的行为钉子：事件落账、Phase 三态、取消语义与 turn 循环（脚本化端口，不外呼）。
 */
class ReactLoopAgentTest {

    @Test
    void singleTurnWritesFullEventLedgerAndReturnsIdle() throws Exception {
        AtomicReference<GenerateOptions> captured = new AtomicReference<>();
        StubPort port = new StubPort(options -> {
            captured.set(options);
            return List.of(
                    new StreamChunk.BlockStart(0, "text"),
                    new StreamChunk.TextDelta(0, "你好"),
                    new StreamChunk.TextDelta(0, "，世界"),
                    new StreamChunk.BlockEnd(0, new TextBlock("你好，世界")),
                    new StreamChunk.Finish(new FinishReason.Stop())
            );
        });
        SessionLog session = newSessionLog("session-1");
        ReactLoopAgent agent = newAgent(port, session);

        agent.send(Message.createUser(List.of(new TextBlock("打个招呼")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);

        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(AgentStatus.IDLE, agent.status());

        // 事件账本：入箱拼接 → 回合开启 → 抢占拼接 → 步开启 → 用户消息 →
        // 请求头×2 → 分片×2 → 助手消息 → 步结束 → 回合结束(Completed)
        List<SessionEvent> events = session.events();
        List<String> wireNames = events.stream().map(e -> e.type().wireName()).toList();
        assertEquals(List.of(
                "agent/inbox/spliced",
                "turn/start",
                "agent/inbox/spliced",
                "step/start",
                "user/message",
                "request/header", "request/context",
                "assistant/chunk", "assistant/chunk", "assistant/message",
                "step/end", "turn/end"), wireNames);
        assertEquals(TurnEndReason.Completed.class,
                ((SessionEvent.TurnEnd) events.get(events.size() - 1)).reason().getClass());
        // seq 单调无空洞
        for (int i = 0; i < events.size(); i++) {
            assertEquals(i, events.get(i).seq());
        }

        // 表面投影恰好两条消息；请求历史含刚投递的用户消息
        List<Message> surface = session.deriveMessages();
        assertEquals(2, surface.size());
        assertEquals("user", surface.get(0).role());
        assertEquals("assistant", surface.get(1).role());
        assertEquals(new TextBlock("你好，世界"), surface.get(1).content().get(0));
        assertEquals("session-1", captured.get().sessionId());
        assertEquals(1, captured.get().messages().size());
        assertEquals("user", captured.get().messages().get(0).role());
    }

    @Test
    void llmFailureSoftensIntoVisibleErrorMessageAndTurnEndError() throws Exception {
        StubPort port = new StubPort(options -> List.of(
                new StreamChunk.Finish(new FinishReason.Error(new LlmFailure(
                        "upstream unavailable", LlmErrorCodes.SERVER)))
        ));
        SessionLog session = newSessionLog("session-2");
        ReactLoopAgent agent = newAgent(port, session);

        agent.send(Message.createUser(List.of(new TextBlock("继续")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);

        // 上游失败不让 Future 异常收尾：收敛为一条可见的错误助手消息，回合以 Error 落账
        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(AgentStatus.IDLE, agent.status());

        List<Message> surface = session.deriveMessages();
        assertEquals(2, surface.size());
        String content = ((TextBlock) surface.get(1).content().get(0)).text();
        assertTrue(content.contains("LLM 调用失败"));
        assertTrue(content.contains("upstream unavailable"));
        SessionEvent.TurnEnd turnEnd = (SessionEvent.TurnEnd) session.lastEvent();
        assertTrue(turnEnd.reason() instanceof TurnEndReason.Error);
    }

    @Test
    void twoMessagesConsumeTwoTurnsWithSeparateBoundaries() throws Exception {
        StubPort port = new StubPort(options -> List.of(
                new StreamChunk.BlockStart(0, "text"),
                new StreamChunk.TextDelta(0, "ok"),
                new StreamChunk.BlockEnd(0, new TextBlock("ok")),
                new StreamChunk.Finish(new FinishReason.Stop())
        ));
        SessionLog session = newSessionLog("session-3");
        ReactLoopAgent agent = newAgent(port, session);

        agent.send(Message.createUser(List.of(new TextBlock("第一条")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);
        agent.send(Message.createUser(List.of(new TextBlock("第二条")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);

        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(AgentStatus.IDLE, agent.status());

        // 两条消息各占一个回合：turn/start、turn/end 各两次；表面 4 条消息
        List<SessionEvent> events = session.events();
        assertEquals(2, events.stream().filter(e -> e instanceof SessionEvent.TurnStart).count());
        assertEquals(2, events.stream().filter(e -> e instanceof SessionEvent.TurnEnd).count());
        assertEquals(4, session.deriveMessages().size());
    }

    @Test
    void cancelDuringStreamAbortsTurnClearsInboxAndReturnsIdle() throws Exception {
        BlockingPort port = new BlockingPort();
        SessionLog session = newSessionLog("session-4");
        ReactLoopAgent agent = newAgent(port, session);

        agent.send(Message.createUser(List.of(new TextBlock("慢慢想")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);
        assertTrue(port.awaitStreamStarted(5, TimeUnit.SECONDS));
        assertEquals(AgentStatus.RUNNING, agent.status());
        // 取消前排一条消息：keepInbox=false 时应随取消一并清空
        agent.send(Message.createUser(List.of(new TextBlock("排队中的消息")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, false);

        agent.cancel(new AgentCancelCause.UserCancel("user requested"), false);

        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(AgentStatus.IDLE, agent.status());

        // 回合以 Aborted 落账收尾；排队消息已随取消清空，代理回到静止
        SessionEvent.TurnEnd turnEnd = (SessionEvent.TurnEnd) session.lastEvent();
        assertTrue(turnEnd.reason() instanceof TurnEndReason.Aborted);
        // 再次取消空闲 Agent 幂等无副作用
        agent.cancel(new AgentCancelCause.UserCancel("again"), false);
        assertEquals(AgentStatus.IDLE, agent.status());
    }

    @Test
    void sendWhileAbortingRequeuesMessageAndAbortDiscardsWakeCompensation() throws Exception {
        BlockingPort port = new BlockingPort();
        SessionLog session = newSessionLog("session-5");
        ReactLoopAgent agent = newAgent(port, session);

        agent.send(Message.createUser(List.of(new TextBlock("第一回合")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);
        assertTrue(port.awaitStreamStarted(5, TimeUnit.SECONDS));
        agent.cancel(new AgentCancelCause.UserCancel("user requested"), true);  // keepInbox=true

        // 中止态下再唤醒：消息改排 NEXT_TURN。被中止的 kick 不做唤醒补偿
        //（vendor 语义：abort 丢弃 wakeRequested），消息留在收件箱等下一次唤醒。
        agent.send(Message.createUser(List.of(new TextBlock("中止后新消息")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);
        port.releaseStream();

        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(AgentStatus.IDLE, agent.status());
        assertEquals(1, session.events().stream().filter(e -> e instanceof SessionEvent.TurnEnd).count());

        // 下一次唤醒消费积压消息：中止后新消息 + 第三条各占一个回合
        agent.send(Message.createUser(List.of(new TextBlock("第三条")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);
        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(3, session.events().stream().filter(e -> e instanceof SessionEvent.TurnEnd).count());
        // 表面：user(第一回合) + user(中止后新消息) + assistant + user(第三条) + assistant
        assertEquals(5, session.deriveMessages().size());

        // 维护相位：空闲可进入，任务完成后回 Idle（消费方随 L08 压缩接入）
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        agent.runMaintenance(abort -> CompletableFuture.completedFuture(ran.compareAndSet(false, true)))
                .get(5, TimeUnit.SECONDS);
        assertTrue(ran.get());
        assertEquals(AgentStatus.IDLE, agent.status());
    }

    @Test
    void flowLevelStreamErrorEndsTurnWithErrorReason() throws Exception {
        // 端口级 Flow.onError（传输层故障，未包装为 FinishReason.Error 的场景）：
        // step 应以 Error(STREAM_ERROR) 收尾（vendor 行为），而不是空消息 Completed
        ILlmRuntimePort port = new ILlmRuntimePort() {
            @Override
            public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
                return subscriber -> {
                    subscriber.onSubscribe(new Flow.Subscription() {
                        @Override public void request(long n) { }
                        @Override public void cancel() { }
                    });
                    subscriber.onError(new RuntimeException("connection reset"));
                };
            }

            @Override public void registerAdapter(String provider, LlmAdapter adapter) { }
            @Override public void unregisterAdapter(String provider) { }
            @Override public boolean hasAdapter(String provider) { return true; }
        };
        SessionLog session = newSessionLog("session-6");
        ReactLoopAgent agent = newAgent(port, session);

        agent.send(Message.createUser(List.of(new TextBlock("会断流的消息")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);

        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(AgentStatus.IDLE, agent.status());

        SessionEvent.TurnEnd turnEnd = (SessionEvent.TurnEnd) session.lastEvent();
        assertTrue(turnEnd.reason() instanceof TurnEndReason.Error);
        assertEquals("STREAM_ERROR", ((TurnEndReason.Error) turnEnd.reason()).code());
        // 流级失败不落助手消息：表面只有用户消息（区别于 FinishReason.Error 的软化路径）
        assertEquals(1, session.deriveMessages().size());
    }

    // ── helpers ──────────────────────────────────────────────────

    private SessionLog newSessionLog(String sessionId) {
        return new SessionLog(sessionId,
                SessionHeader.create(sessionId, Instant.now(), "/tmp", "test-agent"));
    }

    private ReactLoopAgent newAgent(ILlmRuntimePort port, SessionLog session) {
        AgentOptions options = new AgentOptions(
                "deepseek", "deepseek", "deepseek-chat", 1024, null, null, null, null);
        Inbox inbox = new Inbox(session, LoggingInboxNotifications.INSTANCE);
        return new ReactLoopAgent("agent-1", options, "/tmp", session, inbox, port);
    }

    /** 脚本化端口：按请求返回固定分片脚本，同步发布。 */
    private static final class StubPort implements ILlmRuntimePort {
        private final Function<GenerateOptions, List<StreamChunk>> script;

        private StubPort(Function<GenerateOptions, List<StreamChunk>> script) {
            this.script = script;
        }

        @Override
        public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
            List<StreamChunk> chunks = new CopyOnWriteArrayList<>(script.apply(options));
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { }
                    @Override public void cancel() { }
                });
                chunks.forEach(subscriber::onNext);
                subscriber.onComplete();
            };
        }

        @Override public void registerAdapter(String provider, LlmAdapter adapter) { }
        @Override public void unregisterAdapter(String provider) { }
        @Override public boolean hasAdapter(String provider) { return true; }
    }

    /**
     * 可挂起的端口：订阅后从独立线程发布两个分片并挂住，等 release 信号再收尾——
     * 让驱动线程有机会走进「周期检查取消」的安全点（取消测试的关键）。
     */
    private static final class BlockingPort implements ILlmRuntimePort {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        boolean awaitStreamStarted(long timeout, TimeUnit unit) throws InterruptedException {
            return started.await(timeout, unit);
        }

        void releaseStream() {
            release.countDown();
        }

        @Override
        public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { }
                    @Override public void cancel() { }
                });
                started.countDown();
                Thread worker = new Thread(() -> {
                    try {
                        subscriber.onNext(new StreamChunk.BlockStart(0, "text"));
                        subscriber.onNext(new StreamChunk.TextDelta(0, "部分"));
                        release.await(5, TimeUnit.SECONDS);
                        subscriber.onNext(new StreamChunk.BlockEnd(0, new TextBlock("部分")));
                        subscriber.onNext(new StreamChunk.Finish(new FinishReason.Stop()));
                        subscriber.onComplete();
                    } catch (Throwable t) {
                        subscriber.onError(t);
                    }
                }, "blocking-port-worker");
                worker.setDaemon(true);
                worker.start();
            };
        }

        @Override public void registerAdapter(String provider, LlmAdapter adapter) { }
        @Override public void unregisterAdapter(String provider) { }
        @Override public boolean hasAdapter(String provider) { return true; }
    }
}
