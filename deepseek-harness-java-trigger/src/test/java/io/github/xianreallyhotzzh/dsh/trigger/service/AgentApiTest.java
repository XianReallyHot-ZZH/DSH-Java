package io.github.xianreallyhotzzh.dsh.trigger.service;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import io.github.xianreallyhotzzh.dsh.cases.agent.IAgentUseCase;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * 信封收敛钉子：用例正常/非法参数/内部故障三条路径的响应编码，
 * 以及 L04 新增的状态查询与取消。
 * （vendor 同名测试只钉流式相位映射，其 sendMessage 路径无测试——本测试为复刻件自建。）
 */
class AgentApiTest {

    @Test
    void wrapsUseCaseResultAsSuccessEnvelope() {
        AgentApi agentApi = new AgentApi(useCaseReturning(new AgentMessageResponseDTO(
                "agent-1", "session-1", "idle", List.of(), List.of(), null)));

        Response<AgentMessageResponseDTO> response = agentApi.sendMessage(request());

        assertEquals(Response.SUCCESS_CODE, response.code());
        assertEquals("agent-1", response.data().agentId());
        assertNull(response.data().error());
    }

    @Test
    void foldsIllegalArgumentTo40000() {
        AgentApi agentApi = new AgentApi(useCaseThrowing(new IllegalArgumentException("agentId must be non-blank")));

        assertEquals(Response.INVALID_ARGUMENT_CODE, agentApi.sendMessage(request()).code());
    }

    @Test
    void foldsInternalFailureTo50000WithoutLeaking() {
        AgentApi agentApi = new AgentApi(useCaseThrowing(new IllegalStateException("wiring broken")));

        Response<AgentMessageResponseDTO> response = agentApi.sendMessage(request());
        assertEquals(Response.INTERNAL_ERROR_CODE, response.code());
        assertEquals("wiring broken", response.info());
        assertNull(response.data());
    }

    @Test
    void agentStatusPassesThroughUseCaseResult() {
        AgentApi agentApi = new AgentApi(new IAgentUseCase() {
            @Override public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request) { return null; }
            @Override public String getAgentStatus(String agentId) { return "idle"; }
            @Override public void cancelAgent(String agentId) { }
        });

        Response<String> response = agentApi.getAgentStatus("agent-1");

        assertEquals(Response.SUCCESS_CODE, response.code());
        assertEquals("idle", response.data());
    }

    @Test
    void cancelAgentRunsActionAndWrapsSuccessEnvelope() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AgentApi agentApi = new AgentApi(new IAgentUseCase() {
            @Override public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request) { return null; }
            @Override public String getAgentStatus(String agentId) { return "not-found"; }
            @Override public void cancelAgent(String agentId) { cancelled.set(true); }
        });

        Response<Void> response = agentApi.cancelAgent("agent-1");

        assertTrue(cancelled.get());
        assertEquals(Response.SUCCESS_CODE, response.code());
        assertNull(response.data());
    }

    // ── helpers ──────────────────────────────────────────────────

    private IAgentUseCase useCaseReturning(AgentMessageResponseDTO response) {
        return new IAgentUseCase() {
            @Override public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request) { return response; }
            @Override public String getAgentStatus(String agentId) { return "idle"; }
            @Override public void cancelAgent(String agentId) { }
        };
    }

    private IAgentUseCase useCaseThrowing(RuntimeException exception) {
        return new IAgentUseCase() {
            @Override public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request) { throw exception; }
            @Override public String getAgentStatus(String agentId) { throw exception; }
            @Override public void cancelAgent(String agentId) { throw exception; }
        };
    }

    private AgentMessageRequestDTO request() {
        return new AgentMessageRequestDTO("agent-1", null, null, null, "你好", null, null, null, null);
    }
}
