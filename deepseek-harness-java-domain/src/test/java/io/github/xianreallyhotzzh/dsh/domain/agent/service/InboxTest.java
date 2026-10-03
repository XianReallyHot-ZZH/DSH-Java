package io.github.xianreallyhotzzh.dsh.domain.agent.service;

import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.InboxTarget;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.MessageSource;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionEvent;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionHeader;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.InboxSplicedPayload;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * 事件版收件箱的行为钉子：拼接事件落账、抢占语义、重复 ID 拒绝与重放恢复。
 */
class InboxTest {

    @Test
    void appendClaimsAndClearsAreRecordedAsSpliceEvents() {
        SessionLog session = newSessionLog("inbox-1");
        Inbox inbox = new Inbox(session, LoggingInboxNotifications.INSTANCE);

        inbox.append(InboxTarget.NEXT_TURN, msg("m1"));
        inbox.append(InboxTarget.NEXT_TURN, msg("m2"));

        List<SessionEvent> events = session.events();
        assertEquals(2, events.size());
        assertEquals("agent/inbox/spliced", events.get(0).type().wireName());
        InboxSplicedPayload first = ((SessionEvent.AgentInboxSpliced) events.get(0)).data();
        assertEquals("next-turn", first.target());
        assertEquals(1, first.inserted().size());
        assertEquals(0, first.removedCount());
        assertTrue(inbox.hasPending());

        // 抢占：全部 next-step + 最早一条 next-turn；被抢占消息以事件出账
        List<Message> claimed = inbox.claim(InboxTarget.NEXT_TURN, 1);
        assertEquals(1, claimed.size());
        assertEquals("m1", messageId(claimed.get(0)));
        InboxSplicedPayload claimPayload = lastSplice(session);
        assertEquals(1, claimPayload.removedCount());
        assertEquals(0, claimPayload.start());
        // claim 不标记 canceled（抢占不是丢弃）
        assertEquals(null, claimPayload.outcome());
        assertEquals(List.of("m2"), inbox.nextTurn().stream().map(this::messageId).toList());

        // 清空：全部待处理消息以 canceled 出账
        inbox.clear();
        assertFalse(inbox.hasPending());
        assertEquals("canceled", lastSplice(session).outcome());
    }

    @Test
    void duplicatePendingMessageIdIsRejected() {
        SessionLog session = newSessionLog("inbox-2");
        Inbox inbox = new Inbox(session, LoggingInboxNotifications.INSTANCE);
        Message shared = msg("dup");

        inbox.append(InboxTarget.NEXT_TURN, shared);
        assertThrows(IllegalStateException.class,
                () -> inbox.append(InboxTarget.NEXT_STEP, shared));
        // 校验失败时结构不变：仍只有一条待处理
        assertEquals(1, inbox.nextTurn().size());
    }

    @Test
    void spliceEventsReplayToRestoreInboxStructure() {
        SessionLog original = newSessionLog("inbox-3");
        Inbox inbox = new Inbox(original, LoggingInboxNotifications.INSTANCE);
        inbox.append(InboxTarget.NEXT_TURN, msg("a"));
        inbox.append(InboxTarget.NEXT_TURN, msg("b"));
        inbox.claim(InboxTarget.NEXT_TURN, 1);
        inbox.append(InboxTarget.NEXT_STEP, msg("c"));

        // 用同一事件日志重建收件箱：队列结构应与原收件箱一致（事件是唯一事实源）
        SessionLog replayed = newSessionLog("inbox-3");
        replayed.appendBatch(original.events());
        Inbox restored = new Inbox(replayed, LoggingInboxNotifications.INSTANCE);

        assertEquals(List.of("b"), restored.nextTurn().stream().map(this::messageId).toList());
        assertEquals(List.of("c"), restored.nextStep().stream().map(this::messageId).toList());
        assertTrue(restored.hasPending());
    }

    // ── helpers ──────────────────────────────────────────────────

    private SessionLog newSessionLog(String sessionId) {
        return new SessionLog(sessionId,
                SessionHeader.create(sessionId, Instant.now(), "/tmp", "test-agent"));
    }

    private Message msg(String text) {
        return Message.createUser(List.of(new TextBlock(text)), new MessageSource.UserSource());
    }

    private String messageId(Message message) {
        // 文本即身份：同一 TextBlock 构造的消息在断言里用文本对齐
        return ((TextBlock) message.content().get(0)).text();
    }

    private InboxSplicedPayload lastSplice(SessionLog session) {
        List<SessionEvent> events = session.events();
        return ((SessionEvent.AgentInboxSpliced) events.get(events.size() - 1)).data();
    }
}
