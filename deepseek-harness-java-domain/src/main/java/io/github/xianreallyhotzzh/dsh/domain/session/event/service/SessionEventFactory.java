package io.github.xianreallyhotzzh.dsh.domain.session.event.service;

import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.TokenUsage;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.MessageSource;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionEvent;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * SessionEventFactory 负责会话事件的创建与装配。
 * <p>
 * 统一以 seq=0 创建（占位），由 {@code SessionLog.append} 分配权威序列号——
 * 调用方永远不需要自己管理 seq。
 */
public final class SessionEventFactory {

    private SessionEventFactory() {}

    private static Instant now() {
        return Instant.now();
    }

    // ── Turn / Step boundaries ───────────────────────────────────

    /** 创建回合开始事件。 */
    public static SessionEvent.TurnStart turnStart() {
        return new SessionEvent.TurnStart(0, now());
    }

    /** 使用指定时间创建回合开始事件。 */
    public static SessionEvent.TurnStart turnStart(Instant time) {
        return new SessionEvent.TurnStart(0, time);
    }

    /** 创建回合结束事件。 */
    public static SessionEvent.TurnEnd turnEnd(TurnEndReason reason) {
        return new SessionEvent.TurnEnd(0, now(), reason);
    }

    /** 使用指定时间和原因创建回合结束事件。 */
    public static SessionEvent.TurnEnd turnEnd(Instant time, TurnEndReason reason) {
        return new SessionEvent.TurnEnd(0, time, reason);
    }

    /** 创建步开始事件。 */
    public static SessionEvent.StepStart stepStart(long turn, long step) {
        return new SessionEvent.StepStart(0, now(), turn, step);
    }

    /** 使用指定时间创建步开始事件。 */
    public static SessionEvent.StepStart stepStart(Instant time, long turn, long step) {
        return new SessionEvent.StepStart(0, time, turn, step);
    }

    /** 创建步结束事件。 */
    public static SessionEvent.StepEnd stepEnd(long turn, long step) {
        return new SessionEvent.StepEnd(0, now(), turn, step);
    }

    /** 使用指定时间创建步结束事件。 */
    public static SessionEvent.StepEnd stepEnd(Instant time, long turn, long step) {
        return new SessionEvent.StepEnd(0, time, turn, step);
    }

    // ── Messages ─────────────────────────────────────────────────

    /** 创建助手增量分片事件。 */
    public static SessionEvent.AssistantChunk assistantChunk(long turn, long step, String chunk, String kind) {
        return new SessionEvent.AssistantChunk(0, now(),
                new AssistantChunkPayload(turn, step, chunk, kind));
    }

    /** 创建用户消息事件（Append 投影）。 */
    public static SessionEvent.UserMessage userMessage(long turn, long step, Message message) {
        return new SessionEvent.UserMessage(0, now(),
                new UserMessagePayload(turn, step, message),
                new SurfaceIntent(SurfaceOp.append(), null));
    }

    /** 创建用户消息事件（显式投影意图——Replace 场景由 L07/L20 的调用方给出）。 */
    public static SessionEvent.UserMessage userMessage(long turn, long step, Message message, SurfaceIntent intent) {
        return new SessionEvent.UserMessage(0, now(),
                new UserMessagePayload(turn, step, message),
                intent);
    }

    /** 用纯文本创建用户消息事件（便捷重载，turn/step 记 0）。 */
    public static SessionEvent.UserMessage userMessage(String content) {
        Message msg = Message.createUser(
                List.of(new TextBlock(content)),
                new MessageSource.UserSource()
        );
        return userMessage(0, 0, msg);
    }

    /** 创建注入型用户消息事件（插件/压缩等系统侧插入，Append 投影）。 */
    public static SessionEvent.UserMessage injectedUserMessage(long turn, long step, Message message) {
        return new SessionEvent.UserMessage(0, now(),
                new UserMessagePayload(turn, step, message),
                new SurfaceIntent(SurfaceOp.append(), null));
    }

    /** 创建助手完整消息事件（Append 投影）。 */
    public static SessionEvent.AssistantMessage assistantMessage(
            long turn, long step, Message message, TokenUsage usage
    ) {
        return new SessionEvent.AssistantMessage(0, now(),
                new AssistantMessagePayload(turn, step, message, usage),
                new SurfaceIntent(SurfaceOp.append(), null));
    }

