package io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity;

import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.SessionEventType;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.SurfaceEventType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L04 对拍点钉子：sealed permits 事件种类集合与 vendor 的 SessionEvent 完全一致。
 * 期望清单逐字抄自 vendor（cn.xiaofuge...SessionEvent 的 permits 列表），
 * 任何一边多一种/少一种/换名字都会在这里红。
 */
class SessionEventPermitsTest {

    /** vendor sealed permits 的字面全集（16 种），顺序也一致。 */
    private static final List<String> VENDOR_PERMITS = List.of(
            "TurnStart",
            "TurnEnd",
            "StepStart",
            "StepEnd",
            "UserMessage",
            "AssistantChunk",
            "AssistantMessage",
            "ToolCall",
            "ToolResult",
            "TodoWrite",
            "RequestHeader",
            "RequestContext",
            "SessionEndSeed",
            "PlanModeChange",
            "AgentInboxSpliced",
            "Generic"
    );

    @Test
    void sealedPermitsMatchVendorExactly() {
        List<String> actual = Arrays.stream(SessionEvent.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toList());
        assertEquals(VENDOR_PERMITS, actual);
    }

    @Test
    void surfaceEventKindsAreExactlyUserAssistantToolResult() {
        Set<SessionEventType> surface = Arrays.stream(SessionEventType.values())
                .filter(SurfaceEventType::isSurfaceEventType)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                SessionEventType.USER_MESSAGE,
                SessionEventType.ASSISTANT_MESSAGE,
                SessionEventType.TOOL_RESULT
        ), surface);
    }

    @Test
    void wireNamesMatchVendorVocabulary() {
        assertEquals("turn/start", SessionEventType.TURN_START.wireName());
        assertEquals("turn/end", SessionEventType.TURN_END.wireName());
        assertEquals("step/start", SessionEventType.STEP_START.wireName());
        assertEquals("step/end", SessionEventType.STEP_END.wireName());
        assertEquals("user/message", SessionEventType.USER_MESSAGE.wireName());
        assertEquals("assistant/chunk", SessionEventType.ASSISTANT_CHUNK.wireName());
        assertEquals("assistant/message", SessionEventType.ASSISTANT_MESSAGE.wireName());
        assertEquals("tool/call", SessionEventType.TOOL_CALL.wireName());
        assertEquals("tool/result", SessionEventType.TOOL_RESULT.wireName());
        assertEquals("todo/write", SessionEventType.TODO_WRITE.wireName());
        assertEquals("request/header", SessionEventType.REQUEST_HEADER.wireName());
        assertEquals("request/context", SessionEventType.REQUEST_CONTEXT.wireName());
        assertEquals("session/end-seed", SessionEventType.SESSION_END_SEED.wireName());
        assertEquals("plan/mode", SessionEventType.PLAN_MODE_CHANGE.wireName());
        assertEquals("agent/inbox/spliced", SessionEventType.AGENT_INBOX_SPLICED.wireName());
        assertEquals("agent/event", SessionEventType.AGENT_EVENT.wireName());
        // 反查：未知 wire 名抛参数异常（L21 JSONL v3 守卫依赖）
        assertEquals(SessionEventType.TURN_START, SessionEventType.fromWireName("turn/start"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> SessionEventType.fromWireName("no/such"));
    }

    @Test
    void chunkAndTodoAndRequestContextAreIgnorableForReplay() {
        assertTrue(SessionEventType.ASSISTANT_CHUNK.ignorable());
        assertTrue(SessionEventType.TODO_WRITE.ignorable());
        assertTrue(SessionEventType.REQUEST_CONTEXT.ignorable());
        assertFalse(SessionEventType.USER_MESSAGE.ignorable());
        assertFalse(SessionEventType.TOOL_RESULT.ignorable());
        assertFalse(SessionEventType.TURN_END.ignorable());
    }
}
