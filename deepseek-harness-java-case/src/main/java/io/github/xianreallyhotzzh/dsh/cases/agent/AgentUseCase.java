package io.github.xianreallyhotzzh.dsh.cases.agent;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent 交互用例（薄门面）。
 * <p>
 * 消息发送委托给策略树：Resolve（解析/创建 Agent）→ Dispatch（构造消息投递收件箱）
 * → Collect（等待空闲并收集回合结果）。意图节点（Intent）随 L04 插入。
 */
@Service
public class AgentUseCase implements IAgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentUseCase.class);

    private final AgentMessageFactory agentMessageFactory;

    public AgentUseCase(AgentMessageFactory agentMessageFactory) {
        this.agentMessageFactory = agentMessageFactory;
    }

    @Override
    public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request) {
        log.info("[UseCase] Agent={} 收到消息发送请求（channel={} streaming=false）",
                request.agentId(), request.channelCode());
        return agentMessageFactory.pipeline().execute(request);
    }
}
