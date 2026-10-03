package io.github.xianreallyhotzzh.dsh.trigger.http;

import io.github.xianreallyhotzzh.dsh.api.IAgentApi;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 对话入口。
 * <p>
 * L04 交付阻塞式 POST /message 与状态查询/取消；流式 /stream 随 L05 加入。
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

    @GetMapping("/{agentId}/status")
    public Response<String> getAgentStatus(@PathVariable("agentId") String agentId) {
        return agentApi.getAgentStatus(agentId);
    }

    @PostMapping("/{agentId}/cancel")
    public Response<Void> cancelAgent(@PathVariable("agentId") String agentId) {
        return agentApi.cancelAgent(agentId);
    }
}
