package io.github.xianreallyhotzzh.dsh.domain.runtime.setting.adapter.port;

import java.util.Map;

/**
 * 读取生效态（部署视角）harness 配置的端口。
 *
 * <p>domain 只声明能力；把合并后的 Spring 环境投影成配置树、并完成脱敏的实现
 * 位于 infrastructure 的 adapter/config。</p>
 */
public interface IHarnessEffectiveConfigPort {

    /**
     * 返回脱敏后的生效配置树（根键为 harness 下的第一级，如 llm/auth/extensions）。
     *
     * @return 配置树；敏感值为 {@code [MASKED]}
     */
    Map<String, Object> effectiveConfig();
}
