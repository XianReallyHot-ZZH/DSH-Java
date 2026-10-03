package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

/**
 * 工具调用事件载荷（L06 工具内核写入；事件种类本课即就位）。
 */
public record ToolCallPayload(long turn, long step, String callId, String name, String arguments) {}
