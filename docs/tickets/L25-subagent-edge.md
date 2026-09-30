# L25 Subagent 与边缘域工具（S23）

- 前置：L24（tag L24）
- 大纲节：docs/lessons/README.md#L25
- 术语：子代理 / 工具目录（条件注册）

## vendor 精读清单
- `domain/.../agent/service/subagent/`：`SubagentTool.java`、`SubagentRegistry.java`、`SubagentProvider.java`、`SpawnInProcessProvider.java`、`ForkInProcessProvider.java`
- `infrastructure/.../adapter/subagent/`：`OutOfProcessSubagentProvider.java`、`ClaudeCodeSubagentProvider.java`、`CodexSubagentProvider.java`、`AcpSubagentProvider.java`
- Terminal：`domain/.../tool/terminal/TerminalTools.java`（内部类×5）+ `LocalTerminalPort` + `cases/terminal/` 5 节点 + Terminal Command/Query Controller
- Jobs：`domain/.../tool/jobs/JobRunTool.java` 等 4 个 + `InMemoryJobRegistryRepository`
- Schedule：`domain/.../tool/schedule/ScheduleTools.java` ×3 + `InMemoryScheduleRepository`——**教学点：`ScheduleService#drive()` 无调度驱动方，如实保留**
- `domain/.../tool/lsp/LspTool.java` + `domain/lsp/`；`domain/.../tool/session/SessionSearchTool.java`
- `AgentToolCatalog.java` 条件注册段（L114-200 对应各行）+ `harness.subagent.*` 配置

## 实现增量
- 做：subagent 注册表与四 provider（外部 CLI provider 存在但无外部 CLI 时优雅降级）、terminal×5、job×4、schedule×3、lsp、session_search、条件注册接入工具目录。
- 不做：真正的调度驱动线程（vendor 没有）；E2B/typert 等 Stub 域（不入课程范围，lesson 文档记录为已知边界）。

## 测试搬运
- vendor subagent/terminal 相关单测（如有）；新增：进程内 spawn/fork 一例、job 生命周期、条件注册开关。

## 验收（DoD 专项）
- [ ] 配置后 subagent 工具注册；spawn 进程内子代理完成一个小任务并回传
- [ ] terminal open→send→read→close→list 全链；job 后台运行 output/kill
- [ ] 各条件工具随配置开关出现/消失；schedule 工具可用但（如实）不被自动驱动

## 教学文档与配图
- `docs/lessons/L25-subagent-edge.md`；mermaid：subagent 四 provider 分派图 + 边缘域条件注册全景。

## 教学点
- 「识别但如实不驱动」的复刻诚实度；条件注册让边缘域不干扰主线。
