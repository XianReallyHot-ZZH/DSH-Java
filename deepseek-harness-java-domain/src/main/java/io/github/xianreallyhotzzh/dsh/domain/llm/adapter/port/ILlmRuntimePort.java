package io.github.xianreallyhotzzh.dsh.domain.llm.adapter.port;

import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.LlmAdapter;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.GenerateOptions;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;

import java.util.concurrent.Flow;

/**
 * 大模型运行时端口（领域出站端口）。
 * <p>
 * 维护 provider 与适配器的注册关系并统一提供流式响应入口；对上层屏蔽供应商差异。
 * listProviders / listModels / resolveModel 随 L11 模型渠道接入。
 */
public interface ILlmRuntimePort {

    void registerAdapter(String provider, LlmAdapter adapter);

    void unregisterAdapter(String provider);

    /** 判断 provider 是否已注册适配器。 */
    boolean hasAdapter(String provider);

    /** 发起流式模型调用；路由失败也以 Error Finish 分片收尾，不向调用方抛异常。 */
    Flow.Publisher<StreamChunk> stream(GenerateOptions options);
}
