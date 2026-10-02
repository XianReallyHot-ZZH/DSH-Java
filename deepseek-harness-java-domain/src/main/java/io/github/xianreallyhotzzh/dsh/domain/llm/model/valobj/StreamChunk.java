package io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj;

import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ContentBlock;

/**
 * 模型流式输出的归一化分片（密封层级）。
 * <p>
 * L03 子集交付六种：BlockStart / TextDelta / ReasoningDelta / BlockEnd / Usage / Finish；
 * ToolCallDelta 随 L06 工具内核加入，Retry 随 L11 重试机制加入。
 */
public sealed interface StreamChunk permits
        StreamChunk.BlockStart,
        StreamChunk.TextDelta,
        StreamChunk.ReasoningDelta,
        StreamChunk.BlockEnd,
        StreamChunk.Usage,
        StreamChunk.Finish {

    /** 一个内容块的开始。 */
    record BlockStart(int index, String blockType) implements StreamChunk {}

    /** 文本增量。 */
    record TextDelta(int index, String text) implements StreamChunk {}

    /** 推理增量。 */
    record ReasoningDelta(int index, String text) implements StreamChunk {}

    /** 一个内容块的结束，携带归并后的最终块。 */
    record BlockEnd(int index, ContentBlock block) implements StreamChunk {}

    /** 本次调用的 token 用量。 */
    record Usage(TokenUsage usage) implements StreamChunk {}

    /** 流的终态：模型为何停止。 */
    record Finish(FinishReason reason) implements StreamChunk {}
}
