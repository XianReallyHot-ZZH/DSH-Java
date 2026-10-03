package io.github.xianreallyhotzzh.dsh.domain.agent.contract.outbound;

import java.util.concurrent.CompletableFuture;

/**
 * AgentEventListener 表示智能体领域事件的监听契约（出站端口）。
 * <p>
 * L04 消费：cancel 发出 {@code agent/cancelled}。serial/waterfall 的消费方随
 * 后续课（L06 工具生命周期、L14 插件 Hook）加入。
 */
public interface AgentEventListener {

    /** 发出一个事件（尽力而为，不阻塞调用方）。 */
    void emit(String name, Object payload);

    /** 发出一个需要顺序消费的事件。 */
    CompletableFuture<Void> serial(String name, Object payload);

    /** 发出一个瀑布式事件：监听方依次加工，无监听方时执行默认动作。 */
    <T> CompletableFuture<T> waterfall(String name, Object payload, CompletableFuture<T> defaultAction);
}
