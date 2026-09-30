# 逐课工单索引与通用执行协议

> 一课一票（`docs/tickets/L<NN>-<slug>.md`），票序即课序：L<NN> 阻塞 L<NN+1>。
> 每票消费：本票文件 + [课程大纲对应节](../lessons/README.md) + [CONTEXT.md](../../../CONTEXT.md) + [研究报告](../research/)。

## 票索引

| 票 | 文件 | 前置 | 切片 |
|----|------|------|------|
| L01 | [L01-skeleton.md](L01-skeleton.md) | — | S01 |
| L02 | [L02-config-auth.md](L02-config-auth.md) | L01 | S02 |
| L03 | [L03-tracer-chat.md](L03-tracer-chat.md) | L02 | S03 |
| L04 | [L04-session-events.md](L04-session-events.md) | L03 | S04 |
| L05 | [L05-sse-console.md](L05-sse-console.md) | L04 | S05+S19a |
| L06 | [L06-tool-kernel.md](L06-tool-kernel.md) | L05 | S06 |
| L07 | [L07-tool-matrix.md](L07-tool-matrix.md) | L06 | S07 |
| L08 | [L08-intent-compaction.md](L08-intent-compaction.md) | L07 | S08 |
| L09 | [L09-persistence-core.md](L09-persistence-core.md) | L08 | S09a |
| L10 | [L10-replay-restore.md](L10-replay-restore.md) | L09 | S09b+S19b |
| L11 | [L11-model-channels.md](L11-model-channels.md) | L10 | S10+S19c |
| L12 | [L12-task-approval.md](L12-task-approval.md) | L11 | S11+S19d |
| L13 | [L13-runtime-governance.md](L13-runtime-governance.md) | L12 | S12 |
| L14 | [L14-plugin-core.md](L14-plugin-core.md) | L13 | S13a |
| L15 | [L15-plugin-management.md](L15-plugin-management.md) | L14 | S13b+S19e |
| L16 | [L16-plugin-engineering.md](L16-plugin-engineering.md) | L15 | S14 |
| L17 | [L17-node-bridge.md](L17-node-bridge.md) | L16 | S15 |
| L18 | [L18-mcp.md](L18-mcp.md) | L17 | S16+S19f |
| L19 | [L19-skills.md](L19-skills.md) | L18 | S17 |
| L20 | [L20-workflow-goal.md](L20-workflow-goal.md) | L19 | S18 |
| L21 | [L21-gateway-metering.md](L21-gateway-metering.md) | L20 | S20 |
| L22 | [L22-a2a.md](L22-a2a.md) | L21 | S21 |
| L23 | [L23-digital-human.md](L23-digital-human.md) | L22 | S22a |
| L24 | [L24-collaboration-room.md](L24-collaboration-room.md) | L23 | S22b |
| L25 | [L25-subagent-edge.md](L25-subagent-edge.md) | L24 | S23 |
| L26 | [L26-finalize.md](L26-finalize.md) | L25 | S24+S19 终态 |

## 通用执行协议（对每票生效，票内不重复）

1. **会话纪律**：一票一个全新会话（Phase 2）。会话内顺序：读本票与链接文档 → `/implement`（内部走 TDD）→ `/code-review`（两轴：代码标准 / 是否达成本课 spec）。课与课之间 `/clear`。
2. **DoD 通用项**（除票内专项外一律要求）：
   - `mvn clean verify` 绿（编译 + 全量回归）；
   - 票内「测试搬运」清单改包名后全绿；
   - 产出 `docs/lessons/L<NN>-<slug>.md`，按大纲模板（含至少一张 mermaid 配图：本课核心架构/链路/状态机）；
   - master 直接提交（≥1 commit）并打 tag `L<NN>`；lesson 文档末尾附 `git diff L<NN-1>..L<NN> --stat` 摘要（L01 附首提交统计）。
3. **包名映射**（ADR-0001）：`cn.xiaofuge.deepseek.harness` → `io.github.xianreallyhotzzh.dsh`，仅前缀替换；groupId `io.github.xianreallyhotzzh`，artifactId 与 vendor 相同。
4. **vendor 只读**：`vendors/deepseek-harness-java` 固定 `ff2d0a5`；报告中行号仅供导航，以实际代码为准。
5. **LLM 端点**：L03 起手工验收需环境变量 `LLM_BASE_URL`/`LLM_API_KEY`/`LLM_DEFAULT_MODEL`（开工前向用户确认已配置，不要索取明文）；自动化测试一律脚本化 fake，禁止外呼。
6. **对拍口径**：见大纲「对拍总口径」节；[对拍] 票必须把对拍结果（一致项/差异项及理由）写进 lesson 文档验收节。
7. **验收 UI**：带前端面板增量的票，面板功能作为该课验收项（对 vendor `app.js` 对应段落做行为级对照，不逐行抄写）。

## 拉伸练习登记（不占课序，学有余力时做）

- **L14 后**：将 `vendors/dsh-java-mysql/dsh-java-mysql-plugin` 源码做同样前缀替换后编译，安装进复刻件并调通一个只读查询——外部验证 types SPI 复刻到位。
- **L22 后**：用 vendor 实例（或复刻件双开）作为远端数字人互相对话，验证 A2A 互通。
