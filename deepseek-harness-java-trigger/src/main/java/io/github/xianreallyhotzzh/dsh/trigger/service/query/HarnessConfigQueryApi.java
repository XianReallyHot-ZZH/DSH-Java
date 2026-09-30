package io.github.xianreallyhotzzh.dsh.trigger.service.query;

import io.github.xianreallyhotzzh.dsh.api.IHarnessConfigQueryApi;
import io.github.xianreallyhotzzh.dsh.api.response.Response;
import io.github.xianreallyhotzzh.dsh.cases.config.IHarnessConfigCase;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 生效配置查询的 Web 侧适配器（IHarnessConfigQueryApi 实现方）。
 *
 * <p>职责仅剩协议转换：把 case 的裸 Map 包进统一响应信封；内部错误折成 50000。</p>
 */
@Service
public class HarnessConfigQueryApi implements IHarnessConfigQueryApi {

    private final IHarnessConfigCase configCase;

    public HarnessConfigQueryApi(IHarnessConfigCase configCase) {
        this.configCase = configCase;
    }

    @Override
    public Response<Map<String, Object>> effectiveConfig() {
        try {
            return Response.success(configCase.effectiveConfig());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
