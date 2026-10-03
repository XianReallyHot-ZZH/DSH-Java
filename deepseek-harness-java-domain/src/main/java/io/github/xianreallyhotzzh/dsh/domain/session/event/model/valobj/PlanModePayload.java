package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

/**
 * 计划模式切换事件载荷（L20 工作流与目标消费）。
 */
public record PlanModePayload(
        String mode,         // "enter" | "exit"
        String plan          // the approved plan text (null on enter)
) {
    public PlanModePayload {
        if (mode == null || (!mode.equals("enter") && !mode.equals("exit"))) {
            throw new IllegalArgumentException("mode must be 'enter' or 'exit'");
        }
    }
}
