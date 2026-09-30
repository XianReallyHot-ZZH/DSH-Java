# L26 收尾：测试基线、性能与部署（S24 + S19 终态）

- 前置：L25（tag L25）
- 大纲节：docs/lessons/README.md#L26
- 术语：全课词汇总复习

## vendor 精读清单
- ArchUnit 五件套：`TriggerArchitectureTest`、`CaseArchitectureTest`、`ApiArchitectureTest`、`CaseOrchestrationArchitectureTest`、`AgentRunArchitectureTest`（随各课已陆续建立，本课对齐补全规则面）
- `ApplicationIntegrationSmokeTest`（含 `exposesCoreConsoleAndQueryEndpoints()`）
- `docs/md/test-cases.md`（116 条用例登记——对齐口径参考）
- 部署：根 `docker-compose.yml`、`Dockerfile`（多阶段 + standalone profile）、`scripts/package-local.sh`、`app/StartupInfoRunner.java`
- 前端：`static/app.js` 终态对照（全流程走查清单）

## 实现增量
- 做：全量测试对齐（目标 107 用例基线 0 失败）、ArchUnit 五件套齐、冒烟测试、Dockerfile+compose、本地 zip 分发+start 脚本、StartupInfoRunner、前端终态打磨（遗漏面板/交互补齐）、已知性能项文档化（deriveMessages O(n²)、token length/4 粗估、无界线程池——记录不修复，注明为 vendor 现状）、课程总结文档。
- 不做：性能优化（超出复刻范围）；`.github/workflows`（可选加练）。

## 验收（DoD 专项）
- [ ] `mvn clean test` 0 失败；ArchUnit 规则面与 vendor 对齐
- [ ] `docker compose up --build -d` 后冒烟端点 200；`package-local.sh --zip` 产物 `./start.sh` 可起
- [ ] 控制台全流程手工走查：建工作区→对话→工具卡片→审批→历史→插件→模型切换
- [ ] 性能已知项与复刻边界清单写入 lesson 文档

## 教学文档与配图
- `docs/lessons/L26-finalize.md`；mermaid：全课程 26 课知识地图（分层×切片总览）。

## 教学点
- 复刻的终点定义：行为对齐 + 交付形态对齐，而非逐行相同；收尾课同时是全课程的复盘索引。