    // ── Tool calls ───────────────────────────────────────────────

    /** 创建工具调用事件（L06 工具内核写入）。 */
    public static SessionEvent.ToolCall toolCall(long turn, long step, String callId, String name, String arguments) {
        return new SessionEvent.ToolCall(0, now(),
                new ToolCallPayload(turn, step, callId, name, arguments));
    }

    /** 创建工具结果事件（Append 投影；L06 写入）。 */
    public static SessionEvent.ToolResult toolResult(
            long turn, long step, Message message,
            Map<String, Object> error, Map<String, Object> meta
    ) {
        return new SessionEvent.ToolResult(0, now(),
                new ToolResultPayload(turn, step, message, error, meta),
                new SurfaceIntent(SurfaceOp.append(), null));
    }

    // ── Todo ─────────────────────────────────────────────────────

    /** 创建 Todo 写入事件。 */
    public static SessionEvent.TodoWrite todoWrite(List<TodoItemPayload> todos) {
        return new SessionEvent.TodoWrite(0, now(), new TodoWritePayload(todos));
    }

    // ── Request metadata ─────────────────────────────────────────

    /** 创建请求头事件：固化模型配置、适配器默认值、系统提示词与工具列表。 */
    public static SessionEvent.RequestHeader requestHeader(
            Map<String, Object> config, Map<String, Object> adapterDefaults,
            String system, List<Map<String, Object>> tools
    ) {
        return new SessionEvent.RequestHeader(0, now(),
                new RequestHeaderPayload(config, adapterDefaults, system, tools));
    }

    /** 创建请求上下文事件（provider/model/上下文窗口路由元数据）。 */
    public static SessionEvent.RequestContext requestContext(String provider, String model, Long contextWindow) {
        return new SessionEvent.RequestContext(0, now(),
                new RequestContextPayload(provider, model, contextWindow));
    }

    // ── Session lifecycle ────────────────────────────────────────

    /** 创建种子历史边界事件（L10 恢复语义消费）。 */
    public static SessionEvent.SessionEndSeed sessionEndSeed() {
        return new SessionEvent.SessionEndSeed(0, now());
    }

    // ── Agent inbox ──────────────────────────────────────────────

    /** 创建收件箱拼接事件：记录目标、起始位置、删除数量、插入消息与结果。 */
    public static SessionEvent.AgentInboxSpliced inboxSpliced(
            String target, int start, int removedCount,
            List<Message> inserted, String outcome
    ) {
        return new SessionEvent.AgentInboxSpliced(0, now(),
                new InboxSplicedPayload(target, start, removedCount, inserted, outcome));
    }

    // ── Plan mode ────────────────────────────────────────────────

    /** 创建计划模式进入事件。 */
    public static SessionEvent.PlanModeChange planModeEnter() {
        return new SessionEvent.PlanModeChange(0, now(), new PlanModePayload("enter", null));
    }

    /** 创建计划模式退出事件（携带已批准的计划文本）。 */
    public static SessionEvent.PlanModeChange planModeExit(String plan) {
        return new SessionEvent.PlanModeChange(0, now(), new PlanModePayload("exit", plan));
    }

    // ── Generic ──────────────────────────────────────────────────

    /** 创建通用扩展事件。 */
    public static SessionEvent.Generic generic(SessionEventType type, String payload) {
        return new SessionEvent.Generic(0, now(), type, payload);
    }

    // ── Turn end reason helpers ──────────────────────────────────

    public static TurnEndReason completed() { return new TurnEndReason.Completed(); }
    public static TurnEndReason maxTokens() { return new TurnEndReason.MaxTokens(); }
    public static TurnEndReason blocked() { return new TurnEndReason.Blocked(); }
    public static TurnEndReason aborted(String reason) { return new TurnEndReason.Aborted(reason); }
    public static TurnEndReason error(String message) { return new TurnEndReason.Error(message, "UNKNOWN"); }
    public static TurnEndReason error(String message, String code) { return new TurnEndReason.Error(message, code); }
    public static TurnEndReason interrupted() { return new TurnEndReason.Interrupted(); }
}
