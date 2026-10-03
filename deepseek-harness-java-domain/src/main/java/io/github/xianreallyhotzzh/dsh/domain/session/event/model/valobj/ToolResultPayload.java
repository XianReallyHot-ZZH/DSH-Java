package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

import java.util.Map;

/**
 * 工具结果事件载荷（属于表面投影：结果以 tool 角色消息回灌模型历史）。
 */
public record ToolResultPayload(
        long turn,
        long step,
        Message message,
        Map<String, Object> error,
        Map<String, Object> meta
) {}
