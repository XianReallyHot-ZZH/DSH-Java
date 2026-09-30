# DSH-Java 逐课复刻 · 课程大纲

> 在本仓库根以累积式工程复刻 `vendors/deepseek-harness-java`（基准 `ff2d0a5`，只读）。
> 走完全部 26 课，仓库根即完整复刻件。术语见根目录 [CONTEXT.md](../CONTEXT.md)；包名决策见 [ADR-0001](../adr/0001-own-package-prefix.md)；研究报告见 [docs/research/](../research/)。

## 课程约定（对每课生效）

- **包名映射**：`cn.xiaofuge.deepseek.harness.*` → `io.github.xianreallyhotzzh.dsh.*`，仅前缀替换，包结构/类名/artifactId 与 vendor 一致；groupId 用 `io.github.xianreallyhotzzh`。
- **工程形态**：仓库根即 Maven 工程根（根 `pom.xml` + 七模块 + plugins），`docs/`、`vendors/` 与工程共存。
- **每课产出**：`docs/lessons/L<NN>-<slug>.md` 教学文档（模板见下）+ 代码增量 + 搬运测试 + 契约对拍记录；结束打 tag `L<NN>`，lesson 文档引用 tag 间 diff。
- **完成定义（DoD）分层**：所有课 = 编译启动 ✓ + 新搬测试与全量回归绿 ✓；标注 **[对拍]** 的课另需契约级逐项对齐（REST 路由与响应信封 / SSE 帧序列 / schema.sql / 会话事件形态 / JSON-RPC）。
- **验收环境**：手工验收用真实 DeepSeek 端点（环境变量 `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_DEFAULT_MODEL`，L03 首次需要）；自动化测试一律用脚本化 fake，不外呼。
- **以代码为准**：vendor README 与代码不一致处（如审批 gate「默认未启用」的说法已过时，实为默认启用）照代码复刻，并在 lesson 文档标注为教学点。

## lesson 文档模板（26 课统一）

```md
# L<NN> - <课名>
目标（一句话 + 走完能做什么）
在课程地图中的位置（前置课 → 本课 → 解锁什么）
概念与词汇（链接 CONTEXT.md 条目）
配图（mermaid：本课核心架构 / 链路 / 状态机）
精读路线（vendor 源码文件清单 + 导读问题）
实现增量（本课做什么 / 明确不做什么）
验收清单（编译 / 搬运测试 / 契约对拍点）
踩坑与教学点
自测题（3–5 问）
```

## 课序总览

课序即依赖：L<NN> 阻塞 L<NN+1>（课程线性化后的 blocking edges，工单据此展开）。

```mermaid
flowchart LR
    L01-->L02-->L03-->L04-->L05-->L06-->L07-->L08-->L09-->L10-->L11-->L12-->L13
    L13-->L14-->L15-->L16-->L17-->L18-->L19-->L20-->L21-->L22-->L23-->L24-->L25-->L26
```

| 课 | 名称 | 覆盖切片 | 增量约 |
|----|------|---------|--------|
| L01 | 七模块骨架与最小启动 | S01 | 500 |
| L02 | 配置中心与 API 认证 | S02 | 800 |
| L03 | 阻塞式对话 tracer | S03 | 2000 |
| L04 | 会话事件与多轮对话 | S04 | 1800 |
| L05 | SSE 流式与最小对话页 [对拍] | S05 + S19a | 600+前端 |
| L06 | 工具系统内核 | S06 | 2500 |
| L07 | 工具矩阵扩展与守卫 | S07 | 1500 |
| L08 | 意图识别与上下文工程 | S08 | 900 |
| L09 | 持久化核心：事件落库 [对拍] | S09a | 1800 |
| L10 | 回放、恢复与历史面板 | S09b + S19b | 1700 |
| L11 | 模型渠道与协议路由 [对拍] | S10 + S19c | 2500 |
| L12 | 任务提交与审批链路 | S11 + S19d | 2500 |
| L13 | 运行时治理：审批门+Hook+沙箱 [对拍] | S12 | 1300 |
| L14 | 插件机制核心（types SPI+加载器） | S13a | 2000 |
| L15 | 插件管理面与预装对账 | S13b + S19e | 1500 |
| L16 | 插件工程化 | S14 | 1400 |
| L17 | Node Bridge 通道 | S15 | 800 |
| L18 | MCP 工具适配 | S16 + S19f | 1300 |
| L19 | Skills 系统 | S17 | 900 |
| L20 | 工作流与目标 | S18 | 1200 |
| L21 | Gateway SSE、Token 计量与会话 v3 [对拍] | S20 | 1100 |
| L22 | A2A 协议与 Agent Card [对拍] | S21 | 800 |
| L23 | 数字人目录 | S22a | 1300 |
| L24 | 协作房间 | S22b | 1700 |
| L25 | Subagent 与边缘域工具 | S23 | 2000 |
| L26 | 收尾：测试基线、性能与部署 | S24 + S19 终态 | 1000+脚本 |

