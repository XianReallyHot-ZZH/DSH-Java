package io.github.xianreallyhotzzh.dsh.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Agent 消息发送响应（字段集与 vendor 逐一对齐——L03 对拍点）。
 *
 * @param agentId Agent ID
 * @param sessionId 会话 ID
 * @param status Agent 状态，例如 idle、running
 * @param messages 会话消息（role/content 结构；工具消息随 L06 加入）
 * @param artifacts 本回合写文件类工具产出的文件绝对路径清单（L06 起为权威来源，本课恒空）
 * @param error 回合异常信息（驱动器线程死亡等，正常完成为 null；异常时仍携带已收集的部分消息）
 */
public record AgentMessageResponseDTO(
        String agentId,
        String sessionId,
        String status,
        List<Map<String, Object>> messages,
        List<String> artifacts,
        String error
) {
}
