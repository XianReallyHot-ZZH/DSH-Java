package io.github.xianreallyhotzzh.dsh.domain.agent.service;

import io.github.xianreallyhotzzh.dsh.domain.agent.contract.outbound.InboxNotificationPublisher;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

/**
 * 收件箱通知的空实现（事实已落 AgentInboxSpliced 事件，实时旁路通知暂无消费方）。
 */
public final class LoggingInboxNotifications implements InboxNotificationPublisher {

    public static final LoggingInboxNotifications INSTANCE = new LoggingInboxNotifications();

    private LoggingInboxNotifications() {
    }

    @Override
    public void inserted(Message message) {
        // no-op
    }

    @Override
    public void discarded(Message message) {
        // no-op
    }

    @Override
    public void claimed(Message message, int turn) {
        // no-op
    }
}
