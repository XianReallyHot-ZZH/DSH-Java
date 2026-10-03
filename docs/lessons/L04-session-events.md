# L04 - 会话事件与多轮对话

**目标**：会话里发生的一切都成为不可变的事件事实（sealed 事件集 + SessionLog WAL），同一 agentId 连续多轮上下文延续，Agent 可查询可取消，策略树长出四节点（Intent 最小版），工作区可管。走完本课，`SessionLog` 从 L03 的消息版原位升级为事件版——这是 L05 SSE、L09 落库、L10 回放共同的地基。

**课程位置**：前置 L03（阻塞式对话 tracer——竖切通路就位）→ 本课 → 解锁 L05（SSE 直通只是事件的另一种读者）、L06（工具事件的生产者就位：ToolCall/ToolResult）。

## 概念与词汇

见 [CONTEXT.md](../../CONTEXT.md)：回合（Turn）/ 步（Step）/ 相位（Phase）/ 收件箱（Inbox）/ 会话事件（Session Event）/ 会话日志（SessionLog）/ 投影（Projection）/ 工作区（Workspace）/ 意图（Intent）。

## 配图

[![L04 Phase 三态状态机](assets/l04-phase-lifecycle.svg)](assets/l04-phase-lifecycle.html)

> 🔍 交互版 [assets/l04-phase-lifecycle.html](assets/l04-phase-lifecycle.html)：点击相位/中断节点聚焦、追踪转移边，切换明暗主题与「对话主循环 / 取消与唤醒补偿 / 维护相位」章节视图（GitHub 网页端仅显示源码，请本地克隆中打开）。

[![L04 turn→step 与事件落账结构](assets/l04-turn-steps.svg)](assets/l04-turn-steps.html)

> 🔍 交互版 [assets/l04-turn-steps.html](assets/l04-turn-steps.html)：沿「驱动主循环 / 事件账本 / 收尾与续步」三段导读逐层聚焦，点虚线边可追踪每层动作对应落账的事件（GitHub 网页端仅显示源码，请本地克隆中打开）。

## 精读路线（vendor 源码）

| 文件 | 导读问题 |
|------|---------|
| `domain/.../session/event/model/entity/SessionEvent.java` | 16 种 sealed permits 为什么不允许新增？`Generic` 变体存在的意义（遗留类型兼容）？`isSurfaceEvent()` 与 `surfaceIntent()` 两个 default 方法把「投影规则」挂在谁身上？ |
| `domain/.../session/event/model/entity/SessionLog.java` | append 为什么要 reassignSeq 而不是让调用方带 seq？appendBatch 刻意绕过 append 的注释在防什么（L09 PersistingSessionLog 覆写 append 的重复镜像）？deriveMessages 的 Replace 遮蔽区间如何工作？ |
| `domain/.../agent/model/entity/Phase.java` | 为什么 abort/wakeRequested 是 AtomicBoolean 而记录本身每次重建？Running 构造器接收 lastTurn 的含义？ |
| `domain/.../agent/service/run/ReactLoopAgent.java` 的 `send()/wakeDriver()/kick()/turn()/cancel()/status()/whenIdle()` | send 在 abort 态如何改排 NEXT_TURN？kick 收尾的递归重入条件为什么要求 `!wasAborted`？cancel 为什么是「标记载体」而非「杀死线程」？ |
| `cases/agent/node/AgentResolveNode.java#doApply()` | agentId 命中/未命中分别走什么？findPersistedSession 为什么要存在（重启 ≠ 会话丢失）？ |
| `cases/agent/node/AgentCollectNode.java#doApply()` | 事件→消息列表的折叠顺序？ToolCall 先 running、ToolResult 按 callId 回填 status——为什么 callId 取自 ToolMessageSource 而不是 Message.id？ |
| `cases/agent/node/AgentIntentNode.java` / `AgentDispatchNode.java` | classify 的正则优先级链（问候→代码→文件系统→任务→解释）？prepareMessageText 只对 TASK_EXECUTION 注入约束——闲聊为什么保持原文？ |
| `trigger/.../service/workspace/WorkspaceRegistryService.java` + Workspace Command/Query Controller | 顺序文件 `.workspace-order.json` 为什么是排序的唯一事实源（重启后顺序不丢）？safeName 只替换路径分隔符的取舍？ |

## 实现增量

