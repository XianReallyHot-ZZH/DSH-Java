# L02 配置中心与 API 认证（S02）

- 前置：L01（tag L01）
- 大纲节：docs/lessons/README.md#L02
- 术语：组合根 / trigger 层 / 统一响应信封

## vendor 精读清单
- `app/src/main/resources/application.yml`（spring.config.import 链）+ `harness.yml`
- `infrastructure/.../config/HarnessExtensionsProperties.java` + `HarnessConfigValidator.java`
- `infrastructure/.../adapter/config/HarnessEffectiveConfigPort.java`（脱敏逻辑）
- `cases/config/HarnessConfigQueryCase.java`
- `trigger/.../http/query/HarnessConfigQueryController.java`
- `trigger/.../filter/ApiKeyAuthInterceptor.java#preHandle()`（L32-98：空放行/401/白名单）+ `WebMvcConfig.java`（拦截器注册 + CORS 全开及其注释理由）

## 实现增量
- 做：application.yml + harness.yml + config.import；配置属性类与启动校验；effective 脱敏查询端到端；API Key 拦截器与 CORS。
- 不做：LLM/approval/extensions 配置的实际消费方（后续课各自接入）；Spring Security（vendor 明确没有）。

## 测试搬运
- `HarnessConfigValidatorTest`、`HarnessEffectiveConfigPortTest`（infrastructure，改包名）。

## 验收（DoD 专项）
- [ ] 启动无配置错误；`curl /api/harness/config/effective` 返回 `00000` 且敏感字段掩码
- [ ] 未配置 api-keys 全放行；配置后无头 401、`X-API-Key`/`Bearer` 均可过、静态资源与 OPTIONS 放行

## 教学文档与配图
- `docs/lessons/L02-config-auth.md`；mermaid：配置加载链（yml → @Value/@ConfigurationProperties 三级兜底）与拦截器链。

## 教学点
- 「空 keys 即本地开发模式」的产品取舍；CORS 全开与桌面 WebView 直连的关系。
