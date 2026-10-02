package io.github.xianreallyhotzzh.dsh.domain.model.entity;

/**
 * 消息或注入内容的来源（密封层级）。
 */
public sealed interface MessageSource permits
        MessageSource.UserSource,
        MessageSource.PluginSource,
        MessageSource.ModelMessageSource,
        MessageSource.ToolMessageSource {

    /** 由人类用户产生的消息。 */
    record UserSource() implements MessageSource {}

    /** 由插件产生的消息。 */
    record PluginSource(String plugin) implements MessageSource {
        public PluginSource {
            java.util.Objects.requireNonNull(plugin, "plugin");
        }
    }

    /** 由模型产生的消息。 */
    record ModelMessageSource(String provider, String model) implements MessageSource {
        public ModelMessageSource {
            java.util.Objects.requireNonNull(provider, "provider");
            java.util.Objects.requireNonNull(model, "model");
        }
    }

    /** 携带工具结果的消息（L06 工具内核起使用）。 */
    record ToolMessageSource(String callId) implements MessageSource {
        public ToolMessageSource {
            java.util.Objects.requireNonNull(callId, "callId");
        }
    }
}
