package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

/**
 * SessionEventType 表示会话事件类型。
 * <p>
 * wireName 是事件在持久化与传输层的名称（L05 SSE / L09 落库 / L21 JSONL 都用它）；
 * ignorable 标记「回放时可以跳过而不影响正确性」的辅助事件（分片、UI 态、路由元数据）。
 */
public enum SessionEventType {

    // ── Turn boundaries ───────────────────────────────────────────
    TURN_START("turn/start", false),
    TURN_END("turn/end", false),

    // ── Step boundaries ───────────────────────────────────────────
    STEP_START("step/start", false),
    STEP_END("step/end", false),

    // ── Messages ──────────────────────────────────────────────────
    USER_MESSAGE("user/message", false),
    ASSISTANT_CHUNK("assistant/chunk", true),   // chunks are replay-only; a reader can skip
    ASSISTANT_MESSAGE("assistant/message", false),

    // ── Tool calls ────────────────────────────────────────────────
    TOOL_CALL("tool/call", false),
    TOOL_RESULT("tool/result", false),

    // ── Todo ──────────────────────────────────────────────────────
    TODO_WRITE("todo/write", true),              // UI-state only; non-critical for replay

    // ── Request metadata ─────────────────────────────────────────
    REQUEST_HEADER("request/header", false),
    REQUEST_CONTEXT("request/context", true),    // routing metadata; informational

    // ── Session lifecycle ────────────────────────────────────────
    SESSION_END_SEED("session/end-seed", false),

    // ── Plan mode ────────────────────────────────────────────────
    PLAN_MODE_CHANGE("plan/mode", false),

    // ── Agent inbox ───────────────────────────────────────────────
    AGENT_INBOX_SPLICED("agent/inbox/spliced", false),

    // ── Agent lifecycle events (emitted via AgentEventListener) ──
    AGENT_EVENT("agent/event", true),  // informational; e.g. agent/cancelled, turn lifecycle

    // ── Legacy compatibility (mapped from old event types) ───────
    TASK_SUBMITTED("task/submitted", true),
    TASK_AUTO_APPROVED("task/auto-approved", true),
    TASK_APPROVAL_REQUIRED("task/approval.required", true),
    TASK_APPROVED("task/approved", true),
    TASK_EXECUTION_STARTED("task.execution/started", true),
    TASK_EXECUTION_COMPLETED("task.execution/completed", true),
    TASK_EXECUTION_FAILED("task.execution/failed", true);

    private final String wireName;
    private final boolean ignorable;

    SessionEventType(String wireName, boolean ignorable) {
        this.wireName = wireName;
        this.ignorable = ignorable;
    }

    public String wireName() {
        return wireName;
    }

    public boolean ignorable() {
        return ignorable;
    }

    /** 按 wire 名称反查事件类型；未知名称抛 IllegalArgumentException（L21 JSONL v3 守卫依赖）。 */
    public static SessionEventType fromWireName(String name) {
        for (SessionEventType t : values()) {
            if (t.wireName.equals(name)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown session event type: " + name);
    }
}
