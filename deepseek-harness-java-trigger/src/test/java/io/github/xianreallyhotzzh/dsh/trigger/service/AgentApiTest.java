package io.github.xianreallyhotzzh.dsh.trigger.service;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import io.github.xianreallyhotzzh.dsh.cases.agent.IAgentUseCase;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

/**
 * 信封收敛钉子：用例正常/非法参数/内部故障三条路径的响应编码。
 * （vendor 同名测试只钉流式相位映射，其 sendMessage 路径无测试——本测试为复刻件自建。）
 */
class AgentApiTest {

    @Test
    void wrapsUseCaseResultAsSuccessEnvelope() {
        AgentApi agentApi = new AgentApi(request -> new AgentMessageResponseDTO(
                "agent-1", "session-1", "idle", List.of(), List.of(), null));

        Response<AgentMessageResponseDTO> response = agentApi.sendMessage(request());

        assertEquals(Response.SUCCESS_CODE, response.code());
        assertEquals("agent-1", response.data().agentId());
        assertNull(response.data().error());
    }

    @Test
    void foldsIllegalArgumentTo40000() {
        AgentApi agentApi = new AgentApi(request -> {
            throw new IllegalArgumentException("agentId must be non-blank");
        });

        assertEquals(Response.INVALID_ARGUMENT_CODE, agentApi.sendMessage(request()).code());
    }

    @Test
    void foldsInternalFailureTo50000WithoutLeaking() {
        AgentApi agentApi = new AgentApi(request -> {
            throw new IllegalStateException("wiring broken");
        });

        Response<AgentMessageResponseDTO> response = agentApi.sendMessage(request());
        assertEquals(Response.INTERNAL_ERROR_CODE, response.code());
        assertEquals("wiring broken", response.info());
        assertNull(response.data());
    }

    private AgentMessageRequestDTO request() {
        return new AgentMessageRequestDTO("agent-1", null, null, null, "你好", null, null, null, null);
    }
}
