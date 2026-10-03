package io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity;

import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.MessageSource;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.SurfaceIntent;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.SurfaceOp;
import io.github.xianreallyhotzzh.dsh.domain.session.event.service.SessionEventFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件日志的行为钉子：seq 分配、批量追加、表面投影（Append/Replace）、
 * 种子边界与恢复语义。
 */
class SessionLogTest {

    @Test
    void appendAssignsMonotonicGaplessSeq() {
        SessionLog log = newLog("s-1");

        SessionEvent turnStart = log.append(SessionEventFactory.turnStart());
        SessionEvent stepStart = log.append(SessionEventFactory.stepStart(1, 1));

        assertEquals(0, turnStart.seq());
        assertEquals(1, stepStart.seq());
        assertEquals(2, log.nextSeq());
        assertEquals(2, log.size());
        assertEquals(1, log.lastSeq());
        // 调用方自带的 seq 会被覆盖为权威分配值
        SessionEvent.UserMessage withStaleSeq = new SessionEvent.UserMessage(
                99, Instant.now(),
                SessionEventFactory.userMessage(1, 1, user("hi")).data(),
                new SurfaceIntent(SurfaceOp.append(), null));
        assertEquals(2, log.append(withStaleSeq).seq());
    }

    @Test
    void appendBatchAssignsSequentialSeqs() {
        SessionLog log = newLog("s-2");

        List<SessionEvent> appended = log.appendBatch(List.of(
                SessionEventFactory.turnStart(),
                SessionEventFactory.stepStart(1, 1),
                SessionEventFactory.userMessage(1, 1, user("问题"))
        ));

        assertEquals(List.of(0L, 1L, 2L), appended.stream().map(SessionEvent::seq).toList());
        assertEquals(3, log.nextSeq());
    }

    @Test
    void deriveMessagesProjectsOnlySurfaceAppendsInOrder() {
        SessionLog log = newLog("s-3");
        Message userMsg = user("第一轮问题");
        Message assistantMsg = assistant("第一轮回答");

        log.append(SessionEventFactory.turnStart());
        log.append(SessionEventFactory.stepStart(1, 1));
        log.append(SessionEventFactory.userMessage(1, 1, userMsg));
        log.append(SessionEventFactory.assistantChunk(1, 1, "片段", "text-delta"));
        log.append(SessionEventFactory.assistantMessage(1, 1, assistantMsg, null));
        log.append(SessionEventFactory.stepEnd(1, 1));
        log.append(SessionEventFactory.turnEnd(SessionEventFactory.completed()));

        List<Message> surface = log.deriveMessages();
        // chunk/turn/step 边界都不进表面，只有 user/assistant 两条
        assertEquals(2, surface.size());
        assertEquals(userMsg, surface.get(0));
        assertEquals(assistantMsg, surface.get(1));
    }

    @Test
    void replaceOpShadowsRangeAndProjectsReplacingEvent() {
        SessionLog log = newLog("s-4");
        Message original = user("原始消息");
        Message rewritten = user("改写后的消息");

        long userSeq = log.append(SessionEventFactory.userMessage(1, 1, original)).seq();
        log.append(SessionEventFactory.assistantMessage(1, 1, assistant("回答"), null));
        // 用 Replace 遮蔽 [userSeq, userSeq] 并投影改写消息
        log.append(new SessionEvent.UserMessage(
                0, Instant.now(),
                SessionEventFactory.userMessage(1, 2, rewritten).data(),
                new SurfaceIntent(SurfaceOp.replace(userSeq, userSeq), null)));

        // vendor 语义：被遮蔽消息从表面移除，替换事件投影追加到当前表面末尾（不保位）
        List<Message> surface = log.deriveMessages();
        assertEquals(2, surface.size());
        assertEquals("assistant", surface.get(0).role());
        assertEquals(rewritten, surface.get(1));
        // 原始消息不再出现在任何投影位置
        assertFalse(surface.contains(original));
    }

    @Test
    void endSeedBoundsLiveEventsAndRestoreResetsNextSeq() {
        SessionLog log = newLog("s-5");
        log.append(SessionEventFactory.userMessage("种子消息"));
        log.append(SessionEventFactory.sessionEndSeed());
        log.append(SessionEventFactory.userMessage("实时消息"));

        assertEquals(1, log.lastEndSeedSeq());
        assertEquals(2, log.firstLiveSeq());
        assertEquals(1, log.eventsAfter(1).size());

        // 恢复语义：整体替换后 nextSeq 从最后事件继续
        List<SessionEvent> persisted = log.events();
        SessionLog restored = newLog("s-5");
        restored.restore(persisted);
        assertEquals(persisted.size(), restored.size());
        assertEquals(persisted.size(), restored.nextSeq());
        assertEquals(log.deriveMessages(), restored.deriveMessages());
    }

    @Test
    void requestHeaderFoldsToLatestAndIsCachedIncrementally() {
        SessionLog log = newLog("s-6");
        assertNull(log.requestHeader());

        log.append(SessionEventFactory.requestHeader(
                java.util.Map.of("model", "m1"), java.util.Map.of(), "sys-1", List.of()));
        log.append(SessionEventFactory.requestContext("deepseek", "m1", null));
        assertEquals("sys-1", log.requestHeader().system());
        assertEquals("m1", log.requestContext().model());

        log.append(SessionEventFactory.requestHeader(
                java.util.Map.of("model", "m2"), java.util.Map.of(), "sys-2", List.of()));
        assertEquals("sys-2", log.requestHeader().system());
        assertTrue(log.requestHeader().config().containsKey("model"));
    }

    // ── helpers ──────────────────────────────────────────────────

    private SessionLog newLog(String sessionId) {
        return new SessionLog(sessionId,
                SessionHeader.create(sessionId, Instant.now(), "/tmp", "test-agent"));
    }

    private Message user(String text) {
        return Message.createUser(List.of(new TextBlock(text)), new MessageSource.UserSource());
    }

    private Message assistant(String text) {
        return Message.createAssistant(List.of(new TextBlock(text)),
                new MessageSource.ModelMessageSource("deepseek", "deepseek-chat"));
    }
}
