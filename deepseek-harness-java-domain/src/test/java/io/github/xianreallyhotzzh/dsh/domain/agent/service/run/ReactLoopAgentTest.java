package io.github.xianreallyhotzzh.dsh.domain.agent.service.run;

import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.AgentStatus;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.InboxTarget;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.Inbox;
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
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * 单回合单步子集的行为钉子：投递唤醒 → 阻塞消费流 → 会话落账 → 回到 Idle。
 * LLM 端口用脚本化 stub（不外呼）。
 */
class ReactLoopAgentTest {

    @Test
    void singleTurnDeliversAssistantMessageAndReturnsIdle() throws Exception {
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
        SessionLog session = new SessionLog("session-1");
        ReactLoopAgent agent = newAgent(port, session);

        agent.send(Message.createUser(List.of(new TextBlock("打个招呼")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);

        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(AgentStatus.IDLE, agent.status());

        List<Message> messages = session.messages();
        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).role());
        assertEquals("assistant", messages.get(1).role());
        assertEquals(new TextBlock("你好，世界"), messages.get(1).content().get(0));

        // 请求参数：历史含刚投递的用户消息，provider/model/sessionId 各就各位
        assertEquals("deepseek", captured.get().provider());
        assertEquals("deepseek-chat", captured.get().model());
        assertEquals("session-1", captured.get().sessionId());
        assertEquals(1, captured.get().messages().size());
        assertEquals("user", captured.get().messages().get(0).role());
    }

    @Test
    void llmFailureSoftensIntoVisibleErrorMessage() throws Exception {
        StubPort port = new StubPort(options -> List.of(
                new StreamChunk.Finish(new FinishReason.Error(new LlmFailure(
                        "upstream unavailable", LlmErrorCodes.SERVER)))
        ));
        SessionLog session = new SessionLog("session-2");
        ReactLoopAgent agent = newAgent(port, session);

        agent.send(Message.createUser(List.of(new TextBlock("继续")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);

        // 上游失败不让 Future 异常收尾：收敛为一条可见的错误助手消息
        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(AgentStatus.IDLE, agent.status());

        List<Message> messages = session.messages();
        assertEquals(2, messages.size());
        String content = ((TextBlock) messages.get(1).content().get(0)).text();
        assertTrue(content.contains("LLM 调用失败"));
        assertTrue(content.contains("upstream unavailable"));
    }

    @Test
    void wakeDriverIsIgnoredWhileRunningKeepsSingleActivity() throws Exception {
        StubPort port = new StubPort(options -> List.of(
                new StreamChunk.BlockStart(0, "text"),
                new StreamChunk.TextDelta(0, "ok"),
                new StreamChunk.BlockEnd(0, new TextBlock("ok")),
                new StreamChunk.Finish(new FinishReason.Stop())
        ));
        SessionLog session = new SessionLog("session-3");
        ReactLoopAgent agent = newAgent(port, session);

        agent.send(Message.createUser(List.of(new TextBlock("第一条")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);
        agent.send(Message.createUser(List.of(new TextBlock("第二条")), new MessageSource.UserSource()),
                InboxTarget.NEXT_TURN, true);

        agent.whenIdle().get(5, TimeUnit.SECONDS);
        assertEquals(AgentStatus.IDLE, agent.status());
        // 两条消息分别各占一个回合（kick 循环继续消费），会话共四条消息
        assertEquals(4, session.messages().size());
    }

    private ReactLoopAgent newAgent(ILlmRuntimePort port, SessionLog session) {
        AgentOptions options = new AgentOptions(
                "deepseek", "deepseek", "deepseek-chat", 1024, null, null, null, null);
        return new ReactLoopAgent("agent-1", options, "/tmp", session, new Inbox(), port);
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
}
