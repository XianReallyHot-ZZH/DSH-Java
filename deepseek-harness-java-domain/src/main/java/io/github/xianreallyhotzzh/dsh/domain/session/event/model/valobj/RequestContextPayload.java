package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

/**
 * 请求上下文事件载荷（路由元数据，回放可跳过）。
 */
public record RequestContextPayload(String provider, String model, Long contextWindow) {}
