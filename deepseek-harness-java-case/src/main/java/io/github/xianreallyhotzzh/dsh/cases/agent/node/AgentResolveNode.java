package io.github.xianreallyhotzzh.dsh.cases.agent.node;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageDynamicContext;
import io.github.xianreallyhotzzh.dsh.cases.orchestration.StrategyHandler;
import io.github.xianreallyhotzzh.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.run.AgentRun;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 解析节点：获取或创建目标 Agent（策略树根节点）。
 * <p>
 * L04 面：按 agentId 查缓存，命中复用（热更新运行参数）；未命中则以默认渠道配置创建。
 * vendor 版还做沙箱根注册与流式互斥检查（L05/L13 接入）。AgentOptions 的解析
 * 优先级为「请求显式值 → 默认渠道配置（harness.yml）」，数据库默认档随 L09 加入。
 */
@Service("agentMessageResolveNode")
public class AgentResolveNode extends AbstractAgentMessageNode {

    private static final Logger log = LoggerFactory.getLogger(AgentResolveNode.class);

    private final AgentRunLifecycle agentFactory;
    private final AgentOptions defaultOptions;
    private final AgentIntentNode next;

    public AgentResolveNode(
            AgentRunLifecycle agentFactory,
            AgentOptions defaultOptions,
            AgentIntentNode next
    ) {
        this.agentFactory = agentFactory;
        this.defaultOptions = defaultOptions;
        this.next = next;
    }

    /**
     * 解析或创建本次消息要使用的 Agent；解析结果只写入上下文，本节点不产生最终响应。
     * <p>数据示例：agentId="a-1" 首次请求 → 用默认渠道（provider=deepseek）创建；
     * 再次请求同一 agentId → 复用缓存实例与同一会话，热更新本次请求的运行参数。
     */
    @Override
    protected AgentMessageResponseDTO doApply(AgentMessageRequestDTO request,
                                              AgentMessageDynamicContext ctx) {
        AgentRun agent = agentFactory.get(request.agentId());
        if (agent == null) {
            AgentOptions options = buildAgentOptions(request);
            String workDir = request.cwd() != null && !request.cwd().isBlank()
                    ? request.cwd() : System.getProperty("user.dir");
            // 实例缺失（服务重启）≠ 会话丢失：事件日志里有完整历史。
            // 直接 create 会开全新空会话，模型看到「继续」却没有任何上文；
            // 这里先尝试按 agentId 恢复最近一次持久化会话，恢复失败再走全新会话。
            SessionLog restoredSession = findPersistedSession(request.agentId());
            if (restoredSession != null) {
                log.info("[Resolve] Agent={} 内存实例缺失，从事件日志恢复会话 sessionId={}（事件数={}）",
                        request.agentId(), restoredSession.sessionId(), restoredSession.size());
                agent = agentFactory.resume(request.agentId(), options, workDir, restoredSession);
            } else {
                log.info("[Resolve] Agent={} 不存在，创建新实例（channel={} model={} cwd={}）",
                        request.agentId(), options.channelCode(), options.model(), workDir);
                agent = agentFactory.create(request.agentId(), options, workDir);
            }
        } else {
            log.debug("[Resolve] Agent={} 已存在，复用实例", request.agentId());
            agent.updateOptions(buildAgentOptions(request));
        }
        ctx.setAgent(agent);
        return null;
    }

    @Override
    public StrategyHandler<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO>
    getNext(AgentMessageRequestDTO request, AgentMessageDynamicContext ctx) {
        return next;
    }

    /**
     * 按 agentId 查找最近一次持久化会话并重建内存日志（实例缺失时的上下文恢复）。
     * <p>
     * L04 占位：事件持久化存储（ISessionEventStore）随 L09 落库课加入，届时此处
     * 按 header.agentPreset（存的就是 agentId）倒序找最近会话并 readAll 重建。
     * 现阶段事件只在内存，重启即无会话可恢复，恒返回 null（调用方走全新会话路径）。
     */
    private SessionLog findPersistedSession(String agentId) {
        return null;
    }

    /** 请求显式值覆盖默认渠道配置；provider/model 目前只有默认档（渠道体系 L11 接入）。 */
    private AgentOptions buildAgentOptions(AgentMessageRequestDTO request) {
        return new AgentOptions(
                request.channelCode() != null && !request.channelCode().isBlank()
                        ? request.channelCode() : defaultOptions.channelCode(),
                defaultOptions.provider(),
                defaultOptions.model(),
                request.maxTokens() != null ? request.maxTokens() : defaultOptions.maxTokens(),
                request.reasoningEffort() != null && !request.reasoningEffort().isBlank()
                        ? request.reasoningEffort().trim().toLowerCase() : defaultOptions.reasoningEffort(),
                defaultOptions.baseUrl(),
                defaultOptions.apiKey(),
                defaultOptions.protocol()
        );
    }
}