合计约 4.1 万行 Java + 0.53 万行前端（与报告 02 的实测总量吻合）。

---

## 每课目标与验收标准

### L01 七模块骨架与最小启动（S01）
- **目标**：立起与 vendor 同构的 Maven 七模块骨架（api/types/domain/case/infrastructure/trigger/app）与依赖方向（App→Trigger→API/Case→Domain←Infrastructure，Domain→Types），`Response` 信封就位，应用可启动。
- **交付**：根 `pom.xml` + 各模块 pom；`Application`；`Response`；ArchUnit 架构测试首建（守依赖方向）。
- **验收**：`mvn clean verify` 绿；`java -jar` 启动出 banner；`GET /` 占位页；架构测试能抓住「case 直接依赖 trigger」这类违规（用故意违规样例证明）。

### L02 配置中心与 API 认证（S02）
- **目标**：`harness.yml` 统一配置模型生效，配置可脱敏查询，API Key 认证可开可关。
- **交付**：`application.yml` + `harness.yml`；`HarnessExtensionsProperties` + 校验器；`GET /api/harness/config/effective`；`ApiKeyAuthInterceptor` + `WebMvcConfig`（CORS）。
- **验收**：启动无配置错误；effective 返回 `00000` 且 apiKey 掩码；配置 api-keys 后无头请求 401，未配置全放行。

### L03 阻塞式对话 tracer（S03）
- **目标**：打通一条消息从 HTTP 到 LLM 再回来的端到端竖切（单轮、无工具、内存态会话），六边形端口-适配器真正跑起来。**本课起需要真实 DeepSeek 端点。**
- **交付**：`AgentController` → `AgentApi(IAgentApi)` → `AgentUseCase` 薄用例 → `ReactLoopAgent`（先单 turn 单步子集）→ `InMemoryLlmRuntimePort` → `DeepSeekAdapter`（HTTP SSE 内部消费 + 非流式兜底）。
- **验收**：`curl POST /api/agent/message` 返回模型文本，信封 `00000`；脚本化 fake 端点的单测绿；行为对齐点——请求/响应 DTO 字段集与 vendor 一致。

### L04 会话事件与多轮对话（S04）
- **目标**：同一 agentId 连续多轮上下文延续；会话事件成为一切后续功能的数据源；Agent 可查询可取消。
- **交付**：`SessionEvent` sealed 事件集 + `SessionLog`；`Phase` 三态；`ReactLoopAgent.cancel/status/whenIdle`；`AgentResolveNode`（复用/互斥）；策略树四节点骨架就位（Intent/Dispatch 本课最小实现）；工作区 CRUD。
- **验收**：第二轮能引用第一轮内容；cancel 后 status 空闲；`GET /api/agent/workspaces` 返回 `00000`；事件种类集合与 vendor 的 sealed permits 一致。

### L05 SSE 流式与最小对话页（S05 + S19a）**[对拍]**
- **目标**：逐 token 推送成为产品的核心体验；Web 控制台最小版可对话。
- **交付**：`AgentStreamApi`（SseEmitter、60ms 增量批、15s 心跳）；4+1 个 sink 经上下文挂载到 Agent volatile 字段；SSE 帧协议八种事件；`index.html + app.js` 最小对话页（消费 meta/chunk/finish/done）。
- **对拍与验收**：`curl -N POST /api/agent/stream` 观察 `meta → chunk* → finish → done`（有工具时后续课补 step_break/tool_result）；**帧名与载荷字段逐项与 vendor 对拍**；断连不泄漏线程；前端实时渲染。

