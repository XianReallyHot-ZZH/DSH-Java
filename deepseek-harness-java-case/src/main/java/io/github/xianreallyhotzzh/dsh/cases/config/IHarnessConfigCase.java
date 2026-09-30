package io.github.xianreallyhotzzh.dsh.cases.config;

import java.util.Map;

/**
 * 配置查询用例（Web 控制台与运维诊断）。
 */
public interface IHarnessConfigCase {

    /**
     * 返回脱敏后的生效配置树。
     *
     * @return 配置树
     */
    Map<String, Object> effectiveConfig();
}
