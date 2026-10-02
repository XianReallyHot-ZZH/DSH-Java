package io.github.xianreallyhotzzh.dsh.infrastructure.adapter.llm.deepseek;

import java.util.Objects;

/**
 * {@link DeepSeekAdapter} 的配置项。
 * <p>
 * 与 vendor 的差异（后续课收敛）：模型目录（models）与重试策略（retryPolicy）
 * 分别随 L11 模型渠道 / 重试机制接入，本课从 harness.llm.deepseek.* 直接取值。
 *
 * @param baseUrl OpenAI 兼容基础地址（如 https://api.deepseek.com/v1）
 * @param apiKey Bearer 凭据
 * @param maxTokens 默认最大输出 token
 */
public record DeepSeekAdapterOptions(
        String baseUrl,
        String apiKey,
        int maxTokens
) {
    public DeepSeekAdapterOptions {
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(apiKey, "apiKey");
    }

    /** 解析请求未携带凭据时使用的兜底 key。 */
    public String resolveApiKey() {
        return apiKey;
    }
}
