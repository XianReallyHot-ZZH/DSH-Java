package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

import java.util.List;

/**
 * Todo 写入事件载荷（UI 态；回放可跳过）。
 */
public record TodoWritePayload(List<TodoItemPayload> todos) {
    public TodoWritePayload {
        todos = List.copyOf(todos);
    }
}
