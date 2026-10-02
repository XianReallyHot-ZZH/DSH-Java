package io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj;

/**
 * 一次模型调用失败的稳定描述。
 *
 * @param message 面向人的失败说明
 * @param code {@link LlmErrorCodes} 稳定错误码
 * @param status 上游 HTTP 状态码（可空）
 * @param providerRetryAfterMs 上游 Retry-After 指示的等待毫秒（可空）
 * @param requestId 上游请求 ID（可空）
 */
public record LlmFailure(
        String message,
        String code,
        Integer status,
        Long providerRetryAfterMs,
        String requestId
) {
    public LlmFailure {
        java.util.Objects.requireNonNull(message, "message");
        java.util.Objects.requireNonNull(code, "code");
    }

    public LlmFailure(String message, String code) {
        this(message, code, null, null, null);
    }
}
