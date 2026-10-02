package io.github.xianreallyhotzzh.dsh.api;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.api.response.Response;

/**
 * Agent 消息交互契约（由 trigger 层实现）。
 * <p>
 * L03 最小面仅阻塞式 sendMessage；流式（sendMessageStreaming）、状态查询与取消
 * 随 L05 / L04 加入。
 */
public interface IAgentApi {

    /** 发送一条消息并阻塞返回完整回合结果。 */
    Response<AgentMessageResponseDTO> sendMessage(AgentMessageRequestDTO request);
}
