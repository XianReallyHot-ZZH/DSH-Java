package io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj;

/**
 * AgentCancelCause 表示智能体取消原因的领域值对象（封闭集合）。
 */
public sealed interface AgentCancelCause
        permits AgentCancelCause.UserCancel,
                AgentCancelCause.Disposed,
                AgentCancelCause.Maintenance {

    /** 用户主动取消（REST cancel 走这里）。 */
    record UserCancel(String reason) implements AgentCancelCause {}

    /** Agent 释放。 */
    record Disposed() implements AgentCancelCause {}

    /** 维护任务取消。 */
    record Maintenance() implements AgentCancelCause {}
}
