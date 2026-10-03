package io.github.xianreallyhotzzh.dsh.domain.model.valobj;

/**
 * 所有内容块类型的密封接口联合。
 *
 * <p>L04 增补 ToolResultBlock（事件折叠与 ToolResultPayload 需要）；ToolCallBlock
 * 随 L06 工具内核加入，ImageBlock 随 L07 多模态附件加入（与 vendor 的最终
 * permits 集合对齐是后续课的对拍点）。</p>
 */
public sealed interface ContentBlock permits
        TextBlock, ReasoningBlock, ToolResultBlock {
}
