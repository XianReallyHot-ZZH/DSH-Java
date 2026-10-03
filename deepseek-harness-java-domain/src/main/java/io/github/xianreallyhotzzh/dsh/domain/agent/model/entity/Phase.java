package io.github.xianreallyhotzzh.dsh.domain.agent.model.entity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 智能体驱动的阶段状态机（三态：空闲 / 维护 / 运行）。
 * <p>
 * 职责：表达三种阶段，并承载中断与唤醒补偿语义——运行/维护阶段收到中断后，
 * 等待中的唤醒事件会在阶段回到空闲时补发。
 * <p>
 * 流程：空闲 → 维护或运行 → 中断/唤醒事件合并 → 回到空闲。
 */
public sealed interface Phase
        permits Phase.Idle, Phase.Maintenance, Phase.Running {

    /** 空闲阶段：没有正在执行的工作，记录最近完成轮次。 */
    record Idle(long lastTurn) implements Phase {}

    /** 维护阶段：非对话轮次的维护任务占用智能体（L08 压缩等）。 */
    record Maintenance(
            AtomicBoolean abort,
            long lastTurn,
            AtomicBoolean wakeRequested
    ) implements Phase {
        public Maintenance(long lastTurn) {
            this(new AtomicBoolean(false), lastTurn, new AtomicBoolean(false));
        }
    }

    /** 运行阶段：驱动器正在推进轮次和步骤。 */
    record Running(
            AtomicBoolean abort,
            long turn,
            long step,
            AtomicBoolean wakeRequested
    ) implements Phase {
        public Running(long lastTurn) {
            this(new AtomicBoolean(false), lastTurn, 0, new AtomicBoolean(false));
        }
    }
}
