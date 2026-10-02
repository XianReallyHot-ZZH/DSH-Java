package io.github.xianreallyhotzzh.dsh.domain.model.valobj;

import java.util.Map;

/**
 * 单个工具的 JSON Schema 描述（L03 仅作为 GenerateOptions 的占位字段存在，
 * 真实注册与消费随 L06 工具内核接入）。
 *
 * @param name 工具名（模型可见的 function name）
 * @param description 工具用途说明
 * @param parameters JSON Schema 形式的参数定义
 */
public record ToolSchema(
        String name,
        String description,
        Map<String, Object> parameters
) {
}
