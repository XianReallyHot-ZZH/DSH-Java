package io.github.xianreallyhotzzh.dsh.cases.orchestration;

/**
 * 用例管线工厂契约：绑定操作名、根节点与上下文工厂。
 *
 * @param <I> 请求类型
 * @param <C> 编排上下文类型
 * @param <O> 结果类型
 */
public interface CasePipelineFactory<I, C, O> {

    CasePipeline<I, C, O> pipeline();
}
