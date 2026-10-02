# L03 - 阻塞式对话 tracer

**目标**：一条用户消息从 HTTP 入口打到 DeepSeek 上游再原路返回，打穿全部六个模块与六边形的三类边界（trigger→case→domain→infrastructure，api 契约隔离，types 值对象）。走完本课，`curl POST /api/agent/message` 能拿到真实模型的回复文本，端口-适配器在 LLM 上第一次真实运转。**本课起需要真实端点**（`LLM_BASE_URL` / `LLM_API_KEY` / `LLM_DEFAULT_MODEL`）。

**课程位置**：前置 L02（配置中心与 API 认证——harness.yml 的 llm 段自此有真实消费方）→ 本课 → 解锁 L04（会话事件与多轮：SessionLog 从消息版升级为事件版，策略树补 Intent 节点）。

## 概念与词汇

见 [CONTEXT.md](../../CONTEXT.md)：Agent / 驱动循环（Agent Run）/ 回合（Turn）/ 步（Step）/ LLM 端口 / 协议路由 / 策略树 / 收件箱（Inbox）/ 相位（Phase，本课只用 Idle/Running 两态）/ 统一响应信封（Response）。

## 配图：本课链路时序

对照 [研究报告 01 §3.1](../research/01-module-dependencies-and-request-lifecycle.md) 前 15 跳的 L03 子集：四个时间段（入口转发 → 策略树编排 → LLM 往返 → 收集与响应），三个注记分别钉住「驱动循环 + 阻塞消费」「空流降级 / 5xx 收敛」「归并落账 / 失败软化」。

[![L03 阻塞式对话 tracer 链路](assets/l03-tracer-sequence.svg)](assets/l03-tracer-sequence.html)

> 🔍 交互版 [assets/l03-tracer-sequence.html](assets/l03-tracer-sequence.html)：三段式导读（入口转发 / 策略树与驱动循环 / LLM 往返）逐段聚焦参与者，点消息可追踪回流路径（GitHub 网页端仅显示源码，请本地克隆中打开）。

## 精读路线（vendor 源码）

| 文件 | 导读问题 |
|------|---------|
| `api/dto/AgentMessageRequestDTO.java` + `AgentMessageResponseDTO.java` | 为什么校验写在 record 紧凑构造器里？这个时机发生在 Jackson 绑定期，异常会进我们的信封吗（见教学点 3）？ |
| `trigger/.../http/AgentController.java#sendMessage()` + `trigger/.../service/AgentApi.java#execute()` | Controller 为什么薄到一行？`IllegalArgumentException → 40000` 分支在什么场景才真正生效？ |
| `cases/agent/AgentUseCase.java` + `cases/orchestration/CasePipeline.java` | 用例薄到只做「取管线 + execute」时，价值落在哪一层？`execute` 的 finally 关闭上下文在防什么？ |
| `domain/.../run/ReactLoopAgent.java` 的 `send()/wakeDriver()/kick()/turn()/step()` | 994 行全量版里哪些属于本课子集、哪些是 L04-L08 的扩展？`step()` 里 CountDownLatch 等流结束的写法为什么是「阻塞式消费流式」的关键？ |
| `domain/.../run/AgentRunFactory.java` | 组合的最小集是什么？vendor 版的 12 参构造器各属于哪一课？ |
| `infrastructure/.../llm/InMemoryLlmRuntimePort.java` + `deepseek/DeepSeekAdapter.java#doStream()/doNonStreamFallback()` + `OpenAiCompatibleUriResolver` | 10s 首字节超时解决什么现实问题？「流结束但没收到 [DONE]」为什么按失败而不是空流处理？ |

## 实现增量

**做**：`AgentController`（仅 POST /message）→ `AgentApi`（信封收敛）→ `AgentUseCase` 薄门面 → 策略树编排四件套（`StrategyHandler`/`AbstractStrategyRouter`/`CasePipeline`/`CaseExecutionException`）+ `AgentMessageFactory` + 三节点最小实现（Resolve→Dispatch→Collect）；`ReactLoopAgent` 单回合单步版（send/kick/turn/step 骨架 + 最小 Inbox/SessionLog）+ `AgentRunFactory`；LLM 值对象（`StreamChunk` 六种/`GenerateOptions`/`FinishReason`/`LlmFailure`/`LlmErrorCodes`/`TokenUsage`）+ `LlmAdapter`/`ILlmRuntimePort` 端口 + `BlockAssembler`；`InMemoryLlmRuntimePort`（注册/路由/错误包装）+ `DeepSeekAdapter`（SSE 翻译 + 空流降级非流式 + 5xx 收敛）+ `OpenAiCompatibleUriResolver`；组合根第一批 `@Bean`（defaultAgentOptions / deepSeekAdapter / llmRuntimePort / agentRunFactory，消费 harness.yml llm 段）；types 模块第一批值对象（`Message`/`MessageSource`/`ContentBlock`/`TextBlock`/`ReasoningBlock`/`ToolSchema` 占位）。

