package io.github.xianreallyhotzzh.dsh.cases.agent.node;

import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageRequestDTO;
import io.github.xianreallyhotzzh.dsh.api.dto.AgentMessageResponseDTO;
import io.github.xianreallyhotzzh.dsh.cases.agent.factory.AgentMessageDynamicContext;
import io.github.xianreallyhotzzh.dsh.cases.orchestration.StrategyHandler;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.run.AgentRun;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ContentBlock;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ReasoningBlock;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 收集节点：等待 Agent 空闲后，从会话日志收集对话消息（策略树终止节点）。
 * <p>
 * L03 从消息日志折叠 role/content；vendor 版从事件日志折叠并按 callId 配对
 * 工具调用/结果（L04/L06 接入），产物清单（artifacts）随 L06 取 drainWrittenArtifacts。
 */
@Service("agentMessageCollectNode")
public class AgentCollectNode extends AbstractAgentMessageNode {

    private static final Logger log = LoggerFactory.getLogger(AgentCollectNode.class);

    /**
     * 等待 Agent 执行完成，并把会话消息折叠为响应。
     * <p>执行流程：whenIdle().join() 等待收尾（异常收口为 turnError）→ 遍历会话消息
     * → 组装响应 DTO。
     */
    @Override
    protected AgentMessageResponseDTO doApply(AgentMessageRequestDTO request,
                                              AgentMessageDynamicContext ctx) {
        AgentRun agent = ctx.getAgent();
        log.info("[Collect] Agent={} 等待 Agent 空闲，开始收集会话结果...", request.agentId());
        // 驱动器线程异常死亡（如 NoClassDefFoundError）会让 whenIdle() 异常完成。
        // 这里不能静默吞掉：要把根因带出响应（error 字段），前端才能显示「执行出错」。
        String turnError = null;
        try {
            agent.whenIdle().join();
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            turnError = "智能体执行异常中断：" + cause.getClass().getSimpleName()
                    + (cause.getMessage() != null ? " " + cause.getMessage() : "");
            log.warn("[Collect] Agent={} 等待空闲异常，回合异常收口：{}", request.agentId(), turnError, e);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : agent.session().messages()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("role", message.role());
            entry.put("content", extractText(message));
            messages.add(entry);
        }

        log.info("[Collect] Agent={} 收集完成，共 {} 条消息，status={}",
                request.agentId(), messages.size(), agent.status().name().toLowerCase());
        return new AgentMessageResponseDTO(
                request.agentId(),
                agent.session().sessionId(),
                agent.status().name().toLowerCase(),
                messages,
                List.of(),
                turnError
        );
    }

    @Override
    public StrategyHandler<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO>
    getNext(AgentMessageRequestDTO request, AgentMessageDynamicContext ctx) {
        return defaultStrategyHandler;
    }

    /**
     * 将消息中的推理块和正文块转为可读文本。
     * <p>两者同时存在时使用 HTML details 折叠推理内容，前端可直接展示 Markdown。
     */
    private String extractText(Message message) {
        StringBuilder reasoning = new StringBuilder();
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.content()) {
            if (block instanceof TextBlock tb) {
                text.append(tb.text());
            } else if (block instanceof ReasoningBlock rb) {
                if (!reasoning.isEmpty()) reasoning.append("\n");
                reasoning.append(rb.text());
            }
        }
        if (!reasoning.isEmpty() && !text.isEmpty()) {
            return "<think>" + reasoning + "</think>\n" + text;
        } else if (!reasoning.isEmpty()) {
            return "<think>" + reasoning + "</think>";
        }
        return text.toString();
    }
}
