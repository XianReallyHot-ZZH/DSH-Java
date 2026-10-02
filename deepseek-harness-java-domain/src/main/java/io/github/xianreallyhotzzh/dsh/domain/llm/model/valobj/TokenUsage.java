package io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj;

/**
 * 单次模型调用的 token 用量。
 *
 * @param inputTokens 输入 token（不含缓存命中的部分，由适配器扣除）
 * @param outputTokens 输出 token
 * @param cacheReadTokens 缓存命中读 token（可空）
 * @param cacheWriteTokens 缓存写 token（可空）
 * @param reasoningTokens 推理 token（可空）
 */
public record TokenUsage(
        int inputTokens,
        int outputTokens,
        Integer cacheReadTokens,
        Integer cacheWriteTokens,
        Integer reasoningTokens
) {
    public TokenUsage(int inputTokens, int outputTokens) {
        this(inputTokens, outputTokens, null, null, null);
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public TokenUsage plus(TokenUsage other) {
        if (other == null) {
            return this;
        }
        return new TokenUsage(
                inputTokens + other.inputTokens,
                outputTokens + other.outputTokens,
                addNullable(cacheReadTokens, other.cacheReadTokens),
                addNullable(cacheWriteTokens, other.cacheWriteTokens),
                addNullable(reasoningTokens, other.reasoningTokens)
        );
    }

    private static Integer addNullable(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0 : left) + (right == null ? 0 : right);
    }
}
