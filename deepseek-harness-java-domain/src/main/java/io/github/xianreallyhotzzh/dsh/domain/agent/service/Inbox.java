package io.github.xianreallyhotzzh.dsh.domain.agent.service;

import io.github.xianreallyhotzzh.dsh.domain.agent.contract.outbound.InboxNotificationPublisher;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.InboxSplice;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.InboxTarget;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionEvent;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.InboxSplicedPayload;
import io.github.xianreallyhotzzh.dsh.domain.session.event.service.SessionEventFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Inbox 表示智能体收件箱的领域模型（L04 起为事件版——结构变更先落会话事件再改内存态）。
 * <p>
 * 两段队列：NEXT_TURN（下一回合消费）与 NEXT_STEP（工具续步间消费，L06 起有生产者）。
 * 构造时从会话日志重放 AgentInboxSpliced 事件恢复队列结构——事件是唯一事实源，
 * 内存队列只是投影。
 */
public final class Inbox {

    private final SessionLog session;
    private final InboxNotificationPublisher notifications;
    private final List<Message> nextTurn = new ArrayList<>();
    private final List<Message> nextStep = new ArrayList<>();

    public Inbox(SessionLog session, InboxNotificationPublisher notifications) {
        this.session = session;
        this.notifications = notifications;
        replay();
    }

