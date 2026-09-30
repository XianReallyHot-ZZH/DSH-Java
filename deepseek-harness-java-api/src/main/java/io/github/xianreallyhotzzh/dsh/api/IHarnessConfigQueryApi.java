package io.github.xianreallyhotzzh.dsh.api;

import io.github.xianreallyhotzzh.dsh.api.response.Response;
import java.util.Map;

/**
 * 读取生效态 harness 配置的对外契约（诊断端点）。
 */
public interface IHarnessConfigQueryApi {

    /**
     * 返回脱敏后的生效配置树，统一信封包装。
     *
     * @return {@code 00000} + 配置树；内部错误为 {@code 50000}
     */
    Response<Map<String, Object>> effectiveConfig();
}
