package io.github.xianreallyhotzzh.dsh.domain.agent.model.entity;

/**
 * Agent 运行时的模型渠道配置。
 * <p>
 * L03 子集不含 approvalMode（随 L13 运行时治理加入）与热更新。
 *
 * @param channelCode 模型渠道编码
 * @param provider 模型提供方编码（LLM 端口路由键）
 * @param model 模型编码
 * @param maxTokens 最大输出 token（可空用适配器默认）
 * @param reasoningEffort 推理级别 low / medium / high（可空）
 * @param baseUrl 渠道基础地址（可空用适配器默认）
 * @param apiKey 渠道凭据（可空用适配器默认）
 * @param protocol 渠道协议编码（可空按 openai 兼容处理）
 */
public record AgentOptions(
        String channelCode,
        String provider,
        String model,
        Integer maxTokens,
        String reasoningEffort,
        String baseUrl,
        String apiKey,
        String protocol
) {
    public AgentOptions {
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be a positive integer");
        }
    }
}
