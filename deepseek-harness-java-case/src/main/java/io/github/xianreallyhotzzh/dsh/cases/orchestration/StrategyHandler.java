package io.github.xianreallyhotzzh.dsh.cases.orchestration;

/**
 * 策略处理器：用例编排树中的一个节点。
 *
 * @param <I> 请求参数类型
 * @param <C> 动态上下文类型（可变，随链流转）
 * @param <O> 输出类型
 */
@FunctionalInterface
public interface StrategyHandler<I, C, O> {

    O apply(I requestParameter, C dynamicContext) throws Exception;
}
