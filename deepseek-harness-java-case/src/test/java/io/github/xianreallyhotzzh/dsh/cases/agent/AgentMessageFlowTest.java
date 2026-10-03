package io.github.xianreallyhotzzh.dsh.cases.agent;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageFactory;
import io.github.xianreallyhotzzh.dsh.cases.agent.node.AgentCollectNode;
import io.github.xianreallyhotzzh.dsh.cases.agent.node.AgentDispatchNode;
import io.github.xianreallyhotzzh.dsh.cases.agent.node.AgentIntentNode;
import io.github.xianreallyhotzzh.dsh.cases.agent.node.AgentResolveNode;
import io.github.xianreallyhotzzh.dsh.cases.orchestration.CasePipeline;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.run.AgentRunFactory;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.LlmAdapter;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.FinishReason;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.GenerateOptions;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ReasoningBlock;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * L04 会话延续的单元孪生：同 agentId 第二轮引用第一轮上下文、新 agentId 独立、
 * 事件折叠响应。DoD 的真端点 curl 验收与之互为镜像（fake LLM，不外呼）。
 */
class AgentMessageFlowTest {

    @Test
    void secondTurnOnSameAgentSeesFirstTurnHistory() {
        // fake LLM：回显「历史条数 + 首条用户消息文本」，用于证明第二轮看得见第一轮
        AtomicReference<GenerateOptions> captured = new AtomicReference<>();
        ILlmRuntimePort port = echoingPort(captured);
        CasePipeline<AgentMessageRequestDTO, ?, AgentMessageResponseDTO> pipeline =
                newPipeline(port, defaultOptions());

        AgentMessageResponseDTO first = pipeline.execute(request("agent-flow", "我的暗号是菠萝"));
        assertEquals("idle", first.status());
        assertEquals(2, first.messages().size());

        AgentMessageResponseDTO second = pipeline.execute(request("agent-flow", "我的暗号是什么？"));
        // 同一 agentId：会话延续（sessionId 相同），响应含全部 4 条消息
        assertEquals(first.sessionId(), second.sessionId());
        assertEquals(4, second.messages().size());
        assertEquals("user", second.messages().get(2).get("role"));
        assertEquals("我的暗号是什么？", second.messages().get(2).get("content"));
        // 第二次 LLM 调用的历史包含第一轮的 user+assistant 与本轮 user（3 条），
        // 且首条正是第一轮的暗号——上下文延续的可观察证据
        assertEquals(3, captured.get().messages().size());
        assertEquals("我的暗号是菠萝",
                ((TextBlock) captured.get().messages().get(0).content().get(0)).text());
        assertTrue(((String) second.messages().get(3).get("content")).contains("菠萝"));
    }

    @Test
    void distinctAgentIdsHoldIndependentSessions() {
        AtomicReference<GenerateOptions> captured = new AtomicReference<>();
        ILlmRuntimePort port = echoingPort(captured);
        CasePipeline<AgentMessageRequestDTO, ?, AgentMessageResponseDTO> pipeline =
                newPipeline(port, defaultOptions());

        AgentMessageResponseDTO first = pipeline.execute(request("agent-a", "我的暗号是菠萝"));
        AgentMessageResponseDTO other = pipeline.execute(request("agent-b", "我的暗号是什么？"));

        // 新 agentId 独立：不同 sessionId、互不可见的历史（history=1，看不见暗号）
        assertNotEquals(first.sessionId(), other.sessionId());
        assertEquals(2, other.messages().size());
        assertEquals(1, captured.get().messages().size());
        assertEquals("我的暗号是什么？",
                ((TextBlock) captured.get().messages().get(0).content().get(0)).text());
        assertTrue(((String) other.messages().get(1).get("content")).contains("history=1"));
    }