**不做**：多轮/事件日志/Inbox 完整语义/Agent 查询取消（L04）、SSE 与流式 sink（L05）、工具与 tool_calls 解析（L06）、图片多模态（L07）、系统提示词组装/压缩/续写（L08）、重试策略与模型发现（L11）、审批（L13）。

**与 vendor 的差异清单（L03 新增）**：

| 差异 | 理由 | 收敛课 |
|------|------|--------|
| case 节点与工厂用构造器注入（vendor 为字段 `@Autowired` + 冗余 `@Qualifier`） | 复刻件要求 tracer 管线可脱离 Spring 单测（`AgentMessageFlowTest` 全手工装配）；vendor 靠只测静态纯函数绕开了注入问题 | 不收敛（有意增补；类型唯一后 Qualifier 本就多余） |
| `ReactLoopAgent` 驱动线程池为静态守护线程池（vendor 每次 kick `new` 一个非守护 cachedThreadPool 且从不关闭） | 非守护线程会挂在测试与关机路径上 | 不收敛（修正性差异） |
| `InMemoryLlmRuntimePort` 惰性桥接：首个下游订阅者到位后才订阅适配器（vendor 在 `stream()` 调用时即刻订阅） | `SubmissionPublisher` 在无订阅者时 `submit` 直接丢弃——vendor 的即刻订阅存在「适配器先发布、调用方后订阅」的丢分片竞态（本课同步 fake 测试将其暴露，见教学点 4） | 保留修正，不回归 vendor 形态 |
| `LlmAdapter`/`ILlmRuntimePort` 最小接口面（无 providerInfo/listModels/resolveModel/retryPolicy/fallback 工厂） | 这些能力的消费方在 L11 模型渠道 | L11 |
| 适配器不持有模型目录、不做上游发现（`DirectSeekCatalogModel` 未搬运） | 请求模型恒由 AgentOptions（harness.yml 默认渠道）给定 | L11 |
| `AgentOptions` 无 `approvalMode` 与热更新 | 审批门禁在 L13 | L13 |
| `SessionLog` 为消息版（append/messages/deriveMessages） | 事件版（sealed SessionEvent + WAL）是 L04 的交付物，类名路径与 vendor 对齐以便原位升级 | L04 |
| `AgentResolveNode` 的选项解析为「请求 → harness.yml 默认档」两跳 | vendor 的 `AgentModelSettingResolver` 还含数据库默认档 | L09 |
| `ContentBlock` permits 仅 Text/Reasoning；`StreamChunk` permits 六种（无 ToolCallDelta/Retry） | 密封集合随消费课扩员，L06/L11/L07 分别补齐 | L06/L07/L11 |

**搬运与自建测试**：搬运 `OpenAiCompatibleUriResolverTest`（4 例）与 `CasePipelineTest`（3 例，改包名零改动）；自建 `CaseArchitectureTest`（case 不依赖 trigger/infrastructure/app，对齐 vendor 同名测试与 api/domain/trigger 的既有规则）、`ReactLoopAgentTest`（3 例：单回合落账/失败软化/运行中唤醒不并发）、`AgentMessageFlowTest`（2 例：tracer 全链 + 会话复用、上游失败不炸链）、`InMemoryLlmRuntimePortTest`（3 例：路由透传/未知 provider/适配器崩溃收敛）、`DeepSeekAdapterTest`（3 例：正常 SSE / 上游 5xx / 空流降级——工单钉住的三条，JDK 内置 HttpServer 脚本化端点）、`AgentApiTest`（3 例：00000/40000/50000 信封收敛）。

## 验收清单

- [x] `mvn clean verify` 绿（api 1 / domain 8 / case 6 / infrastructure 10 / trigger 12，共 37 例）
- [x] 真端点 `curl POST /api/agent/message` 返回模型文本，信封 `00000`；`status=idle`、`messages` 为 user/assistant 两条（reasoning 折叠为 `<think>` 前缀）、`artifacts=[]`、`error=null`
- [x] 同 `agentId` 第二次请求复用同一 `sessionId`，`messages` 增至 4 条（内存态会话延续，完整多轮语义见 L04）
- [x] 上游异常收敛不裸抛：脚本化 fake 钉三层——适配器把 5xx 收敛为 `FinishReason.Error(SERVER)`（`DeepSeekAdapterTest`）、领域把上游失败软化为「⚠️ LLM 调用失败」助手消息且信封仍 00000（`AgentMessageFlowTest`）、跨用例的 `RuntimeException` 才折为 50000（`AgentApiTest`）。**工单此条的措辞按 vendor 行为重释**：LLM 上游失败是数据（软化进消息内容，见教学点 3），`50000` 只保留给跨用例边界的 harness 自身故障——这是对拍结论而非偏离
- [x] 请求/响应 DTO 字段集与 vendor 逐一对齐（见下表）

**对拍点：DTO 字段集（与 vendor 完全一致）**

