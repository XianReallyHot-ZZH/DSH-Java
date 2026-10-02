package io.github.xianreallyhotzzh.dsh.cases.orchestration;

/**
 * 策略路由抽象基类：节点自身产出结果则返回，否则路由到下一节点。
 *
 * @param <I> 请求参数类型
 * @param <C> 动态上下文类型
 * @param <O> 输出类型
 */
public abstract class AbstractStrategyRouter<I, C, O> implements StrategyHandler<I, C, O> {

    protected StrategyHandler<I, C, O> defaultStrategyHandler = (request, context) -> null;

    /** 执行当前节点核心逻辑；返回 null 时继续路由下一节点。 */
    protected abstract O doApply(I requestParameter, C dynamicContext) throws Exception;

    /** 根据当前请求和上下文选择下一执行节点。 */
    public abstract StrategyHandler<I, C, O> getNext(I requestParameter, C dynamicContext) throws Exception;

    /** 驱动节点执行：节点有结果则返回，否则继续路由下一节点。 */
    @Override
    public final O apply(I requestParameter, C dynamicContext) throws Exception {
        O result = doApply(requestParameter, dynamicContext);
        if (result != null) {
            return result;
        }
        return router(requestParameter, dynamicContext);
    }

    /** 调用下一节点并透传请求与上下文。 */
    protected O router(I requestParameter, C dynamicContext) throws Exception {
        return getNext(requestParameter, dynamicContext).apply(requestParameter, dynamicContext);
    }
}
