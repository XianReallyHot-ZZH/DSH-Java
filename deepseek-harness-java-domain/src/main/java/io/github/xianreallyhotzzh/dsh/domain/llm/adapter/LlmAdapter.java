package io.github.xianreallyhotzzh.dsh.domain.llm.adapter;

import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.GenerateOptions;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;

import java.util.concurrent.Flow;

/**
 * 与具体供应商无关的大模型适配器契约。
 * <p>
 * 基础设施层实现具体供应商适配器；运行时端口按 provider 选择对应适配器；
 * 调用方通过流式接口消费模型输出。
 * <p>
 * L03 最小面只含 stream 与 providerId；providerInfo / listModels / resolveModel /
 * retryPolicy 随 L11 模型渠道接入。
 */
public interface LlmAdapter {

    /** 从模型调用中持续输出分片；适配器保证以 Finish 分片收尾（异常也收敛为 Error Finish）。 */
    Flow.Publisher<StreamChunk> stream(GenerateOptions options);

    /** 当前适配器服务的 provider 标识。 */
    String providerId();
}
