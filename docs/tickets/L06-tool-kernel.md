# L06 工具系统内核（S06）

- 前置：L05（tag L05）
- 大纲节：docs/lessons/README.md#L06
- 术语：工具 / 工具目录 / 共享工具表 / 合成工具表 / 工具执行器 / 内容块 / 步 / ReAct 循环

## vendor 精读清单
- `domain/.../tool/adapter/ToolRegistry.java` + `agent/service/run/tool/InMemoryToolRegistry.java`、`CompositeToolRegistry.java`（lookup local 优先 / all() 顺序）
- `domain/.../tool/service/ToolCallExecutor.java`：`execute()`（L201-239）→ `runGroup()`（L289-560：分组、并行 maxParallel=10、按派发顺序提交 commitReady L568-579、超时、appendToolCall/appendSkippedToolCall）→ `commitOne()`（L587-651）
- `domain/.../tool/adapter/tool/JacksonToolArgumentsParser.java`
- `types/.../domain/model/entity/AbstractTool.java`、`ToolDefinition.java` + `model/valobj/ContentBlock.java` 族
- 工具首批：`domain/.../tool/fs/FsReadTool/FsWriteTool/FsSearchTool`、`tool/shell/ShellExecuteTool`；`infrastructure/.../adapter/shell/LocalShellExecutor.java`、`adapter/fs/LocalFsService.java` + `CwdResolvingFsPort`
- `domain/.../agent/service/run/tool/AgentToolCatalog.java#createRegistry()`（首批子集）与 `AgentRunFactory#wireAgent()`（registry 组装）
- `ReactLoopAgent.java` L696-760（工具判定：有 ToolCallBlock → 执行返回 null 续步）——**midTurnContinuation 语义**

## 实现增量
- 做：注册表三件套、执行器全链（Hook/审批留占位接口）、参数解析、首批四工具 + shell/fs 端口实现、工具目录装配、step 循环接入（step_break/tool_result 帧在 L05 帧定义上真正触发）。
- 不做：审批门（L13）、Hook 实现（L13）、其余工具（L07）、并行分组可先串行后补（验收前补齐）。

## 测试搬运
- `AgentToolCatalogTest`（首批部分）；新增：callId 配对有序、超时不挂死、错误回灌不中断回合。

## 验收（DoD 专项）
- [ ] 真端点「读某文件并总结」：事件流出现 ToolCall/ToolResult 对，SSE 出现 `step_break`→`tool_result`
- [ ] 工具超时按 TOOL_TIMEOUT 失败、回合继续；工具错误作为结果回灌模型
- [ ] shell 相对路径按 cwd 解析（CwdResolvingFsPort）

## 教学文档与配图
- `docs/lessons/L06-tool-kernel.md`；mermaid：工具执行链流程图（解析→Hook→审批→调度→提交）+ turn/step 状态流。

## 教学点
- 「结果按派发顺序提交」为何是事件日志完整性的关键；`step()` 返回 null 的续步契约。