**做**：`SessionEvent` sealed 16 变体 + 全套载荷/值对象（`SessionEventType`/`SurfaceOp`/`SurfaceIntent`/`TurnEndReason`/11 个 payload）+ `SessionHeader`（version=3）+ `SessionEventFactory`；`SessionLog` 事件版原位升级（append/appendBatch/events(After/Range)/lastEndSeedSeq/firstLiveSeq/restore/deriveMessages/requestHeader/requestContext 折叠）；`Phase` 三态 + `AgentCancelCause`；事件版 `Inbox`（拼接先落事件再改内存、重放恢复、重复 ID 校验、通知端口）；`ReactLoopAgent` 事件版（turn/step 逐段落账、claim 抢占、取消安全点、whenIdle、runMaintenance、updateOptions）；`AgentRunLifecycle.resume`；策略树四节点成形（`AgentIntentNode` 规则分类 + `AgentDispatchNode` 意图增强 + `AgentResolveNode` 复用/恢复占位 + `AgentCollectNode` 事件折叠含 callId 配对）；`getAgentStatus`/`cancelAgent` REST（`GET /{agentId}/status`、`POST /{agentId}/cancel`）；工作区 CRUD（`WorkspaceRegistryService` + Command/Query Api/Controller 六件 + 4 个 DTO）；types 增补 `ToolResultBlock`。

**不做**：事件持久化与 findPersistedSession 真实现（L09）、SSE sink 直通（L05）、工具与 ToolCall/ToolResult 的生产者（L06）、图片附件与 @插件提及（L07/L14）、系统提示词/压缩/受控续写（L08）、MaxTokens 自动续写（L08）、审批模式同步（L13）。

**与 vendor 的差异清单（L04 新增）**：

| 差异 | 理由 | 收敛课 |
|------|------|--------|
| `turn()` 收到 MaxTokens 直接 `turn/end(MaxTokens)` 结束回合 | vendor 的 ≤4 次受控续写依赖 buildRequest 注入续写提示词（L08 交付），本课注入它只会让模型重复收尾 | L08 |
| `ReactLoopAgent` 构造器 6+1 参（vendor 12 参：工具表/执行器/提示词组装器/压缩引擎/事件监听工厂等） | 这些依赖的模块分别在 L06/L08/L14 | 各归属课 |
| `step()` 首步记请求头的条件是 `step==1 \|\| requestHeader()==null`（vendor 另比 assembly.system()） | 系统提示词组装器是 L08；null 比对是其子集 | L08 |
| `buildRequest()` 恒传 system=null / tools=null，无 128K 预算裁剪与续写注入 | 系统提示词（L08）、工具 schema（L06）、预算裁剪（L08）均未接入 | L06/L08 |
| `AgentResolveNode` 无「运行中流式互斥」检查（vendor 对 RUNNING+流式请求抛 IllegalStateException） | 流式请求（deltaSink）本身是 L05 交付 | L05 |
| `MAX_STEPS_PER_TURN` 提为类常量（vendor 为 turn() 内局部 final） | 等价语义；常量更易被单测引用 | 不收敛（等价重构） |
| `SessionLog` 未搬运 vendor 的 derivedCache/derivedCacheSeq 死字段 | vendor 里从未读写（TS 增量缓存移植残留），保留只会误导；同函数内未使用的 `shadowed` 集合按原样保留 | 不收敛（删除死代码） |
| `AgentResolveNode.findPersistedSession()` 为占位（恒 null） | 事件存储 ISessionEventStore 是 L09 交付；vendor 靠 `@Autowired(required=false)` 可选装配 | L09 |
| case 节点/用例维持构造器注入（vendor 字段 `@Autowired`） | L03 差异延续：管线可脱离 Spring 单测 | 不收敛（有意增补） |
| `wakeDriver(wakeAfterAbort)` 参数原样保留但未使用 | vendor 自身如此（死参数），如实复刻 | 不收敛（保真） |
| `AgentUseCase` 新增构造器注入 `AgentRunLifecycle`（vendor 字段注入） | 同构造器注入差异 | 不收敛 |

