# L13 运行时治理：审批门+Hook+沙箱（S12）[对拍]

- 前置：L12（tag L12）
- 大纲节：docs/lessons/README.md#L13
- 术语：审批模式 / 审批门 / 必审清单 / 裁决 / 沙箱 / 钩子

## 事实基准（已核查，钉死）
- **审批门默认启用**：`AgentRunBeanConfig.java:159-181` 无条件注册 Broker 与 @Primary AggregatingRuntimeApprovalGateway；`:270-284` 13 参构造注入。README §14「默认 approvalBroker=null」已过时。
- **空必审清单 = 放行**（`MatrixRuntimeApprovalGate.java:83-84`）；超时 DENY（`RuntimeApprovalBroker.java:59-70`，默认 600000ms）。
- 必审清单默认：`shell_execute,fs_write,plugin.run,subprocess.spawn`。

## vendor 精读清单
- `infrastructure/.../config/AgentRunBeanConfig.java` L159-181、L270-284
- `domain/.../agent/service/run/RuntimeApprovalBroker.java`；`domain/.../tool/service/MatrixRuntimeApprovalGate.java`（Decision 语义 L73-98）
- `domain/.../agent/service/run/tool/ApprovalAwareShellExecutor.java`、`ApprovalAwareFsPort.java`（按 ApprovalMode 选端口）
- `infrastructure/.../digitalhuman/AggregatingRuntimeApprovalGateway.java`（本课先本地 Broker 聚合，远端端点 L24 接入）
- Hook：`domain/.../hooks/` + `infrastructure/.../adapter/plugin/java/CompositeHookService.java`、`InProcessPluginHookRegistry.java`、`LocalShellHookRunner.java` + `HookOutputMerger`（阻止类优先）
- 沙箱：`infrastructure/.../adapter/sandbox/LocalSandboxEnforcer.java`、`SandboxedShellExecutor.java`、`domain/.../tool/fs/SandboxedFsService.java`、`SandboxExtraRootsRegistry`
- `trigger/.../http/command/RuntimeApprovalController.java`——**对拍基准**；`ToolCallExecutor.java#checkApproval()`（L150-162，requires_approval 事件）

## 实现增量
- 做：Broker/Gate/Gateway 装配、ApprovalAware 端口包装、审批模式三档贯通（DTO→Resolve→Gate）、Hook 引擎接工具执行链、沙箱三档 + 动态 extra roots、RuntimeApproval REST、L06 预留占位全部接实。
- 不做：远端审批聚合（L24）、插件注册 Hook 的通道（L14 复用本课 Hook 引擎）。

## 测试搬运
- `ApprovalAwareShellExecutorTest`、`ApprovalAwareFsPortTest`（改包名）；新增：默认配置拦截、超时 DENY、空清单放行三用例。

## 验收（DoD 专项）
- [ ] **对拍**：`requires_approval` 事件与 RuntimeApproval REST 契约一致（记录进 lesson 文档）
- [ ] 默认配置下对话触发 shell_execute 被拦截；`resolve` ALLOW_ONCE 后继续；ALLOW_SESSION 本场免审；超时 DENY 回灌模型
- [ ] required-tools 清空后放行；FULL_OPEN 全放行
- [ ] WORKSPACE_WRITE 档越界写被拒；请求 sandboxRoots 动态放行；危险命令仍被守卫拦

## 教学文档与配图
- `docs/lessons/L13-runtime-governance.md`；mermaid：工具执行链上的治理切面总图（Hook→审批→沙箱三层嵌套）。

## 教学点
- README 与代码不一致的考古处理；「错误 DENY 而不是挂死」的超时语义；两层审批防线在此汇合。
