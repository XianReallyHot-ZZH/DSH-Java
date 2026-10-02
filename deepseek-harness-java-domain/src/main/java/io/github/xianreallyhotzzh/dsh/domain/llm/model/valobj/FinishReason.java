package io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj;

/**
 * 模型响应为何停止的密封层级；适配器把各供应商的停止原因映射进来。
 */
public sealed interface FinishReason permits
        FinishReason.Stop,
        FinishReason.ToolCalls,
        FinishReason.MaxTokens,
        FinishReason.Aborted,
        FinishReason.Error {

    /** 正常收尾。 */
    record Stop() implements FinishReason {}

    /** 模型请求了工具调用（执行循环随 L06 工具内核接入）。 */
    record ToolCalls() implements FinishReason {}

    /** 输出被 max_tokens 截断。 */
    record MaxTokens() implements FinishReason {}

    /** 被主动中止。 */
    record Aborted(LlmFailure failure) implements FinishReason {
        public Aborted {
            java.util.Objects.requireNonNull(failure, "failure");
        }
    }

    /** 调用失败。 */
    record Error(LlmFailure failure) implements FinishReason {
        public Error {
            java.util.Objects.requireNonNull(failure, "failure");
        }
    }

    default boolean isFailure() {
        return this instanceof Aborted || this instanceof Error;
    }
}
