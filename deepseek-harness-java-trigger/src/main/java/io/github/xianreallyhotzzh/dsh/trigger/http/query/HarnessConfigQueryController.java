package io.github.xianreallyhotzzh.dsh.trigger.http.query;

import io.github.xianreallyhotzzh.dsh.api.IHarnessConfigQueryApi;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生效配置诊断端点：GET /api/harness/config/effective。
 *
 * <p>调用流程：HTTP/Web 层 → API 适配器 → 应用门面。</p>
 * <p>示例：控制器只做协议转换，把请求转交给 IHarnessConfigQueryApi。</p>
 */
@RestController
@RequestMapping("/api/harness/config")
public class HarnessConfigQueryController {

    private final IHarnessConfigQueryApi configQueryApi;

    public HarnessConfigQueryController(IHarnessConfigQueryApi configQueryApi) {
        this.configQueryApi = configQueryApi;
    }

    @GetMapping("/effective")
    public Response<Map<String, Object>> effectiveConfig() {
        return configQueryApi.effectiveConfig();
    }
}
