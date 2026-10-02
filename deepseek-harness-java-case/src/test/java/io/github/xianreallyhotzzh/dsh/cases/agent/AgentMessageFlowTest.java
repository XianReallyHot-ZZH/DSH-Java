package io.github.xianreallyhotzzh.dsh.cases.agent;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageFactory;
import io.github.xianreallyhotzzh.dsh.cases.agent.node.AgentCollectNode;
import io.github.xianreallyhotzzh.dsh.cases.agent.node.AgentDispatchNode;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * L03 tracer 的单元孪生：请求 DTO → 策略树三节点 → ReactLoopAgent → 脚本化 LLM 端口
 * → 响应 DTO 全链（不外呼）。DoD 的真端点 curl 验收与之互为镜像。
 */
class AgentMessageFlowTest {

    @Test
    void pipelineDrivesAgentTurnAndCollectsResponse() {
        AtomicReference<GenerateOptions> captured = new AtomicReference<>();
        ILlmRuntimePort port = scriptedPort(captured, List.of(
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

        AgentMessageResponseDTO response = pipeline.execute(request("agent-flow", "收到消息了吗？"));

        assertEquals("agent-flow", response.agentId());
        assertEquals("idle", response.status());
        assertNull(response.error());
        assertEquals(2, response.messages().size());
        assertEquals("user", response.messages().get(0).get("role"));
        assertEquals("收到消息了吗？", response.messages().get(0).get("content"));
        assertEquals("assistant", response.messages().get(1).get("role"));
        assertEquals("<think>思考中</think>\n收到，这是回复", response.messages().get(1).get("content"));

        // 第二次请求复用同一 Agent 实例：会话延续（sessionId 相同，响应含全部 4 条消息；
        // 发起第二次 LLM 调用时历史为 3 条——助手回复在 step 返回后才落账）
        AgentMessageResponseDTO second = pipeline.execute(request("agent-flow", "再说一次"));
        assertEquals(response.sessionId(), second.sessionId());
        assertEquals(4, second.messages().size());
        assertEquals(3, captured.get().messages().size());
    }

    @Test
    void llmUpstreamFailureBecomesVisibleErrorContentNotException() {
        AtomicReference<GenerateOptions> captured = new AtomicReference<>();
        ILlmRuntimePort port = scriptedPort(captured, List.of(
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

    private CasePipeline<AgentMessageRequestDTO, ?, AgentMessageResponseDTO> newPipeline(
            ILlmRuntimePort port, AgentOptions defaultOptions) {
        AgentRunFactory runFactory = new AgentRunFactory(port);
        AgentCollectNode collect = new AgentCollectNode();
        AgentDispatchNode dispatch = new AgentDispatchNode(collect);
        AgentResolveNode resolve = new AgentResolveNode(runFactory, defaultOptions, dispatch);
        return new AgentMessageFactory(resolve).pipeline();
    }

    private AgentOptions defaultOptions() {
        return new AgentOptions("deepseek", "deepseek", "deepseek-chat", 4096, null, null, null, null);
    }

    private AgentMessageRequestDTO request(String agentId, String message) {
        return new AgentMessageRequestDTO(agentId, null, null, null, message, null, null, null, null);
    }

    /** 脚本化端口：注册一个按脚本回放的适配器（不外呼）。 */
    private ILlmRuntimePort scriptedPort(AtomicReference<GenerateOptions> captured, List<StreamChunk> script) {
        return new ILlmRuntimePort() {
            @Override
            public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
                captured.set(options);
                return subscriber -> {
                    subscriber.onSubscribe(new Flow.Subscription() {
                        @Override public void request(long n) { }
                        @Override public void cancel() { }
                    });
                    script.forEach(subscriber::onNext);
                    subscriber.onComplete();
                };
            }

            @Override
            public void registerAdapter(String provider, LlmAdapter adapter) { }
            @Override
            public void unregisterAdapter(String provider) { }
            @Override
            public boolean hasAdapter(String provider) { return true; }
        };
    }
}
