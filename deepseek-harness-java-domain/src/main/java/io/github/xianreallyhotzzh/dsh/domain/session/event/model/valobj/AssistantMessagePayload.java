package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.TokenUsage;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

/**
 * 助手完整消息事件载荷（步的最终结果，属于表面投影）。
 */
public record AssistantMessagePayload(
        long turn,
        long step,
        Message message,
        TokenUsage usage
) {}
