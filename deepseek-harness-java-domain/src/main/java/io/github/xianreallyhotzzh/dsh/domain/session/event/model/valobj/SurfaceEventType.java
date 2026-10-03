package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

/**
 * SurfaceEventType 表示会出现在对话「表面」（前端消息列表 / 模型历史）上的事件类型。
 * <p>
 * 只有 USER_MESSAGE / ASSISTANT_MESSAGE / TOOL_RESULT 三种能成为投影消息。
 */
public enum SurfaceEventType {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    TOOL_RESULT;

    /** 判断事件类型是否属于表面事件。 */
    public static boolean isSurfaceEventType(SessionEventType type) {
        return switch (type) {
            case USER_MESSAGE, ASSISTANT_MESSAGE, TOOL_RESULT -> true;
            default -> false;
        };
    }

    /** 转换为会话事件类型。 */
    public SessionEventType toSessionEventType() {
        return switch (this) {
            case USER_MESSAGE -> SessionEventType.USER_MESSAGE;
            case ASSISTANT_MESSAGE -> SessionEventType.ASSISTANT_MESSAGE;
            case TOOL_RESULT -> SessionEventType.TOOL_RESULT;
        };
    }
}
