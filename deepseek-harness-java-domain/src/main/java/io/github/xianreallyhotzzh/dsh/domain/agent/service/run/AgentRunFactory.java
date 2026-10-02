package io.github.xianreallyhotzzh.dsh.domain.agent.service.run;

import io.github.xianreallyhotzzh.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.Inbox;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent 运行工厂（L03 最小装配）。
 * <p>
 * 创建会话日志、收件箱并装配 {@link ReactLoopAgent}，按 agentId 缓存存活实例。
 * vendor 版还装配 per-Agent 工具表、工具执行器、审批门禁、系统提示词组装器与
 * 压缩引擎——分别随 L06 / L13 / L08 加入。
 */
public class AgentRunFactory implements AgentRunLifecycle {

    private final ILlmRuntimePort llm;
    private final ConcurrentMap<String, AgentRun> liveAgents = new ConcurrentHashMap<>();

    public AgentRunFactory(ILlmRuntimePort llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    @Override
    public AgentRun create(String agentId, AgentOptions options, String cwd) {
        String sessionId = UUID.randomUUID().toString();
        SessionLog session = new SessionLog(sessionId);
        Inbox inbox = new Inbox();
        ReactLoopAgent agent = new ReactLoopAgent(agentId, options, cwd, session, inbox, llm);
        liveAgents.put(agentId, agent);
        return agent;
    }

    @Override
    public AgentRun get(String agentId) {
        return liveAgents.get(agentId);
    }
}
