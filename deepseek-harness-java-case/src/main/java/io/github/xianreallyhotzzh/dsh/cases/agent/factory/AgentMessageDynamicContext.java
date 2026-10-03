package io.github.xianreallyhotzzh.dsh.cases.agent.factory;

import io.github.xianreallyhotzzh.dsh.domain.agent.service.run.AgentRun;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

/**
 * Agent 消息策略树中流转的可变上下文。
 * <p>
 * L04 面：解析出的 Agent、构造的用户消息与意图分类。流式接收器（deltaSink 等）
 * 随 L05 加入。
 */
public class AgentMessageDynamicContext implements AutoCloseable {

    /** 用户消息的轻量意图分类（规则引擎产出，供下游节点调整行为）。 */
    public enum MessageIntent {
        CHAT,
        CODE_QUESTION,
        TASK_EXECUTION,
        CLARIFICATION
    }

    private AgentRun agent;
    private Message userMessage;
    private MessageIntent intent;

    public AgentRun getAgent() {
        return agent;
    }

    public void setAgent(AgentRun agent) {
        this.agent = agent;
    }

    public Message getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(Message userMessage) {
        this.userMessage = userMessage;
    }

    public MessageIntent getIntent() {
        return intent;
    }

    public void setIntent(MessageIntent intent) {
        this.intent = intent;
    }

    /** 清理上下文引用（流式接收器解绑随 L05 接入）。 */
    @Override
    public void close() {
        agent = null;
        userMessage = null;
        intent = null;
    }
}
