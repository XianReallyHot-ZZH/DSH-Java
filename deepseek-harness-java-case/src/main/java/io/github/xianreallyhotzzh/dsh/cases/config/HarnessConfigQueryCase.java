package io.github.xianreallyhotzzh.dsh.cases.config;

import io.github.xianreallyhotzzh.dsh.domain.runtime.setting.adapter.port.IHarnessEffectiveConfigPort;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 读取生效 harness 配置的用例：面向 trigger 的诊断查询。
 *
 * <p>只读薄用例，无策略树——直接转发 domain 端口（脱敏已在 infrastructure 适配器完成）。</p>
 */
@Service
public class HarnessConfigQueryCase implements IHarnessConfigCase {

    private final IHarnessEffectiveConfigPort effectiveConfigPort;

    public HarnessConfigQueryCase(IHarnessEffectiveConfigPort effectiveConfigPort) {
        this.effectiveConfigPort = effectiveConfigPort;
    }

    @Override
    public Map<String, Object> effectiveConfig() {
        return effectiveConfigPort.effectiveConfig();
    }
}
