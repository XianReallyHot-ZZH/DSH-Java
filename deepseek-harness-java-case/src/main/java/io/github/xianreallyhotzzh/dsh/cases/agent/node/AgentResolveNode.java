package io.github.xianreallyhotzzh.dsh.cases.agent.node;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageDynamicContext;
import io.github.xianreallyhotzzh.dsh.cases.orchestration.StrategyHandler;
import io.github.xianreallyhotzzh.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.run.AgentRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 解析节点：获取或创建目标 Agent（策略树根节点）。
 * <p>
 * L03 最小实现：按 agentId 查缓存，未命中则以默认渠道配置创建。vendor 版还做
 * 沙箱根注册、流式互斥检查与事件日志恢复（L04/L13 接入）；AgentOptions 的解析
 * 优先级为「请求显式值 → 默认渠道配置（harness.yml）」，数据库默认档随 L09 加入。
 */
@Service("agentMessageResolveNode")
public class AgentResolveNode extends AbstractAgentMessageNode {

    private static final Logger log = LoggerFactory.getLogger(AgentResolveNode.class);

    private final AgentRunLifecycle agentFactory;
    private final AgentOptions defaultOptions;
    private final AgentDispatchNode next;

    public AgentResolveNode(
            AgentRunLifecycle agentFactory,
            AgentOptions defaultOptions,
            AgentDispatchNode next
    ) {
        this.agentFactory = agentFactory;
        this.defaultOptions = defaultOptions;
        this.next = next;
    }

    /**
     * 解析或创建本次消息要使用的 Agent；解析结果只写入上下文，本节点不产生最终响应。
     * <p>数据示例：agentId="a-1" 首次请求 → 用默认渠道（provider=deepseek）创建；
     * 再次请求同一 agentId → 复用缓存实例与同一会话。
     */
    @Override
    protected AgentMessageResponseDTO doApply(AgentMessageRequestDTO request,
                                              AgentMessageDynamicContext ctx) {
        AgentRun agent = agentFactory.get(request.agentId());
        if (agent == null) {
            AgentOptions options = buildAgentOptions(request);
            String workDir = request.cwd() != null && !request.cwd().isBlank()
                    ? request.cwd() : System.getProperty("user.dir");
            log.info("[Resolve] Agent={} 不存在，创建新实例（channel={} model={} cwd={}）",
                    request.agentId(), options.channelCode(), options.model(), workDir);
            agent = agentFactory.create(request.agentId(), options, workDir);
        } else {
            log.debug("[Resolve] Agent={} 已存在，复用实例", request.agentId());
        }
        ctx.setAgent(agent);
        return null;
    }

    @Override
    public StrategyHandler<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO>
    getNext(AgentMessageRequestDTO request, AgentMessageDynamicContext ctx) {
        return next;
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