### L06 工具系统内核（S06）
- **目标**：模型可发起工具调用并拿到结果——ReAct 循环的价值所在，也是插件/MCP/Skill 的挂载点。
- **交付**：`ToolRegistry`/`InMemory`/`Composite`；`ToolCallExecutor` 全链（Jackson 参数解析→Hook 占位→审批占位→并行/串行调度 orTimeout→按派发顺序提交）；types 的 `AbstractTool/ToolDefinition/ToolExecutionResult`；首批工具 fs_read/fs_write/fs_search/shell_execute + `LocalShellExecutor` + `CwdResolvingFsPort`；`AgentToolCatalog` 雏形。
- **验收**：「读某文件并总结」事件流出现 ToolCall/ToolResult 对（callId 配对有序）与 SSE `step_break`；工具超时按 TOOL_TIMEOUT 失败不挂死回合；错误作为工具结果回灌。

### L07 工具矩阵扩展与守卫（S07）
- **目标**：补齐文件编辑、联网、用户交互能力；危险命令有守卫。
- **交付**：fs_grep/fs_edit/fs_read_image/str_replace_editor；web_search/web_fetch；`AskUserTool` + RuntimeQuestion REST 闭环（对话暂停→回答→继续）；`DangerousCommandGuard`；guard 域（timeout/reminder）。
- **验收**：完成「改文件字符串」「搜网页并总结」；ask_user 闭环按序继续；`rm -rf /` 类命令被拦截提醒。

### L08 意图识别与上下文工程（S08）
- **目标**：闲聊不空转工具；长会话不爆窗口；截断回复自动续写。
- **交付**：`AgentIntentNode` 8 正则分类 + `[no-tools]`；`SystemPromptAssembler`/`ScopedSystemPromptAssembler`；`BasicCompactionEngine`（阈值+最少消息数）；max_tokens 受控续写（≤4 次）。
- **验收**：`AgentIntentNodeTest`（搬运）绿；构造超阈值会话观察压缩摘要事件；截断回复续写拼接完整；128K 预算裁剪生效。

### L09 持久化核心：事件落库（S09a）**[对拍]**
- **目标**：重启后事件与会话不丢——事件溯源闭环。
- **交付**：schema.sql 会话相关表（harness_session/harness_session_event/harness_session_header/harness_session_event_log）；MyBatis dao + repository；`JdbcSessionEventStore`；`PersistingSessionLog` 异步镜像（不阻塞流式收尾）；standalone H2 profile；`AgentResolveNode.findPersistedSession()` 重启恢复。
- **对拍与验收**：**建表 DDL 逐列与 vendor schema.sql 对应表对拍**；standalone 启动自动建表幂等；重启后历史事件完整、恢复会话继续对话。

### L10 回放、恢复与历史面板（S09b + S19b）
- **目标**：历史可查、可回放、可恢复；控制台出现历史面板。
- **交付**：`ConversationQueryCase`（sessions/messages）；`SessionRestoreCase` + REST；`SessionRebuilderService`/`SurfaceProjector`/`InMemorySessionProjectionCache`；`SessionWriteLeaseService`；前端历史会话列表与消息回放面板。
- **验收**：`GET /api/harness/console/sessions` 列历史；restore 后继续对话；投影按 callId 正确配对工具调用；三件套测试（搬运）绿。

### L11 模型渠道与协议路由（S10 + S19c）**[对拍]**
- **目标**：控制台/API 可管理多渠道并切换；支持 openai/anthropic/ollama 协议与上游模型发现。
- **交付**：harness_model_setting 表 + dao/repository；渠道 CRUD/active/delete 的 case 策略树（runtime/node 七节点）；`ProtocolRoutingAdapter` + `LlmAdapterFactory` + `AnthropicAdapter`/`OpenAiCompatibleAdapter`；URI 归一；discover（/models 与 /api/tags）；`ModelSyncRunner`；三级优先级解析（请求>DB>yml）；前端模型面板。
- **对拍与验收**：**渠道 REST 契约对拍**；保存渠道→激活→对话走新渠道；discover 拉到上游模型列表；`AgentModelSettingResolverTest`、`ModelSettingServiceTest`（搬运）绿。

