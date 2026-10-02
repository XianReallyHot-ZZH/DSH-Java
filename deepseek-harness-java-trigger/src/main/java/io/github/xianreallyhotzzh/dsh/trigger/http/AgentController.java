package io.github.xianreallyhotzzh.dsh.trigger.http;

import io.github.xianreallyhotzzh.dsh.api.IAgentApi;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 对话入口。
 * <p>
 * L03 只交付阻塞式 POST /message；流式 /stream 随 L05、状态查询与取消随 L04 加入。
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final IAgentApi agentApi;

    public AgentController(IAgentApi agentApi) {
        this.agentApi = agentApi;
    }

    @PostMapping("/message")
    public Response<AgentMessageResponseDTO> sendMessage(@RequestBody AgentMessageRequestDTO request) {
        return agentApi.sendMessage(request);
    }
}
