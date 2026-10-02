package io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj;

/**
 * Harness 自有的 LLM 错误码。
 * <p>
 * 稳定编码，跨重试策略、适配器失败与诊断使用。
 */
public final class LlmErrorCodes {
    private LlmErrorCodes() {}

    public static final String EMPTY_RESPONSE = "EMPTY_RESPONSE";
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String CONTEXT_WINDOW_EXCEEDED = "CONTEXT_WINDOW_EXCEEDED";
    public static final String RATE_LIMIT = "RATE_LIMIT";
    public static final String SERVER = "SERVER";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String TRANSPORT = "TRANSPORT";
    public static final String AUTH = "AUTH";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String MALFORMED_RESPONSE = "MALFORMED_RESPONSE";
    public static final String STREAM_CLOSED = "STREAM_CLOSED";
    public static final String ABORTED = "ABORTED";
    public static final String UNKNOWN = "UNKNOWN";

    /** 普通重试策略默认可重试的错误码（重试机制随 L11 模型渠道接入）。 */
    public static final java.util.List<String> DEFAULT_RETRYABLE_CODES = java.util.List.of(
            EMPTY_RESPONSE, RATE_LIMIT, SERVER, TIMEOUT, TRANSPORT
    );
}