### L12 任务提交与审批链路（S11 + S19d）
- **目标**：高风险任务先审后执行，任务状态机走到终态。
- **交付**：五节点提交链（SubmissionRoot→ProfileResolution→PermissionCheck→ToolResolution→Enqueue）；`PermissionPolicyService`+权限矩阵；`ApprovalPolicyService`/`ApprovalCommandService`；任务状态机与 `HarnessExecutionService`；提交过滤链三件；审批 REST；前端审批面板。
- **验收**：提交含 shell_execute 的任务→pending 可见→approve→QUEUED→RUNNING→COMPLETED；对 PENDING_APPROVAL 直接 executeSession 抛异常；`ApprovalCommandServiceTest`、`PermissionPolicyServiceTest`（搬运）绿。

### L13 运行时治理：审批门+Hook+沙箱（S12）**[对拍]**
- **目标**：对话链路中的高风险工具被运行期拦截等待裁决；Hook 与沙箱生效。**教学点：审批门默认启用，README §14 已过时（以 AgentRunBeanConfig 13 参构造为准）。**
- **交付**：`RuntimeApprovalBroker`（阻塞等待+超时 DENY）；`MatrixRuntimeApprovalGate`（空清单放行）；`AggregatingRuntimeApprovalGateway`（@Primary）；`ApprovalAwareShellExecutor`/`ApprovalAwareFsPort`；`ApprovalModeVO` 三档；Hook 引擎（`CompositeHookService`/`InProcessPluginHookRegistry`/`LocalShellHookRunner`，阻止类优先合并）；沙箱三档 + `LocalSandboxEnforcer`/`SandboxedShellExecutor`/`SandboxedFsService` + `SandboxExtraRootsRegistry`；RuntimeApproval REST。
- **对拍与验收**：`requires_approval` 事件与 runtime approval REST 契约对拍；默认配置下 shell_execute 被拦截、`resolve` 放行后继续、超时 DENY；required-tools 清空后放行；沙箱 WRITE 档越界写被拒；`ApprovalAware*Test`（搬运）绿。

### L14 插件机制核心（S13a）
- **目标**：外部 JAR 插件经隔离类加载器进宿主，工具以 `plugin__<id>__<name>` 进表，卸载干净——插件系统的灵魂。
- **交付**：types 全套 SPI（`JavaHarnessPlugin`/`AbstractHarnessPlugin`/`PluginContext`/`PluginManifest`/`PluginLifecycleState`/`PluginEventBus`/`PluginHook`/`PluginConfigStore`）；`JavaPluginLoader`（URLClassLoader + plugin.yaml + ServiceLoader 回退）；`JavaPluginRuntimeManager`（start 九步/stop 逆序回滚）；`SpringPluginContext`（AutoCloseable 事务边界）；`InProcessToolBridge`；插件提示词段（order=150）；`sample-tools-plugin` 在本仓库从源码构建（绑定自有包名）。
- **验收**：启动预装 sample 插件后，对话可调 `plugin__sample-tools__weather_query`，系统提示词出现能力段；stop/uninstall 后工具、提示词、Hook 全部回收无悬挂；`SpringPluginContextTest`（搬运）绿。

### L15 插件管理面与预装对账（S13b + S19e）
- **目标**：插件全生命周期 REST 化；登记状态与运行时状态双轨对账。
- **交付**：install/activate/run/disable/enable/uninstall REST；`PluginRuntimePipelineFactory` 五节点（Resolve→InspectBridge→BindBridge→分叉 Activate/Run）；三张插件表 + dao/repository；`PluginToolBridgeService`（进程内桥落表）；`PresetPluginLoader`（预装+启动对账复活）；前端插件面板。
- **对拍与验收**：插件 REST 契约对拍；REST 全链可用；重启后 ACTIVE 插件经对账自动复活；面板可见状态。

### L16 插件工程化（S14）
- **目标**：拖 JAR 即可解析候选；配置持久化；库存可查；预置自启；支持脚手架。
- **交付**：`PluginArtifactAnalyzerService`（analyze-jar/analyze-maven）+ REST；`DatabasePluginConfigStore` + 配置 REST；inventory/status 查询；`PluginHotReloader`（WatchService）；archetype 脚手架模块。
- **验收**：analyze 传 JAR 路径返回候选 DTO；插件配置重启不丢；inventory 含元数据；改 JAR 触发热重载；`DatabasePluginConfigStoreTest`（搬运）绿。

