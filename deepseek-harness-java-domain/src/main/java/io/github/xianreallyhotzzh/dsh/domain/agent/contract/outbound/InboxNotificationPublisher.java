package io.github.xianreallyhotzzh.dsh.domain.agent.contract.outbound;

import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

/**
 * InboxNotificationPublisher 表示收件箱变更通知的领域契约（出站端口）。
 * <p>
 * 收件箱的结构变更事实记入会话事件（AgentInboxSpliced）；本契约是对这些变更的
 * 实时旁路通知（消息被插入/丢弃/抢占），消费方随后续课接入。
 */
public interface InboxNotificationPublisher {

    /** 消息被插入收件箱。 */
    void inserted(Message message);

    /** 消息被丢弃（取消清理等）。 */
    void discarded(Message message);

    /** 消息被回合抢占消费。 */
    void claimed(Message message, int turn);
}
