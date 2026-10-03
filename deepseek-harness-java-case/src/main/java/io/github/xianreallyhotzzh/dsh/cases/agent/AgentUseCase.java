package io.github.xianreallyhotzzh.dsh.cases.agent;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageFactory;
import io.github.xianreallyhotzzh.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.AgentCancelCause;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.run.AgentRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent 交互用例（薄门面）。
 * <p>
 * 消息发送委托给策略树：Resolve（解析/创建 Agent）→ Intent（意图分类）→
 * Dispatch（构造消息投递收件箱）→ Collect（等待空闲并收集回合结果）。
 */
@Service
public class AgentUseCase implements IAgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentUseCase.class);

    private final AgentMessageFactory agentMessageFactory;
    private final AgentRunLifecycle agentFactory;

    public AgentUseCase(AgentMessageFactory agentMessageFactory, AgentRunLifecycle agentFactory) {
        this.agentMessageFactory = agentMessageFactory;
        this.agentFactory = agentFactory;
    }

    @Override
    public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request) {
        log.info("[UseCase] Agent={} 收到消息发送请求（channel={} streaming=false）",
                request.agentId(), request.channelCode());
        return agentMessageFactory.pipeline().execute(request);
    }

    @Override
    public String getAgentStatus(String agentId) {
        AgentRun agent = agentFactory.get(agentId);
        if (agent == null) return "not-found";
        return agent.status().name().toLowerCase();
    }

    @Override
    public void cancelAgent(String agentId) {
        AgentRun agent = agentFactory.get(agentId);
        if (agent != null) {
            agent.cancel(new AgentCancelCause.UserCancel("user requested"), false);
        }
    }
}
