package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

/**
 * Todo 单项载荷。
 */
public record TodoItemPayload(String content, String status) {
    public TodoItemPayload {
        if (status == null || (!status.equals("pending") && !status.equals("in_progress") && !status.equals("completed"))) {
            throw new IllegalArgumentException("Invalid todo status: " + status);
        }
    }
}
