# DSH-Java 复刻课程（deepseek-harness-java 统一语言）

本仓库以循序渐进的 lesson 复刻 `vendors/deepseek-harness-java`（基准 `ff2d0a5`）。本表是该系统的领域词汇表，同时是课程的教学词汇表；术语以中文为正名，首次出现附英文。

## 平台与分层

**Harness（运行时基座）**:
承载智能体对话、工具执行、扩展生态与协作协议的单实例服务端平台；本课程复刻的对象本身。
_Avoid_: 框架、中间件、网关（Gateway 另有所指）

**api 层**:
对外契约模块——全部对外接口（`I*Api`）、DTO 与统一响应信封，零业务逻辑，不依赖任何内部模块。

**types 模块**:
插件开发契约包——外部插件工程唯一需要依赖的模块，定义插件 SPI 与内容块值对象。

**domain 层**:
领域核心——纯 Java、零 Spring 注解的 40+ 子域（agent/tool/session/plugin/…），处于依赖关系圆心。
_Avoid_: 核心包、common

**case 层**:
用例编排层——每个用例一棵策略树，把请求编排为 domain 调用。
_Avoid_: service 层、业务层

**infrastructure 层**:
端口适配层兼组合根——实现 domain 声明的端口、承载 DAO，以及全工程的 Spring 装配中心。

**trigger 层**:
触发层——HTTP 控制器、api 接口的实现方、SSE 流服务与鉴权过滤器。

**app 模块**:
Spring Boot 启动器——main 入口、资源文件与启动 Runner；是扫描入口而非装配中心。

**策略树（Case Pipeline）**:
case 层用例的编排形态：请求依次流过若干节点（Node），每个节点只做一件事，首节点固定为 Resolve。
_Avoid_: 责任链、流水线（Pipeline 在本表指它，工作流是另一个词）

**端口（Port）**:
domain 层声明的能力接口；由 infrastructure 提供实现。
_Avoid_: 接口（泛称）

**适配器（Adapter）**:
端口的某种具体技术实现（协议适配、JDBC、本地 shell 等）。

**组合根（Composition Root）**:
把纯 Java 的 domain 对象装配成对象图的地方；在本项目位于 infrastructure 的 config，而非 app。

**统一响应信封（Response）**:
所有非流式 REST 接口的返回包装：`00000` 成功、`40000` 参数错、`50000` 内部错。
_Avoid_: Result、ApiResponse

## Agent 与对话循环

**Agent**:
一个有身份（agentId）、有收件箱、有相位、有专属工具表的智能体运行实例。
_Avoid_: 助手、bot、会话（会话是 Agent 名下的上下文）

**驱动循环（Agent Run）**:
Agent 被消息唤醒后自驱动推进的过程，从 Idle 出发再回到 Idle。

**回合（Turn）**:
一次用户消息引发的完整处理：从消息入箱到回合结束（完成/出错/取消）。单回合步数有上限。
_Avoid_: 轮、轮次

**步（Step）**:
回合内的一次 LLM 调用及其后续动作：要么执行模型请求的工具批，要么产出最终回复。
_Avoid_: 轮（与 Turn 不可混用——Turn 由多个 Step 组成）

**ReAct 循环**:
「模型思考→请求工具→结果回灌→继续」的步间接力，直到模型不再请求工具。

**收件箱（Inbox）**:
Agent 的消息队列；投递即唤醒驱动循环。
_Avoid_: 队列（泛称）

**相位（Phase）**:
Agent 的三态生命周期：Idle / Running / Maintenance。
_Avoid_: 状态（泛称，与任务状态机区分）

**意图（Intent）**:
消息在派发前被归类到的四类之一：CHAT / CODE_QUESTION / TASK_EXECUTION / CLARIFICATION；纯正则判定，无额外模型调用。

**压缩（Compaction）**:
上下文逼近预算时的会话瘦身：保留近期消息，把更早的部分总结为摘要。

**受控续写（Continuation）**:
回复被 max_tokens 截断时自动拼接的续写，有次数上限。

## 会话与事件

