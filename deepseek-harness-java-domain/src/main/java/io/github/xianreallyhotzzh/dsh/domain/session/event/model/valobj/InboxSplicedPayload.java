package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

import java.util.List;

/**
 * 收件箱拼接事件载荷：Inbox 的每次结构变更都以该事件先行落账（事件是唯一事实源）。
 */
public record InboxSplicedPayload(
        String target,
        int start,
        int removedCount,
        List<Message> inserted,
        String outcome
) {
    public InboxSplicedPayload {
        inserted = inserted == null ? List.of() : List.copyOf(inserted);
    }
}
