package io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj;

import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ToolSchema;

import java.util.List;
import java.util.Objects;

/**
 * 单次 LLM 生成请求参数。
 *
 * @param provider 模型提供方编码（路由到已注册适配器）
 * @param model 模型编码
 * @param reasoningEffort 推理级别 low / medium / high（可空）
 * @param messages 会话历史（不含 system，system 独立携带）
 * @param system 系统提示词（可空表示不发送）
 * @param tools 可用工具 schema（L03 恒为 null，L06 起接入）
 * @param temperature 采样温度（可空表示请求中省略）
 * @param maxTokens 最大输出 token（可空用模型默认）
 * @param stop 停止序列（可空）
 * @param sessionId 会话 ID（透传到上游请求头，便于链路对账）
 * @param purpose 调用用途标记（compaction 等，L08 起使用）
 * @param baseUrl 渠道基础地址（请求级覆盖，可空）
 * @param apiKey 渠道凭据（请求级覆盖，可空）
 * @param protocol 渠道协议编码（openai / anthropic / ollama；空值按 openai 兼容处理）
 */
public record GenerateOptions(
        String provider,
        String model,
        String reasoningEffort,
        List<Message> messages,
        String system,
        List<ToolSchema> tools,
        Double temperature,
        Integer maxTokens,
        List<String> stop,
        String sessionId,
        String purpose,
        String baseUrl,
        String apiKey,
        String protocol
) {
    public GenerateOptions {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        messages = List.copyOf(messages);
        tools = tools == null ? null : List.copyOf(tools);
        stop = stop == null ? null : List.copyOf(stop);
    }
}