**会话（Session）**:
一个 agentId 名义下延续的对话上下文；进程重启不必然丢失。
_Avoid_: 对话记录、上下文（泛称）

**会话事件（Session Event）**:
会话中不可变的事实记录（用户消息、助手分片、工具调用、工具结果、回合边界等）；封闭集合，不允许随意新增种类。

**会话日志（SessionLog）**:
事件的可追加日志（WAL）；内存为先，持久化异步镜像，不阻塞流式收尾。

**事件存储（Event Store）**:
事件日志的持久化落点，MySQL 表与 JSONL 双轨。
_Avoid_: 数据库、消息队列

**投影（Projection）**:
把事件流折叠为人类可读消息列表的过程（如按 callId 配对工具调用与结果）。

**会话恢复（Restore）**:
进程重启后从事件日志重建会话日志、使对话可继续的能力。
_Avoid_: 回放（回放偏只读查看历史）

**写租约（Write Lease）**:
同一会话同一时刻只允许一个写者的并发防护。

**Token 计量（Token Meter）**:
会话级输入/输出/缓存/推理 token 的累计口径。

## 工具系统

**工具（Tool）**:
模型可调用的能力单元：以 schema 声明、以 JSON 参数调用、以内容块回灌结果。

**工具目录（Tool Catalog）**:
创建 Agent 时装配其本地工具表的清单；哪些工具条件注册由它决定。

**共享工具表（Shared ToolRegistry）**:
全局单例工具注册表；插件工具、扩展管理工具与 MCP 工具的共同汇聚点。

**合成工具表（CompositeToolRegistry）**:
Agent 实际使用的工具表 = 本地表 + 共享表，本地同名覆盖共享。

**工具执行器（ToolCallExecutor）**:
工具调用的统一执行链：解析参数→前置 Hook→审批→调度（并行/串行、限时）→结果提交与事件落账；错误作为工具结果回灌，不中断回合。

**内容块（Content Block）**:
消息与工具结果的内容单元（文本、推理、图片、工具调用等）。
_Avoid_: 片段、分片（chunk 特指流式增量）

**工作区（Workspace）**:
对话的工作目录上下文（cwd 之源），可建删排序。

**危险命令守卫（Dangerous Command Guard）**:
shell 工具内拦截高危命令的检查器，先于执行。

## 治理：审批与沙箱

**审批模式（ApprovalMode）**:
请求级的三档策略：REQUEST_APPROVAL（默认）/ AUTO_APPROVE / FULL_OPEN。

**审批门（Approval Gate）**:
工具执行链上的拦截点：命中必审清单的工具阻塞等待人工裁决，超时视为拒绝；默认装配启用。
_Avoid_: 权限（权限矩阵是任务提交期概念）

**必审清单（Required Tools）**:
需要审批的工具名列表（默认 shell_execute、fs_write、plugin.run、subprocess.spawn）；空清单即全放行。

**裁决（Approval Decision）**:
人工对运行期审批的判定：允许一次（ALLOW_ONCE）/ 允许整场（ALLOW_SESSION）/ 拒绝（DENY）/ 取消。

**沙箱（Sandbox）**:
文件与 shell 的三档越界防护：READ_ONLY / WORKSPACE_WRITE / DANGER_FULL_ACCESS。

**钩子（Hook）**:
挂在工具调用前后等切点上的可插拔检查；阻止类结果优先合并（任一阻断即阻断）。

## 扩展生态

**插件（Plugin）**:
独立工件封装的能力扩展，带清单与生命周期；两条可执行通道（JAVA_NATIVE、DSH_NODE_BRIDGE）加两条仅识别占位（CODEX_PLUGIN、CORDIS_PROFILE）。

**JAVA_NATIVE 通道**:
插件 JAR 以隔离类加载器在宿主 JVM 内加载，工具进程内直调，无序列化无子进程。

**DSH_NODE_BRIDGE 通道**:
Node 脚本作为 sidecar 子进程拉起，宿主经 stdio 上的 JSON-RPC 调用其工具。

**插件 SPI（JavaHarnessPlugin）**:
插件作者实现的接口契约；只依赖 types 模块，不接触任何 Spring 类型。

