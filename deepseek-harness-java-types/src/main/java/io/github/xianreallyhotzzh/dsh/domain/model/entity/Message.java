package io.github.xianreallyhotzzh.dsh.domain.model.entity;

import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ContentBlock;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 消息的不可变表示，同时用于投递、持久化历史和模型请求。
 *
 * @param id      稳定的消息 ID（创建时生成）
 * @param role    会话角色：system / user / assistant（tool 随 L06 加入）
 * @param content 发送给模型的完整内容块
 * @param source  消息来源，表示由谁或什么组件产生
 */
public record Message(
        String id,
        String role,
        List<ContentBlock> content,
        MessageSource source
) {
    public Message {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
        content = List.copyOf(content);
        Objects.requireNonNull(source, "source");
    }

    /** 创建一条带新 ID 的用户消息。 */
    public static Message createUser(List<ContentBlock> content, MessageSource source) {
        return new Message(UUID.randomUUID().toString(), "user", content, source);
    }

    /** 创建一条带新 ID 的助手消息。 */
    public static Message createAssistant(List<ContentBlock> content, MessageSource.ModelMessageSource source) {
        return new Message(UUID.randomUUID().toString(), "assistant", content, source);
    }
}
