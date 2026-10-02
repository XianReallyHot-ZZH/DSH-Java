package io.github.xianreallyhotzzh.dsh.api.dto;

/**
 * 向 Agent 发送消息的请求（字段集与 vendor 逐一对齐——L03 对拍点）。
 *
 * @param agentId Agent ID（必填）
 * @param channelCode 模型渠道；为空时使用当前激活渠道（渠道体系 L11 接入，本课走默认渠道）
 * @param maxTokens 最大输出 Token 数；为空时使用默认值
 * @param cwd 工作目录；为空时使用进程工作目录
 * @param message 用户消息文本（必填）
 * @param images 图片附件列表；每项为 data URL（data:image/png;base64,...）或裸 base64，
 *               可为 null/空。仅多模态模型支持（L07 接入）
 * @param approvalMode 运行审批模式：REQUEST_APPROVAL / AUTO_APPROVE / FULL_OPEN（L13 接入）
 * @param reasoningEffort 推理级别：low / medium / high；为空时使用模型默认值
 * @param sandboxRoots 用户授权的额外可写根（项目下挂载的工程目录绝对路径），
 *                     仅对 workspace-write 沙箱模式生效（L13 接入）；可为 null/空
 */
public record AgentMessageRequestDTO(
        String agentId,
        String channelCode,
        Integer maxTokens,
        String cwd,
        String message,
        java.util.List<String> images,
        String approvalMode,
        String reasoningEffort,
        java.util.List<String> sandboxRoots
) {
    public AgentMessageRequestDTO {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must be non-blank");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must be non-blank");
        }
    }
}
