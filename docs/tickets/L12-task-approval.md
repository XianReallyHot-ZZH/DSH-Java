# L12 任务提交与审批链路（S11 + S19d）

- 前置：L11（tag L11）
- 大纲节：docs/lessons/README.md#L12
- 术语：任务 / 任务状态机 / 策略树

## vendor 精读清单
- `cases/task/submit/SubmitHarnessTaskFactory.java` 五节点：`SubmissionRootNode → ProfileResolutionNode → PermissionCheckNode → ToolResolutionNode → EnqueueNode`
- `domain/.../task/permission/service/PermissionPolicyService.java` + `infrastructure/.../adapter/port/PermissionMatrixPort.java`
- `domain/.../task/approval/service/ApprovalPolicyService.java`、`ApprovalCommandService.java`
- `domain/.../task/queue/`（HarnessTaskEntity 状态机）+ `task/execution/service/HarnessExecutionService.java#executeSession()`
- `domain/.../task/submission/service/filter/`（PromptNotBlank/ProfileCode/ToolSelection 三件）
- `trigger/.../http/HarnessTaskController.java`、`http/command/HarnessApprovalCommandController.java`、`http/query/HarnessApprovalQueryController.java`

## 实现增量
- 做：五节点提交链、权限矩阵、提交期审批决策、任务状态机与执行服务、过滤链、任务/审批 REST、前端审批面板（pending 列表+approve/reject）。
- 不做：运行期审批（L13，注意两套审批的边界——提交期审「任务该不该跑」，运行期审「这个工具调用放不放行」）。

## 测试搬运
- `ApprovalCommandServiceTest`、`PermissionPolicyServiceTest`、`TaskSubmissionPolicyServiceTest`（改包名）。

## 验收（DoD 专项）
- [ ] 提交含 shell_execute 的任务→pending 列表可见→approve→QUEUED→RUNNING→COMPLETED
- [ ] 对 PENDING_APPROVAL 会话直接 executeSession 抛异常（兜底守卫）
- [ ] 免审批任务直接执行；过滤链拦截空 prompt/非法 profile

## 教学文档与配图
- `docs/lessons/L12-task-approval.md`；mermaid：任务状态机 + 五节点提交链流程图。

## 教学点
- 提交期审批 vs 运行期审批的两层防线设计；五节点链与对话四节点链共用策略树抽象。
