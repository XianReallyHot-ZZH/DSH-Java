package io.github.xianreallyhotzzh.dsh.domain.agent.service.run;

import io.github.xianreallyhotzzh.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.Inbox;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.LoggingInboxNotifications;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionHeader;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Agent 运行工厂（L04 事件版装配）。
 * <p>
 * 创建带 SessionHeader 的会话日志、事件版收件箱并装配 {@link ReactLoopAgent}，
 * 按 agentId 缓存存活实例。header 的 agentPreset 槽位存 agentId——L09 的
 * findPersistedSession 靠它从事件日志找回「这个 agentId 最近一次会话」。
 * vendor 版还装配 per-Agent 工具表、工具执行器、审批门禁、系统提示词组装器、
 * 压缩引擎与事件监听器工厂——分别随 L06 / L13 / L08 / L14 加入。
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
        SessionHeader header = SessionHeader.create(sessionId, Instant.now(), cwd, agentId);
        SessionLog session = new SessionLog(sessionId, header);
        return wireAgent(agentId, options, cwd, session);
    }

    @Override
    public AgentRun resume(String agentId, AgentOptions options, String cwd, SessionLog session) {
        return wireAgent(agentId, options, cwd, session);
    }

    @Override
    public AgentRun get(String agentId) {
        return liveAgents.get(agentId);
    }

    private AgentRun wireAgent(String agentId, AgentOptions options, String cwd, SessionLog session) {
        Inbox inbox = new Inbox(session, LoggingInboxNotifications.INSTANCE);
        ReactLoopAgent agent = new ReactLoopAgent(agentId, options, cwd, session, inbox, llm);
        liveAgents.put(agentId, agent);
        return agent;
    }
}
