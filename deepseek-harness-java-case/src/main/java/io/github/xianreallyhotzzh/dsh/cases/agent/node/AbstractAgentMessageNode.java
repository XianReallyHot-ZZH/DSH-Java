package io.github.xianreallyhotzzh.dsh.cases.agent.node;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageDynamicContext;
import io.github.xianreallyhotzzh.dsh.cases.orchestration.AbstractStrategyRouter;

/** Agent 消息策略树节点的抽象基类。 */
public abstract class AbstractAgentMessageNode
        extends AbstractStrategyRouter<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO> {
}
