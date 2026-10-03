package io.github.xianreallyhotzzh.dsh.api;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;

/**
 * Agent 消息交互契约（由 trigger 层实现）。
 * <p>
 * L04 面：阻塞式 sendMessage + 状态查询 + 取消。流式（sendMessageStreaming）
 * 随 L05 加入。
 */
public interface IAgentApi {

    /** 发送一条消息并阻塞返回完整回合结果。 */
    Response<AgentMessageResponseDTO> sendMessage(AgentMessageRequestDTO request);

    /** 查询 Agent 运行状态（idle / running / not-found）。 */
    Response<String> getAgentStatus(String agentId);

    /** 取消 Agent 当前活动并清空待处理消息。 */
    Response<Void> cancelAgent(String agentId);
}