### L17 Node Bridge 通道（S15）
- **目标**：Node.js 插件作为 sidecar 进程经 JSON-RPC 桥接。
- **交付**：`JsonRpcPluginToolBridge`（initialize/tools/list/tools/call + initialized 通知 + 60s 超时）；`PluginSidecarGatewayService`（nodeBridgeEnabled、installRoot 边界、入口白名单 .js/.mjs/.cjs、stderr 落日志）；目录树安装；demo 插件源码。**教学点：demo 插件不实现握手，会超时失败并走 stop 回滚——如实复刻这条失败路径。**
- **验收**：Node 在场时生命周期编排跑通（inspect→bind→start）；握手超时后子进程被回收、状态 FAILED、可重新 activate；Node 不在场时 FAILED。

### L18 MCP 工具适配（S16 + S19f）
- **目标**：配置的 MCP Server 启动自动连接，工具以 `mcp__<server>__<tool>` 进共享表；控制台可管。
- **交付**：`McpBootstrap`/`McpServerConfig`/`McpToolAdapter`/`IMcpClient`；`StdioMcpClient` + `HttpMcpClient`（SSE/streamable-http）；servers CRUD/test REST；前端扩展面板（MCP 区）。
- **验收**：配置 SSE server 启动后显示 connected；对话可调 `mcp__<server>__<tool>`；test 连通性端点工作；`McpServerConfigTest`、`StdioMcpClientTest`、`HttpMcpClientTest`（搬运）绿。

### L19 Skills 系统（S17）
- **目标**：纯 Markdown 技能被发现、按需加载、可管理——「轻扩展」对照 L14「重扩展」。
- **交付**：`FilesystemSkillProviderPort`（目录束/扁平两种形态、frontmatter 解析、四根 rank 优先级）；`SkillService`（rank 低者胜合并）；`SkillTool`（正文包 `<skill>` 进工具结果）；`extension_skill_list/install/manage` 工具；git/zip 安装 + 停用名单热生效；示例 skill。
- **验收**：放入 SKILL.md → 列表可见 → 对话中 skill 工具加载正文进上下文并按指引行动；停用后即时不出现；技能不进系统提示词；`FilesystemSkillProviderPortTest`（搬运）绿。

### L20 工作流与目标（S18）
- **目标**：多步工作流复用 Agent 链路独立启动；会话目标可查。
- **交付**：`WorkflowService`/`WorkflowStartPolicy`；`LocalWorkflowEnginePort`；`WorkflowPipelineFactory`（start/cancel）；`WorkflowStreamApi`（只推终态事件+心跳，如实复刻）；goal 域（`GoalAggregate`、REST、GoalTools）。
- **验收**：start 后 stream 收到事件帧、cancel 终止；`GET /api/harness/goals/{sessionId}` 返回目标视图；`WorkflowStartPolicyTest`（搬运）绿。

### L21 Gateway SSE、Token 计量与会话 v3（S20）**[对拍]**
- **目标**：Agent 与工作流统一事件信封出口；会话级 token 可查；JSONL 存储加固。
- **交付**：`GatewayStreamController`/`GatewayStreamApi`（按 requestType 分派 streamAgent/streamWorkflow）；`GatewayStreamEventDTO` 统一信封；`SessionTokenMeterService`；`JsonlSessionEventStore`（格式 v3 版本守卫）+ 写租约 + 投影缓存。
- **对拍与验收**：**Gateway 帧契约对拍**；`POST /api/gateway/stream` 收到统一信封；计量四类 token 可查；`JsonlSessionEventStoreTest`/`SessionWriteLeaseServiceTest`/`InMemorySessionProjectionCacheTest`/`SessionTokenMeterServiceTest`（搬运）绿。

### L22 A2A 协议与 Agent Card（S21）**[对拍]**
- **目标**：本服务成为标准 A2A Server，可被第三方发现与调用。
- **交付**：`A2AController`（message/send、message/stream、tasks/get、tasks/cancel；错误码 -32001/-32002）；`AgentCardController`（三 well-known 路径）；复用 IAgentApi 与 Gateway 流语义。
- **对拍与验收**：**JSON-RPC 方法与错误码契约对拍**；message/send 返回 kind=task 终态；tasks/get 未找到 -32001；Agent Card URL 指向 /a2a。

