package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;

/**
 * 用户消息事件载荷。role 必须是 user——错投角色在构造期即失败，而不是投影期。
 */
public record UserMessagePayload(long turn, long step, Message message) {
    public UserMessagePayload {
        if (message != null && !"user".equals(message.role())) {
            throw new IllegalArgumentException("UserMessagePayload requires role=user, got: " + message.role());
        }
    }
}
