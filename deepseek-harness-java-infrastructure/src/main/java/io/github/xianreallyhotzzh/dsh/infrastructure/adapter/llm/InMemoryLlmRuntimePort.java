package io.github.xianreallyhotzzh.dsh.infrastructure.adapter.llm;

import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.LlmAdapter;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.FinishReason;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.GenerateOptions;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.LlmErrorCodes;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.LlmFailure;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内存版大模型运行时端口实现。
 * <p>
 * 执行流程：
 * 1. 维护 provider 到适配器的映射；
 * 2. 根据 provider 路由到具体模型适配器；
 * 3. 将异常统一包装成结束分片返回——路由失败与适配器崩溃都以 Error Finish 收尾，
 *    不向调用方抛异常（信封 50000 只保留给 harness 自身故障）。
 * <p>
 * 与 vendor 的差异（后续课收敛）：重试策略（retryingStream）与协议路由兜底工厂
 * 随 L11 模型渠道接入；listProviders/listModels/resolveModel 同期补齐。
 */
public class InMemoryLlmRuntimePort implements ILlmRuntimePort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLlmRuntimePort.class);

    private final Map<String, LlmAdapter> adapters = new ConcurrentHashMap<>();

    /** 注册模型 Provider 对应的 LLM 适配器。 */
    @Override
    public void registerAdapter(String provider, LlmAdapter adapter) {
        adapters.put(provider, adapter);
        log.info("Registered LLM adapter for provider: {}", provider);
    }

    /** 取消模型 Provider 的适配器注册。 */
    @Override
    public void unregisterAdapter(String provider) {
        adapters.remove(provider);
        log.info("Unregistered LLM adapter for provider: {}", provider);
    }

    @Override
    public boolean hasAdapter(String provider) {
        return adapters.containsKey(provider);
    }

    /**
     * 通过注册的适配器发起流式模型调用。
     * <p>流程：按 provider 查找适配器 → 订阅适配器输出 → 统一包装增量与异常。
     * Provider 不存在时在订阅时发布一条 Error 结束分片并收尾。
     * <p>
     * 与 vendor 的差异（防御性修正，见 lesson 文档）：vendor 在 stream() 调用时即刻
     * 订阅适配器——若适配器在调用方订阅前就开始发布（如同步回放的实现），
     * SubmissionPublisher 会因无订阅者而丢弃分片。本实现推迟到第一个下游订阅者
     * 到位后才桥接适配器，消除该竞态。
     */
    @Override
    public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
        LlmAdapter adapter = adapters.get(options.provider());
        if (adapter == null) {
            log.error("模型路由失败：provider='{}' 未注册适配器，可用 providers={}",
                    options.provider(), adapters.keySet());
            LlmFailure failure = new LlmFailure(
                    "No adapter registered for provider '" + options.provider()
                            + "'. Available providers: " + adapters.keySet(),
                    LlmErrorCodes.UNKNOWN
            );
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { }
                    @Override public void cancel() { }
                });
                subscriber.onNext(new StreamChunk.Finish(new FinishReason.Error(failure)));
                subscriber.onComplete();
            };
        }

        // 包装适配器的流式输出，统一异常表现；首个订阅者到位后再桥接
        return new LazyBridgePublisher(adapter, options);
    }

    /**
     * 惰性桥接发布器：第一个下游订阅者到位后才订阅适配器并把输出桥接给全部订阅者。
     * 适配器异常统一收敛为 Error Finish 分片。
     */
    private final class LazyBridgePublisher extends SubmissionPublisher<StreamChunk> {

        private final LlmAdapter adapter;
        private final GenerateOptions options;
        private boolean bridged;

        LazyBridgePublisher(LlmAdapter adapter, GenerateOptions options) {
            this.adapter = adapter;
            this.options = options;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super StreamChunk> subscriber) {
            super.subscribe(subscriber);
            bridgeAdapterOnce();
        }

        private synchronized void bridgeAdapterOnce() {
            if (bridged) return;
            bridged = true;
            adapter.stream(options).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(StreamChunk item) {
                    LazyBridgePublisher.this.submit(item);
                }

                @Override
                public void onError(Throwable throwable) {
                    LlmFailure failure = new LlmFailure(
                            throwable.getMessage() != null ? throwable.getMessage() : "LLM adapter failed",
                            LlmErrorCodes.UNKNOWN
                    );
                    log.error("LLM stream error for provider={}", options.provider(), throwable);
                    LazyBridgePublisher.this.submit(new StreamChunk.Finish(new FinishReason.Error(failure)));
                    LazyBridgePublisher.this.close();
                }

                @Override
                public void onComplete() {
                    LazyBridgePublisher.this.close();
                }
            });
        }
    }
}