### L23 数字人目录（S22a）
- **目标**：远端 Agent 可被发现、登记、健康检查。
- **交付**：`DigitalHumanService`（CRUD、Agent Card 探测、健康检查、凭据不落库）；`digital_human`/`digital_human_endpoint` 表；`RemoteEndpointCredentialRegistry`；discover/health-check REST。
- **验收**：对远端（可用 vendor 实例或 mock card 端点）discover→登记→健康检查全链；`DigitalHumanServiceA2aDiscoveryTest`（搬运）绿。

### L24 协作房间（S22b）
- **目标**：多数字人协作房间：消息、任务编排、事件流。
- **交付**：`CollaborationService`（房间/参与者/消息/任务 cancel-resume-retry-reassign/事件 SSE）；`PlannerService`；`RemoteAgentGateway`（调远端 A2A）；远端审批聚合；五张协作表。
- **验收**：建房→邀请→发消息→事件流可见；任务失败改派/重试；`RemoteAgentGatewayTest`/`RemoteAgentGatewayA2aRunTest`（搬运）绿。

### L25 Subagent 与边缘域工具（S23）
- **目标**：Agent 可派生子代理；terminal/jobs/schedule/lsp 等条件工具就位。
- **交付**：`SubagentTool`/`SubagentRegistry`/`SubagentProvider`（进程内 spawn/fork + 外部 CLI claude-code/codex/acp）；Terminal×5 + `LocalTerminalPort`；Job×4；Schedule×3；`LspTool`；`SessionSearchTool`。**教学点：ScheduleService 无调度驱动方、仓储 InMemory——如实保留。**
- **验收**：配置 `harness.subagent.*` 后 subagent 可运行；terminal open/send/read/close/list 全链；jobs 后台 output/kill；各条件注册按配置开关。

### L26 收尾：测试基线、性能与部署（S24 + S19 终态）
- **目标**：复刻件达到 vendor 的交付形态；已知性能项记录在案。
- **交付**：全量测试对齐 v0.1.7 基线（约 107 用例）；ArchUnit 五件套齐；`ApplicationIntegrationSmokeTest`；Dockerfile + docker-compose；`package-local.sh` + start 脚本；前端终态打磨与全流程走查；已知性能项文档（deriveMessages O(n²)、token length/4 粗估、无界线程池）。
- **验收**：`mvn clean test` 0 失败；`docker compose up --build -d` 后冒烟端点 200；zip 分发 `./start.sh` 可起；控制台全流程手工走查（建工作区→对话→工具卡片→审批→历史）通过。

---

## 与 vendor 的对拍总口径（[对拍] 课通用）

| 契约面 | 对拍方法 |
|--------|---------|
| REST | 路由路径、HTTP 方法、请求/响应 DTO 字段集、`Response` 信封 code 语义逐一比对 vendor 源码（trigger + api/dto） |
| SSE 帧 | 事件名集合、载荷字段、顺序保证（meta 首发必先于 chunk 等）比对 `AgentStreamApi`/`GatewayStreamApi` |
| schema.sql | 表名/列名/类型逐列比对（对应课只比对所属表） |
| 会话事件 | sealed permits 集合与事件载荷形态比对 `SessionEvent` |
| JSON-RPC | 方法名、参数、错误码比对 `A2AController` |

## 环境与准备

- JDK 17+、Maven 3.6+；`vendors/` submodule 保持 `ff2d0a5`。
- **上游增量悬置（2026-09-30 记）**：上游已有 `v0.1.8` 分支领先 `main`/ff2d0a5 4 个提交（f9a2537，2026-09-26，`git diff origin/main...origin/v0.1.8`：附件上传 + 工作区文件树三个新 trigger 控制器、控制台前端迭代、全 pom bump 0.1.8、start-standalone.sh）；既有 api/schema/SSE 契约面无变化。课程**不换基线**；走完 L26 后将该 delta 作为附加补丁课处理（届时建 L27 工单，见 `docs/tickets/L26-finalize.md` 末节）。
- 真端点（L03 起）：`export LLM_BASE_URL=... LLM_API_KEY=... LLM_DEFAULT_MODEL=...`（建议 DeepSeek 官方 `https://api.deepseek.com/v1`，模型 `deepseek-chat`）；key 永不写入仓库。
- H2 standalone（L09 起）零外部依赖；MySQL profile 可选。
