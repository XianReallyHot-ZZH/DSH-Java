package io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj;

/**
 * 收件箱投递目标：下一回合（NEXT_TURN）或下一步（NEXT_STEP，L06 工具续步用）。
 * <p>
 * wireName 用于 AgentInboxSpliced 事件的 target 字段（事件是收件箱结构的唯一事实源）。
 */
public enum InboxTarget {
    NEXT_TURN("next-turn"),
    NEXT_STEP("next-step");

    private final String wireName;

    InboxTarget(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    /** 按 wire 名称反查；未知名称抛 IllegalArgumentException。 */
    public static InboxTarget fromWireName(String name) {
        for (InboxTarget t : values()) {
            if (t.wireName.equals(name)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown inbox target: " + name);
    }
}
