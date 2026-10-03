package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

/**
 * 助手增量分片事件载荷（回放可跳过；L05 SSE 的 chunk 帧由此直通）。
 */
public record AssistantChunkPayload(long turn, long step, String chunk, String kind) {}