    /** 从会话日志重放收件箱拼接事件，恢复两段队列。 */
    private void replay() {
        int seedLength = session.header() != null
                ? session.header().seedLength() : 0;
        List<SessionEvent> events = session.events();
        for (int i = seedLength; i < events.size(); i++) {
            SessionEvent event = events.get(i);
            if (!(event instanceof SessionEvent.AgentInboxSpliced spliced)) continue;
            try {
                apply(spliced.data());
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "invalid persisted inbox splice at session seq " + event.seq(), e);
            }
        }
    }

    /** 下一回合段快照。 */
    public List<Message> nextTurn() {
        return List.copyOf(nextTurn);
    }

    /** 下一步段快照。 */
    public List<Message> nextStep() {
        return List.copyOf(nextStep);
    }

    /** 是否还有待处理消息。 */
    public boolean hasPending() {
        return !nextTurn.isEmpty() || !nextStep.isEmpty();
    }

    /** 清空全部待处理消息（以拼接事件落账）。 */
    public void clear() {
        splice(InboxTarget.NEXT_STEP, 0, nextStep.size(), List.of());
        splice(InboxTarget.NEXT_TURN, 0, nextTurn.size(), List.of());
    }

    /**
     * 抢占待处理消息：先取全部下一步消息，再从下一回合段取最早一条。
     * <p>数据示例：NEXT_TURN 段有 [m1, m2] 时 claim 返回 [m1]，m2 留给后续回合。
     */
    public List<Message> claim(InboxTarget target, int turn) {
        List<Message> claimed = new ArrayList<>(mutate(InboxTarget.NEXT_STEP, 0, nextStep.size(), List.of(), false));
        if (target == InboxTarget.NEXT_TURN) {
            claimed.addAll(mutate(InboxTarget.NEXT_TURN, 0, 1, List.of(), false));
        }
        for (Message message : claimed) {
            notifications.claimed(message, turn);
        }
        return claimed;
    }

    /** 投递一条消息到指定目标段。 */
    public void append(InboxTarget target, Message message) {
        splice(target, listFor(target).size(), 0, List.of(message));
    }

    /** 把一条消息插到指定目标段头部。 */
    public void prepend(InboxTarget target, Message message) {
        splice(target, 0, 0, List.of(message));
    }

    /** 按 messageId 替换一条待处理消息；未找到返回 false。 */
    public boolean replace(String messageId, Message newMessage) {
        Optional<Location> location = locate(messageId);
        if (location.isEmpty()) return false;
        Location loc = location.get();
        splice(loc.target(), loc.index(), 1, List.of(newMessage));
        return true;
    }

    /** 按 messageId 移除一条待处理消息；未找到返回 false。 */
    public boolean remove(String messageId) {
        Optional<Location> location = locate(messageId);
        if (location.isEmpty()) return false;
        Location loc = location.get();
        splice(loc.target(), loc.index(), 1, List.of());
        return true;
    }

    /** 拼接（start 起删 deleteCount 条、插入 inserted），落事件并返回被移除消息。 */
    public List<Message> splice(InboxTarget target, int start, int deleteCount, List<Message> inserted) {
        return mutate(target, start, deleteCount, inserted, true);
    }

    // ── Internal ──────────────────────────────────────────────────

    private List<Message> listFor(InboxTarget target) {
        return target == InboxTarget.NEXT_TURN ? nextTurn : nextStep;
    }

    private Optional<Location> locate(String messageId) {
        for (InboxTarget target : InboxTarget.values()) {
            List<Message> list = listFor(target);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(messageId)) {
                    return Optional.of(new Location(target, i));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 按 Inbox 目标修改消息队列：定位目标队列 → 应用插入/删除规则 → 返回新的消息列表。
     * <p>
     * discardRemoved 区分「外部拼接」与「内部抢占」：抢占（claim）不产生 canceled
     * 事件结果、不发出 discarded 通知。
     */
    private List<Message> mutate(
            InboxTarget target,
            int start,
            int deleteCount,
            List<Message> inserted,
            boolean discardRemoved
    ) {
        List<Message> inbox = listFor(target);

        // Normalize start (clamp, support negative as offset from end)
        int offset = start;
        int actualStart = offset < 0
                ? Math.max(inbox.size() + offset, 0)
                : Math.min(offset, inbox.size());

        // Normalize deleteCount
        int actualDeleteCount = Math.min(
                Math.max(deleteCount, 0),
                inbox.size() - actualStart
        );

        if (actualDeleteCount == 0 && inserted.isEmpty()) {
            return List.of();
        }

        String outcome = (discardRemoved && actualDeleteCount > 0) ? "canceled" : null;
        InboxSplice splice = new InboxSplice(target, actualStart, actualDeleteCount, inserted, outcome);
        validate(splice);

        // Append durable event BEFORE mutating live state
        SessionEvent.AgentInboxSpliced event = SessionEventFactory.inboxSpliced(
                target.wireName(), actualStart, actualDeleteCount, inserted, outcome);
        session.append(event);

        // Mutate live state
        List<Message> removed = new ArrayList<>();
        for (int i = 0; i < actualDeleteCount; i++) {
            removed.add(inbox.remove(actualStart));
        }
        for (int i = inserted.size() - 1; i >= 0; i--) {
            inbox.add(actualStart, inserted.get(i));
        }

        // Publish notifications
        if (discardRemoved) {
            for (Message message : removed) {
                notifications.discarded(message);
            }
        }
        for (Message message : inserted) {
            notifications.inserted(message);
        }

        return removed;
    }

    /** 重放路径：按事件载荷直接改队列（不再落事件——事件本身已在日志里）。 */
    private List<Message> apply(InboxSplicedPayload data) {
        InboxTarget target = InboxTarget.fromWireName(data.target());
        int removedCount = data.removedCount();
        List<Message> inserted = data.inserted();

        // Validate
        InboxSplice splice = new InboxSplice(target, data.start(), removedCount, inserted, data.outcome());
        validate(splice);

        List<Message> inbox = listFor(target);
        List<Message> removed = new ArrayList<>();
        for (int i = 0; i < removedCount; i++) {
            removed.add(inbox.remove(data.start()));
        }
        for (int i = inserted.size() - 1; i >= 0; i--) {
            inbox.add(data.start(), inserted.get(i));
        }
        return removed;
    }

    /** 校验拼接合法性：区间有效 + 拼接后两段队列无重复消息 ID。 */
    private void validate(InboxSplice splice) {
        List<Message> inbox = listFor(splice.target());
        int removedCount = splice.removedCount();

        if (splice.start() < 0 || splice.start() > inbox.size()
                || removedCount < 0
                || splice.start() + removedCount > inbox.size()) {
            throw new IllegalStateException("invalid inbox splice: target=" + splice.target()
                    + " start=" + splice.start() + " removedCount=" + removedCount
                    + " listSize=" + inbox.size());
        }

        // Build candidate post-splice list
        List<Message> candidate = new ArrayList<>(inbox);
        for (int i = 0; i < removedCount; i++) {
            candidate.remove(splice.start());
        }
        for (int i = splice.inserted().size() - 1; i >= 0; i--) {
            candidate.add(splice.start(), splice.inserted().get(i));
        }

        // Check for duplicate ids across both lists
        Set<String> ids = new HashSet<>();
        List<Message> other = splice.target() == InboxTarget.NEXT_TURN ? nextStep : nextTurn;
        for (Message message : candidate) {
            if (!ids.add(message.id())) {
                throw new IllegalStateException(
                        "message \"" + message.id() + "\" is already pending");
            }
        }
        for (Message message : other) {
            if (!ids.add(message.id())) {
                throw new IllegalStateException(
                        "message \"" + message.id() + "\" is already pending");
            }
        }
    }

    private record Location(InboxTarget target, int index) {}
}