`AgentMessageRequestDTO`（9 字段）：`agentId`* / `channelCode` / `maxTokens` / `cwd` / `message`* / `images` / `approvalMode` / `reasoningEffort` / `sandboxRoots`（*紧凑构造器校验非空）

`AgentMessageResponseDTO`（6 字段）：`agentId` / `sessionId` / `status` / `messages`(role+content) / `artifacts` / `error`

## 踩坑与教学点

1. **tracer bullet 的意义**：本课交付的不是「一个聊天功能」而是「一条被验证过的架构通路」。此后每一课（事件、SSE、工具、压缩）都在这条通路上换零件，而不是另起炉灶——这也是为什么单回合单步也要保留 `send/kick/turn/step` 四层骨架：它们是后续所有扩展的挂点，砍掉任何一层，L04 起就要改调用方。
2. **端口-适配器第一次真实运用**：domain 只认 `ILlmRuntimePort`，不知道 DeepSeek、不知道 HTTP、不知道 SSE。换供应商 = 组合根里换一个 `LlmAdapter` Bean，六层中五层零改动。这是六边形架构「依赖指向圆心」的第一次可运行证明。
3. **LLM 上游失败是数据，不是错误**：上游 5xx、断流、Key 无效都发生在「产品正常运转」的语境里——vendor 的设计（本课如实复刻）是把它们软化为一条可见的助手错误消息，信封保持 00000；50000 只保留给 harness 自身的装配/内部故障。同理，DTO 紧凑构造器的校验发生在 Jackson 绑定期（AgentApi 之前），`message` 为空时返回的是 Spring 默认的 400 错误体而非 40000 信封——vendor 行为一致，`AgentApi.execute` 的 40000 分支真正服务的是用例内抛出的 `IllegalArgumentException`。
4. **`SubmissionPublisher` 无订阅者即丢弃**：端口在 `stream()` 时立刻桥接适配器，若适配器在调用方订阅前就同步发布（本课的 fake 测试正是这种），分片会被静默丢光——错误信号是「收到 onComplete 但 0 个分片」。vendor 未暴露纯因其实适配器都从工作线程发布（仍有理论竞态）。修复：惰性桥接，第一个订阅者到位后才订阅适配器。教训：Flow 的「冷发布器」语义要靠发布方自律，包装层不能假设调用方一定先订阅。
5. **阻塞式消费流式**：`step()` 用 CountDownLatch 等流结束、`BlockAssembler` 边到边并，拿到完整回合后才返回——HTTP 调用方看到的是一次同步往返。这是刻意的中间形态：L05 的 SSE 直通（streamDeltaSink）只需把「并进 assembler」的同时「抄送一份给 sink」，骨架不用动。
6. **空流的两副面孔**：上游「接受 stream=true 但从不吐数据」有两种结局——10s 首字节超时 / 只回注释行后 EOF，都判定为空流并降级非流式（`doNonStreamFallback` 再发一次 `stream:false`）；但「吐过数据后没有 [DONE] 就断开」是协议违规，按 `STREAM_CLOSED` 失败处理。前者是兼容性（某些网关不支持流式），后者是正确性（内容可能不完整，续用会静默截断）。
7. **守护线程池**：vendor 的驱动循环每次 kick 都 `new` 一个非守护 cachedThreadPool 且从不 shutdown——线程泄露且阻碍 JVM 退出。复刻件改为单例守护线程池（修正性差异）；L13 运行时治理课会重新审视线程生命周期。
8. **配置旁路是保真带来的债**：`harness.agent.temperature` 与 `harness.llm.request-timeout-seconds` 两个开关经 `System.getProperty`/`getenv` 直读（vendor 同款原样搬运），绕过了 L02 建立的 harness.yml → 组合根配置链。这是 vendor 自身的债被复刻件如实继承——L11 模型渠道课重构适配器配置时应顺手收敛为 `@Value` 注入。同类的还有 vendor 陈旧注释原样保留（如 `CaseExecutionException` 自称「受检异常边界」实为 RuntimeException、Collect 的「HTML details 折叠」实为 `<think>` 标签）：保真优先，勘误记在这里。

## 自测题

1. 同一 `agentId` 发第二条消息，链路里哪一步决定了「会话延续」？`AgentRunFactory.liveAgents` 命中与未命中分别走什么路径？
2. 上游返回 500 时，从 `DeepSeekAdapter` 到 HTTP 响应体，错误信息经过了哪几种形态（异常 → ? → ? → JSON 字段）？哪一层保证它不会变成裸 500？
3. 为什么 `generateOptions.messages()` 里的历史在第二次请求时是 3 条而不是 4 条（提示：助手消息何时落账）？
4. `InMemoryLlmRuntimePort.stream()` 若按 vendor 一样「即刻订阅适配器」，本课哪个测试会红？根因是 Flow 的哪条语义？
5. 把 `translateSse` 的首字节等待从 10s 改成 0，哪些真实场景会被误判？「收到过数据但没有 [DONE]」为什么不能也走降级？