**插件清单（Plugin Manifest）**:
JAR 内的 `META-INF/plugin.yaml`；id/name/version 必填，entrypoint 优先于 ServiceLoader 回退。

**插件上下文（PluginContext）**:
插件向宿主贡献能力的唯一注册边界（工具、事件、Hook、提示词段、配置）；事务性——关闭时逆序回收全部贡献项，失败不留悬挂。

**插件生命周期（Plugin Lifecycle）**:
PENDING → LOADING → ACTIVE → UNLOADING → DISPOSED / FAILED。

**登记状态 / 运行时状态**:
插件的双轨状态：数据库里的声明意图 vs 内存里的真实加载事实；启动时对账纠正。
_Avoid_: 单说「插件状态」

**热重载（Hot Reload）**:
监视已登记 JAR 的变更并自动 stop→start 的机制。

**技能（Skill）**:
纯 Markdown 提示词包：零代码、零注册表，文件系统即真相；按需经 skill 工具注入对话，不预注入系统提示词。
_Avoid_: 技能=插件、技能=MCP

**技能根（Skills Root）**:
技能的发现目录（目录束 `<name>/SKILL.md` 或扁平 `<name>.md`）；多根并存带优先级，rank 低者胜。

**MCP**:
外部工具服务器协议；服务端工具以 `mcp__<server>__<tool>` 命名注册进共享工具表，支持 stdio / SSE / streamable-http 三种传输。

**子代理（Subagent）**:
Agent 派生的下级代理：进程内 spawn/fork，或委托外部 CLI（claude-code / codex / acp）。

## 模型接入

**LLM 端口（LlmRuntimePort）**:
domain 对模型调用声明的能力接口，流式订阅语义；实现负责按渠道路由到协议适配器。

**协议路由（Protocol Routing）**:
按渠道配置的协议（openai / anthropic / ollama）选择具体适配器的转发层。

**模型渠道（Model Channel）**:
一组上游模型接入参数（provider、baseUrl、apiKey、协议、默认模型），可增删与激活；生效优先级：请求显式值 > 数据库设置 > 配置文件兜底。

**渠道模板（Channel Preset）**:
常见供应商的渠道参数预填模板。

## 流式与协作协议

**SSE 事件帧**:
流式对话的帧协议：meta / chunk / reasoning / step_break / tool_result / finish / done / error。

**流回调（Sink）**:
Agent 运行期把增量吐给流服务的回调束（delta / reasoning / toolCall / finish）；挂载是互斥的——同一 Agent 同时只允许一条流。

**增量批（Delta Batch）**:
帧发送前的短窗口聚合，平衡流畅与发送开销。

**心跳（Keepalive）**:
SSE 空闲期的周期性注释行，防中间层断连。

**网关流（Gateway Stream）**:
Agent 与工作流共用的统一 SSE 出口与事件信封。
_Avoid_: 与 Agent 流式混称

**A2A**:
Agent-to-Agent 开放协议（JSON-RPC：message/send、message/stream、tasks/get、tasks/cancel）。

**Agent Card**:
描述 Agent 能力的发现文档，挂在 well-known 路径；本地与远端靠它互相探测。

**数字人（Digital Human）**:
可登记的远端（或本地）Agent 端点，经 Agent Card 探测与健康检查纳入目录；是人格化的 Agent 端点，不是真人。
_Avoid_: 虚拟人、角色

**协作房间（Collaboration Room）**:
多个数字人参与、带任务编排（改派/重试/恢复）与事件流的协作容器。

## 任务、工作流与目标

**任务（Harness Task）**:
走提交链（提交→权限→审批→入队）的异步工作单元，与对话回合相互独立。
_Avoid_: 与 Turn / Step 混称

**任务状态机**:
CREATED → PENDING_APPROVAL → QUEUED → RUNNING → COMPLETED / FAILED。

**工作流（Workflow）**:
复用 Agent 链路的多步编排，可独立启动、流式输出与取消。
_Avoid_: Pipeline（那是 case 层策略树）

**目标（Goal）**:
会话级目标状态的查询与维护域。
