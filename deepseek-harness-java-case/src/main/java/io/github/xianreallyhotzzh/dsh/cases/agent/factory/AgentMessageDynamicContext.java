package io.github.xianreallyhotzzh.dsh.cases.agent.factory;

import io.github.xianreallyhotzzh.dsh.domain.agent.service.run.AgentRun;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

/**
 * Agent 消息策略树中流转的可变上下文。
 * <p>
 * L03 最小子集只承载解析出的 Agent 与构造的用户消息；意图（MessageIntent）
 * 随 L04、流式接收器（deltaSink 等）随 L05 加入。
 */
public class AgentMessageDynamicContext implements AutoCloseable {

    private AgentRun agent;
    private Message userMessage;

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

    /** 清理上下文引用（流式接收器解绑随 L05 接入）。 */
    @Override
    public void close() {
        agent = null;
        userMessage = null;
    }
}
