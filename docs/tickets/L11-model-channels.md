# L11 模型渠道与协议路由（S10 + S19c）[对拍]

- 前置：L10（tag L10）
- 大纲节：docs/lessons/README.md#L11
- 术语：LLM 端口 / 协议路由 / 模型渠道 / 渠道模板

## vendor 精读清单
- `harness_model_setting` 表（schema.sql L62）+ `dao/IHarnessModelSettingDao.java` + `adapter/repository/ModelSettingRepository.java`
- `cases/runtime/`：`ModelSettingCommandCaseImpl`、`ModelDiscoveryCaseImpl`、`ChannelPresetQueryCaseImpl` + `runtime/node/` 七节点
- `infrastructure/.../adapter/llm/ProtocolRoutingAdapter.java#delegate()`、`LlmAdapterFactory.java`（L65-75 协议分派）、`anthropic/AnthropicAdapter.java`、`openai/OpenAiCompatibleAdapter.java`
- `domain/.../channel/service/OpenAiCompatibleUriResolver`（非 /v1 Base URL 归一）
- `infrastructure/.../adapter/llm/UpstreamModelSyncService.java`（/models 与 /api/tags 自动选择）
- `cases/agent/node/AgentResolveNode.java#buildAgentOptions()`（L164-174 三级优先级）+ `AgentModelSettingResolver`
- `app/model/ModelSyncRunner.java`、`app/model/SchemaMigrationRunner.java`
- Controllers：HarnessModelSettingCommand/Query、HarnessChannelPreset、HarnessModelDiscovery、HarnessRuntimeModel——**REST 契约对拍基准**

## 实现增量
- 做：渠道表+CRUD/active/delete、渠道模板、协议路由（anthropic/openai 兼容适配器）、URI 归一、模型发现、启动同步 Runner、三级优先级接 Resolve、前端模型面板。
- 不做：Gateway（L21）；deepseek 适配器 L03 已有，此处纳入工厂体系。

## 测试搬运
- `AgentModelSettingResolverTest`、`ModelSettingServiceTest`、`OpenAiCompatibleUriResolverTest`、`InMemoryLlmRuntimePortTest`（改包名）。

## 验收（DoD 专项）
- [ ] **对拍**：渠道相关 REST 路由与 DTO 字段与 vendor 一致（记录进 lesson 文档）
- [ ] 保存渠道→激活→对话走新渠道；对 Anthropic 协议端点（可用兼容 mock）正常对话
- [ ] discover 从上游拉模型列表成功；三级优先级：请求显式 > DB > yml

## 教学文档与配图
- `docs/lessons/L11-model-channels.md`；mermaid：渠道配置→协议路由→三适配器分派图。

## 教学点
- 「协议」是渠道属性而非 provider 属性——DeepSeek 走 openai 协议；URI 归一让各种 Base URL 写法都能用。
