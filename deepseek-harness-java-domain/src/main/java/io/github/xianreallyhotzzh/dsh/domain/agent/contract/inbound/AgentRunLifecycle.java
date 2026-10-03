package io.github.xianreallyhotzzh.dsh.domain.agent.contract.inbound;

import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.run.AgentRun;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;

/**
 * Agent 运行的入站生命周期契约（case 层以此驱动 domain，不依赖具体工厂类）。
 * <p>
 * L04 面：create / get / resume。resume 的会话来源（事件日志恢复）随 L09 接入；
 * configureApprovalMode、updateCwd、disposeAll 随 L13 / L10 加入。
 */
public interface AgentRunLifecycle {

    /** 以全新会话创建一个 Agent 并缓存。 */
    AgentRun create(String agentId, AgentOptions options, String cwd);

    /** 以既有会话日志装配 Agent（实例缺失但会话可恢复时走这里）。 */
    AgentRun resume(String agentId, AgentOptions options, String cwd, SessionLog session);

    /** 取已缓存的 Agent；不存在返回 null。 */
    AgentRun get(String agentId);
}