    @Test
    void llmUpstreamFailureBecomesVisibleErrorContentNotException() {
        ILlmRuntimePort port = scriptedPort(List.of(
                new StreamChunk.Finish(new FinishReason.Error(
                        new io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.LlmFailure(
                                "upstream 5xx", io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.LlmErrorCodes.SERVER)))
        ));
        CasePipeline<AgentMessageRequestDTO, ?, AgentMessageResponseDTO> pipeline =
                newPipeline(port, defaultOptions());

        AgentMessageResponseDTO response = pipeline.execute(request("agent-err", "会失败的消息"));

        // 上游失败不炸链路：信封照常成功，错误以助手消息内容呈现
        assertEquals("idle", response.status());
        assertNull(response.error());
        String content = (String) response.messages().get(1).get("content");
        assertTrue(content.contains("LLM 调用失败"));
        assertTrue(content.contains("upstream 5xx"));
    }

    @Test
    void reasoningFoldsIntoThinkTagInCollectedResponse() {
        ILlmRuntimePort port = scriptedPort(List.of(
                new StreamChunk.BlockStart(0, "reasoning"),
                new StreamChunk.ReasoningDelta(0, "思考中"),
                new StreamChunk.BlockEnd(0, new ReasoningBlock("思考中")),
                new StreamChunk.BlockStart(1, "text"),
                new StreamChunk.TextDelta(1, "收到，这是回复"),
                new StreamChunk.BlockEnd(1, new TextBlock("收到，这是回复")),
                new StreamChunk.Finish(new FinishReason.Stop())
        ));
        CasePipeline<AgentMessageRequestDTO, ?, AgentMessageResponseDTO> pipeline =
                newPipeline(port, defaultOptions());

        AgentMessageResponseDTO response = pipeline.execute(request("agent-think", "收到消息了吗？"));

        assertEquals(2, response.messages().size());
        assertEquals("<think>思考中</think>\n收到，这是回复", response.messages().get(1).get("content"));
    }

    // ── helpers ──────────────────────────────────────────────────

    private CasePipeline<AgentMessageRequestDTO, ?, AgentMessageResponseDTO> newPipeline(
            ILlmRuntimePort port, AgentOptions defaultOptions) {
        AgentRunFactory runFactory = new AgentRunFactory(port);
        AgentCollectNode collect = new AgentCollectNode();
        AgentDispatchNode dispatch = new AgentDispatchNode(collect);
        AgentIntentNode intent = new AgentIntentNode(dispatch);
        AgentResolveNode resolve = new AgentResolveNode(runFactory, defaultOptions, intent);
        return new AgentMessageFactory(resolve).pipeline();
    }

    private AgentOptions defaultOptions() {
        return new AgentOptions("deepseek", "deepseek", "deepseek-chat", 4096, null, null, null, null);
    }

    private AgentMessageRequestDTO request(String agentId, String message) {
        return new AgentMessageRequestDTO(agentId, null, null, null, message, null, null, null, null);
    }

    /**
     * 回声端口：回复「history=N, first=<首条用户消息文本>」——N 证明上下文延续，
     * first 证明历史内容可引用；同时捕获最近一次请求参数。
     */
    private ILlmRuntimePort echoingPort(AtomicReference<GenerateOptions> captured) {
        return scriptedDelegate(options -> {
            captured.set(options);
            String first = options.messages().isEmpty() ? "none"
                    : ((TextBlock) options.messages().get(0).content().get(0)).text();
            String reply = "history=" + options.messages().size() + ", first=" + first;
            return List.of(
                    new StreamChunk.BlockStart(0, "text"),
                    new StreamChunk.TextDelta(0, reply),
                    new StreamChunk.BlockEnd(0, new TextBlock(reply)),
                    new StreamChunk.Finish(new FinishReason.Stop())
            );
        });
    }

    /** 脚本化端口：注册一个按脚本回放的适配器（不外呼）。 */
    private ILlmRuntimePort scriptedPort(List<StreamChunk> script) {
        return scriptedDelegate(options -> script);
    }

    private ILlmRuntimePort scriptedDelegate(java.util.function.Function<GenerateOptions, List<StreamChunk>> script) {
        return new ILlmRuntimePort() {
            @Override
            public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
                List<StreamChunk> chunks = script.apply(options);
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
        };
    }
}
