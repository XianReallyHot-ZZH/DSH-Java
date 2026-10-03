package io.github.xianreallyhotzzh.dsh.domain.agent.service.run;

import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.AgentCancelCause;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.AgentStatus;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.valobj.InboxTarget;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.session.event.model.entity.SessionLog;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 一次可交互的智能体运行。
 * <p>
 * 调用流程：创建/恢复运行 → 接收输入 → 驱动 ReAct 循环 → 等待空闲 / 取消 / 查询。
 * <p>
 * L04 面：send / status / cancel / whenIdle / updateOptions / runMaintenance。
 * 流式 sink 与 drainWrittenArtifacts 分别随 L05 / L06 加入。
 */
public interface AgentRun {

    /** Agent 唯一标识。 */
    String id();

    /** 当前运行参数。 */
    AgentOptions options();

    /** 热更新下一次模型调用使用的运行参数。 */
    void updateOptions(AgentOptions options);

    /** 名下会话日志。 */
    SessionLog session();

    /** 当前是否正在执行驱动循环。 */
    AgentStatus status();

    /** 把一条消息写入收件箱，并按需唤醒驱动器。 */
    void send(Message message, InboxTarget target, boolean wakeup);

    /** 取消当前活动并按需清空待处理消息。 */
    void cancel(AgentCancelCause cause, boolean keepInbox);

    /** 当前活动完成后的 Future；空闲状态下返回已完成 Future。 */
    CompletableFuture<Void> whenIdle();

    /**
     * 在空闲相位执行维护任务（压缩/重建等；消费方随 L08 压缩引擎接入）。
     * 维护期间收到的唤醒请求在任务完成后补发。
     */
    <T> CompletableFuture<T> runMaintenance(Function<AtomicBoolean, CompletableFuture<T>> task);

    /** 释放当前运行占用的资源。 */
    CompletableFuture<Void> dispose();
}
