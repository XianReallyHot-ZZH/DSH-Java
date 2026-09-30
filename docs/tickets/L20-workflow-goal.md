# L20 工作流与目标（S18）

- 前置：L19（tag L19）
- 大纲节：docs/lessons/README.md#L20
- 术语：工作流 / 目标 / 网关流（本课不出，L21）

## vendor 精读清单
- `cases/workflow/WorkflowPipelineFactory.java`（WorkflowStartNode/WorkflowCancelNode）
- `domain/.../workflow/service/WorkflowService.java` + `WorkflowStartPolicy.java`
- `infrastructure/.../adapter/workflow/LocalWorkflowEnginePort.java`
- `trigger/.../http/command/WorkflowCommandController.java` + `trigger/.../service/stream/WorkflowStreamApi.java`（**只推终态事件 + 300s 超时 + 心跳——如实复刻**）
- goal 域：`cases/goal/GoalCommandCaseImpl.java`、`GoalQueryCaseImpl.java`；`domain/.../goal/model/entity/GoalAggregate.java`；`domain/.../tool/goal/GoalTools.java`（goal_get/create/update 条件注册）+ `harness_goal_state` 表（schema.sql L79）

## 实现增量
- 做：工作流 start/cancel/stream（复用 Agent 链路）、启动策略、本地引擎端口、goal 聚合与 REST 与 goal 三工具。
- 不做：Gateway 统一信封（L21 包一层）；工作流中间步流式（vendor 只推终态，如实）。

## 测试搬运
- `WorkflowStartPolicyTest`（改包名）。

## 验收（DoD 专项）
- [ ] `POST /api/workflow/start` 后 stream 收到事件帧、`/{runId}/cancel` 终止
- [ ] 工作流内部能调工具完成多步任务（fake 或真端点各验一次）
- [ ] `GET /api/harness/goals/{sessionId}` 返回目标视图；goal 工具按条件注册

## 教学文档与配图
- `docs/lessons/L20-workflow-goal.md`；mermaid：工作流复用 Agent 链路的组件图。

## 教学点
- 「复用而非另起炉灶」：工作流引擎的端口化让本地实现先行的合理性；只推终态是已知边界不是缺陷。