**搬运与自建测试**：vendor 对应模块无会话/phase 独立单测（SessionLog/Phase/ReactLoopAgent 均无，`AgentIntentNodeTest`/`AgentDispatchNodeTest` 按 README 归 L08 搬运），本课全部自建——`SessionEventPermitsTest`（4 例：**sealed permits 与 vendor 字面全集逐项对拍** + 表面事件种类 + wireName 词汇表 + ignorable 标记）、`SessionLogTest`（6 例：seq 单调无空洞/appendBatch/Append 投影/Replace 遮蔽且不保位/end-seed 边界与 restore/请求头折叠缓存）、`InboxTest`（3 例：拼接事件落账与 claim 语义/重复 ID 拒绝/事件重放恢复队列）、`ReactLoopAgentTest` 重写扩展（6 例：完整事件账本逐 wireName 断言/失败软化+turn/end(Error)/两消息两回合边界/流中取消 Aborted+清箱幂等/中止态改排与唤醒补偿丢弃+维护相位/Flow 级流错误 → Error(STREAM_ERROR) 且不落助手消息）、`AgentMessageFlowTest` 重写扩展（4 例：**同 agentId 第二轮引用第一轮（fake 回声端口）**/新 agentId 独立/上游失败不炸链/推理折叠）、`WorkspaceRegistryServiceTest`（3 例：建删往返与路径分隔符替换/排序跨实例持久化/空名拒绝）、`AgentApiTest` 扩展（+2 例：status 透传/cancel 信封）。

## 验收清单

- [x] `mvn clean verify` 绿（api 1 / domain 24 / case 8 / infrastructure 10 / trigger 17，共 60 例；L03 为 37）
- [x] 同 agentId 第二轮引用第一轮内容（`AgentMessageFlowTest` fake 回声端口：第二轮 LLM 历史 3 条且首条是第一轮暗号；真服务 curl 同样验证）；新 agentId 独立（不同 sessionId、history=1）
- [x] cancel 后 status 空闲（`ReactLoopAgentTest` 流中取消 → turn/end(Aborted) → IDLE；curl `POST /{agentId}/cancel` 后 `GET /{agentId}/status` = idle；未知 agentId 返回 not-found）
- [x] `GET /api/agent/workspaces` 返回 `00000`；建（POST）/删（DELETE）/排序（PUT order）可用（curl 实测 + `WorkspaceRegistryServiceTest`；空名 40000）
- [x] 事件种类集合与 vendor sealed permits 一致（`SessionEventPermitsTest`：16 变体字面清单逐项对拍 + wireName 抽查）

**对拍点：事件契约面**

- `SessionEvent` permits：16 变体与 vendor 同名同序（TurnStart/TurnEnd/StepStart/StepEnd/UserMessage/AssistantChunk/AssistantMessage/ToolCall/ToolResult/TodoWrite/RequestHeader/RequestContext/SessionEndSeed/PlanModeChange/AgentInboxSpliced/Generic）
- `SessionEventType` wireName 词汇表（`turn/start`…`agent/event` 及 7 个遗留 `task/*`）与 ignorable 标记逐项一致
- 表面事件恰为三种：user/message、assistant/message、tool/result
- 工作区 REST 路由与请求/响应 DTO 字段集（`GET/POST/DELETE/PATCH /api/agent/workspaces*`、`PUT /workspaces/order`、`GET /workspaces/list?path=`）与 vendor 逐一对齐

## 踩坑与教学点

