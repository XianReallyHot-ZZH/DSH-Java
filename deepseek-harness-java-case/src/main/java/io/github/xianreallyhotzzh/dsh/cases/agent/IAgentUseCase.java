package io.github.xianreallyhotzzh.dsh.cases.agent;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;

/**
 * Agent 交互用例契约。
 * <p>
 * L03 最小面仅阻塞式 sendMessage；流式与状态/取消随 L05 / L04 加入。
 */
public interface IAgentUseCase {

    /** 发送一条消息，经策略树驱动 Agent 完成一个回合并收集结果。 */
    AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request);
}
