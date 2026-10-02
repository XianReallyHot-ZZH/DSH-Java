package io.github.xianreallyhotzzh.dsh.cases.agent.factory;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.node.AgentResolveNode;
import io.github.xianreallyhotzzh.dsh.cases.orchestration.CasePipeline;
import io.github.xianreallyhotzzh.dsh.cases.orchestration.CasePipelineFactory;
import org.springframework.stereotype.Service;

/**
 * Agent 消息策略树工厂。
 * <p>
 * 根节点固定为 {@link AgentResolveNode}，后续节点由每个节点自己的 getNext 规则连接：
 * Resolve → Dispatch → Collect（Intent 节点随 L04 插入 Resolve 与 Dispatch 之间）。
 * streamingPipeline（携带流式接收器的上下文）随 L05 加入。
 */
@Service
public class AgentMessageFactory
        implements CasePipelineFactory<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO> {

    private final AgentResolveNode rootNode;

    public AgentMessageFactory(AgentResolveNode rootNode) {
        this.rootNode = rootNode;
    }

    /** 构建阻塞式消息用例的执行 Pipeline，绑定首节点和上下文。 */
    @Override
    public CasePipeline<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO> pipeline() {
        return CasePipeline.of("Agent message", rootNode, AgentMessageDynamicContext::new);
    }
}