1. **为什么先有事件日志，再有 SSE / 持久化 / 回放**：事件是唯一事实源，其余都是读者。L05 的 SSE 帧只是把 `assistant/chunk` 抄送一份给浏览器；L09 的落库只是把 append 镜像到 MySQL/JSONL；L10 的回放只是把事件重放给投影器。如果本课先做消息列表，后面每加一种能力都要改数据结构；先做事件，后面每种能力都只是加一个订阅者。sealed permits 把「会话里允许发生什么」变成编译期契约——新增事件种类必须改 permits，评审时无法漏看。
2. **collect 节点的折叠投影**：响应里的 messages 不是存储，是 `session.events()` 的投影——按时间序遍历，UserMessage/AssistantMessage 展平文本，ToolCall 先建 `status=running` 的卡片，ToolResult 经 **callId（取自 ToolMessageSource，不是 Message.id）** 回填同一张卡片的 status/result。工具的生产者在 L06，但配对逻辑现在就位且被事件账本测试覆盖。
3. **收件箱是投影，事件才是本体**：`Inbox.mutate` 先 append `agent/inbox/spliced` 事件、再改内存队列——WAL 顺序（先账后态）。构造函数的 `replay()` 从事件重建队列，本课的测试用它证明「同一份事件日志能恢复出同一收件箱」；L09 重启恢复走的正是这条路。
4. **取消是标记，不是杀线程**：`cancel()` 只设 `abort` 原子标记（顺带清箱、发 agent/cancelled 事件）；真正的停止发生在安全点——step 的 `done.await(100ms)` 轮询圈与流的 `onNext` 各自检查 abort。杀线程会留下半写状态，标记让回合以 `turn/end(Aborted)` 体面收账。
5. **abort 丢弃唤醒补偿（vendor 语义，测试钉住）**：kick 收尾的重入条件是 `!wasAborted && wakeRequested && hasPending`——被中止的活动即使有唤醒请求也不重入，消息留在收件箱等下一次唤醒。这避免了「取消后立即又自己跑起来」的体验，代价是 keepInbox=true 的积压要等下一条消息才被消费。`ReactLoopAgentTest.sendWhileAbortingRequeuesMessageAndAbortDiscardsWakeCompensation` 把这条语义钉死。
6. **Phase.Running 每步重建**：`turn = new Phase.Running(abort, turn, step, wakeRequested)`——record 不可变，计步靠整体替换，而 abort/wakeRequested 是共享的 AtomicBoolean 引用，跨代存活。这就是「状态可变、身份不变」：同一逻辑活动的取消标记在每次重建后依然有效。
7. **claim 的「1 条 next-turn」规则决定多轮形态**：一次 claim 取走全部 next-step + 最早一条 next-turn——所以同时投递两条消息会产生两个回合（各有 turn/start 与 turn/end），而不是一个回合里两段回答。第二个回自然能看到第一个回合的 assistant/message（deriveMessages 折叠），这就是多轮延续的全部机制——没有任何专门的「会话管理」代码。
8. **deriveMessages 全量重建的取舍**：TS 版按表面节点增量缓存，Java 版（vendor 注释自述）在 profiling 证明热点前保持简单——每次 O(n) 重扫。known 性能债记入 L26 的已知性能项清单（与 vendor 一致）。
9. **工作区排序的持久化**：排序写在根目录 `.workspace-order.json`（换行分隔的名字清单），listWorkspaces 先合并「配置 + 磁盘目录」再按它排序——顺序跨重启存活，且它被排除在目录列表之外。`safeName` 只替换 `\` 和 `/`（允许 emoji/Unicode），既防路径逃逸又不限制命名表达。
10. **UserMessagePayload 在构造期校验 role=user**：错投角色的消息在事件构造瞬间失败而不是投影时悄悄变味——载荷校验前置是事件模型的自卫手段（同类：PlanModePayload 只接受 enter/exit、TodoItemPayload 只接受三种状态）。

## 自测题

1. 同一 agentId 第二条消息的「上下文延续」发生在哪一步？——第二轮 turn 的 claim 抢占后 user/message 落账，step 的 buildRequest 用 `session.deriveMessages()` 折叠出含第一轮的历史。链路里哪两处决定了「同一个会话」？（AgentRunFactory.liveAgents 命中 + SessionLog 按 sessionId 常驻）
2. `SessionLog.append` 为什么要 reassignSeq？如果调用方自带 seq=99 会发生什么？appendBatch 注释里「避免子类覆写 append」在为 L09 的哪个类预防什么 bug？
3. cancel(keepInbox=false) 之后：收件箱里排队的消息去了哪里（以什么事件落账）？正在流式输出的 step 在哪两个「安全点」感知 abort？被中止的 kick 为什么不做唤醒重入？
4. `Inbox.claim(NEXT_TURN, turn)` 一次取走多少条消息？同时投递「第一条」「第二条」会产生几个 turn/start 事件？midTurnContinuation 在 L04 为什么永远为 false，L06 的哪段代码会把它置 true？
5. 工作区排序为什么重启后不丢？`.workspace-order.json` 在 listWorkspaces 和 listDirectories 里分别怎么被处理？

---

**对拍记录**：`SessionEventPermitsTest.VENDOR_PERMITS` 为 vendor（ff2d0a5）`SessionEvent` permits 的字面抄录；工作区 REST 与 agent status/cancel 路由逐条比对 vendor `WorkspaceCommandController`/`WorkspaceQueryController`/`AgentController`。实测 curl 输出见本课 commit message 与验收清单。
