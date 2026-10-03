package io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity;

import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.*;

import java.time.Instant;

/**
 * SessionEvent 表示会话事件：会话中一切事实的不可变记录（L04 对拍基准——
 * sealed permits 集合与 vendor 完全一致，16 种，不允许随意新增种类）。
 * <p>
 * 事件先于 SSE / 持久化 / 回放存在：流式帧（L05）、事件落库（L09）、历史回放
 * 与投影（L10）都只是这份事实源的不同读者。
 */
public sealed interface SessionEvent permits
        SessionEvent.TurnStart,
        SessionEvent.TurnEnd,
        SessionEvent.StepStart,
        SessionEvent.StepEnd,
        SessionEvent.UserMessage,
        SessionEvent.AssistantChunk,
        SessionEvent.AssistantMessage,
        SessionEvent.ToolCall,
        SessionEvent.ToolResult,
        SessionEvent.TodoWrite,
        SessionEvent.RequestHeader,
        SessionEvent.RequestContext,
        SessionEvent.SessionEndSeed,
        SessionEvent.PlanModeChange,
        SessionEvent.AgentInboxSpliced,
        SessionEvent.Generic {

    long seq();

    Instant time();

    SessionEventType type();

    /** 判断事件是否属于表面投影（会成为前端消息 / 模型历史）。 */
    default boolean isSurfaceEvent() {
        return SurfaceEventType.isSurfaceEventType(type());
    }

    /** 表面投影意图；非表面事件返回 null。 */
    default SurfaceIntent surfaceIntent() {
        return null;
    }

    // ── Variants ────────────────────────────────────────────────

    /** 回合开始。 */
    record TurnStart(long seq, Instant time) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.TURN_START; }
    }

    /** 回合结束（携带结束原因）。 */
    record TurnEnd(long seq, Instant time, TurnEndReason reason) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.TURN_END; }
    }

    /** 步开始。 */
    record StepStart(long seq, Instant time, long turn, long step) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.STEP_START; }
    }

    /** 步结束。 */
    record StepEnd(long seq, Instant time, long turn, long step) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.STEP_END; }
    }

    /** 用户消息（表面事件）。 */
    record UserMessage(
            long seq, Instant time,
            UserMessagePayload data,
            SurfaceIntent surfaceIntent
    ) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.USER_MESSAGE; }
    }

    /** 助手增量分片（回放可跳过；L05 直通 SSE）。 */
    record AssistantChunk(
            long seq, Instant time,
            AssistantChunkPayload data
    ) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.ASSISTANT_CHUNK; }
    }

    /** 助手完整消息（表面事件）。 */
    record AssistantMessage(
            long seq, Instant time,
            AssistantMessagePayload data,
            SurfaceIntent surfaceIntent
    ) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.ASSISTANT_MESSAGE; }
    }

    /** 工具调用（L06 写入）。 */
    record ToolCall(
            long seq, Instant time,
            ToolCallPayload data
    ) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.TOOL_CALL; }
    }

    /** 工具结果（表面事件；L06 写入）。 */
    record ToolResult(
            long seq, Instant time,
            ToolResultPayload data,
            SurfaceIntent surfaceIntent
    ) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.TOOL_RESULT; }
    }

    /** Todo 写入（UI 态）。 */
    record TodoWrite(long seq, Instant time, TodoWritePayload data) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.TODO_WRITE; }
    }

    /** 请求头（配置/系统提示词/工具列表的固化快照）。 */
    record RequestHeader(long seq, Instant time, RequestHeaderPayload data) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.REQUEST_HEADER; }
    }

    /** 请求上下文（路由元数据）。 */
    record RequestContext(long seq, Instant time, RequestContextPayload data) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.REQUEST_CONTEXT; }
    }

    /** 种子历史与实时工作的边界标记（L10 恢复语义消费）。 */
    record SessionEndSeed(long seq, Instant time) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.SESSION_END_SEED; }
    }

    /** 计划模式切换（L20 消费）。 */
    record PlanModeChange(long seq, Instant time, PlanModePayload data) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.PLAN_MODE_CHANGE; }
    }

    /** 收件箱拼接（Inbox 结构变更的事实记录）。 */
    record AgentInboxSpliced(long seq, Instant time, InboxSplicedPayload data) implements SessionEvent {
        @Override public SessionEventType type() { return SessionEventType.AGENT_INBOX_SPLICED; }
    }

    /** 通用扩展事件：承载 sealed 集合之外的遗留/扩展类型（L21 JSONL 兼容）。 */
    record Generic(
            long seq, Instant time,
            SessionEventType type,
            String payload
    ) implements SessionEvent {
        @Override
        public boolean isSurfaceEvent() {
            return false;
        }
    }
}
