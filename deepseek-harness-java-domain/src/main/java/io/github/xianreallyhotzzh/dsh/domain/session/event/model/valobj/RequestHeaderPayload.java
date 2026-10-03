package io.github.xianreallyhotzzh.dsh.domain.session.event.model.valobj;

import java.util.List;
import java.util.Map;

/**
 * 请求头事件载荷：固化模型配置、适配器默认值、系统提示词与工具列表，供回放与对账。
 */
public record RequestHeaderPayload(Map<String, Object> config, Map<String, Object> adapterDefaults, String system, List<Map<String, Object>> tools) {}
