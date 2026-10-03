package io.github.xianreallyhotzzh.dsh.cases.agent;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;

/**
 * Agent 交互用例契约。
 * <p>
 * L04 面：阻塞式 sendMessage + 状态查询 + 取消。流式随 L05 加入。
 */
public interface IAgentUseCase {

    /** 发送一条消息，经策略树驱动 Agent 完成一个回合并收集结果。 */
    AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request);

    /** 查询 Agent 运行状态（idle / running）；实例不存在返回 not-found。 */
    String getAgentStatus(String agentId);

    /** 取消 Agent 当前活动并清空待处理消息；实例不存在时静默返回。 */
    void cancelAgent(String agentId);
}
