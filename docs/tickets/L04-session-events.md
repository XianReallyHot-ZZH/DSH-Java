# L04 会话事件与多轮对话（S04）

- 前置：L03（tag L03）
- 大纲节：docs/lessons/README.md#L04
- 术语：回合 / 步 / 相位 / 收件箱 / 会话事件 / 会话日志 / 投影 / 工作区

## vendor 精读清单
- `domain/.../session/event/model/entity/SessionEvent.java`（sealed permits 全集——对拍基准）
- `domain/.../session/event/model/entity/SessionLog.java`（append/appendBatch/deriveMessages）
- `domain/.../agent/model/entity/Phase.java`
- `domain/.../agent/service/run/ReactLoopAgent.java`：`send()/wakeDriver()/kick()/turn()/cancel()/status()/whenIdle()`
- `cases/agent/node/AgentResolveNode.java#doApply()`（agentId 复用、findPersistedSession 占位）与 `AgentCollectNode.java#doApply()`（事件折叠为消息列表、callId 配对）
- `cases/agent/node/AgentIntentNode.java` / `AgentDispatchNode.java`（本课最小实现，完整版 L08）
- `trigger/.../service/workspace/WorkspaceRegistryService.java` + Workspace Command/Query Controller

## 实现增量
- 做：sealed 事件集 + SessionLog + Phase 三态 + Inbox 唤醒语义 + turn 循环（单回合内多 message claim）+ cancel/status REST + 四节点策略树成形（Intent/Dispatch 最小版）+ 工作区 CRUD。
- 不做：事件持久化（L09）、SSE sink（L05）、工具（L06）。

## 测试搬运
- vendor 对应模块会话/phase 相关单测（如有）；新增：两轮上下文延续用例（fake LLM）。

## 验收（DoD 专项）
- [ ] 同 agentId 第二轮引用第一轮内容；新 agentId 独立
- [ ] cancel 后 status 空闲；`GET /api/agent/workspaces` 返回 `00000`，建删排序可用
- [ ] 事件种类集合与 vendor sealed permits 一致（对拍点）

## 教学文档与配图
- `docs/lessons/L04-session-events.md`；mermaid：Phase 状态机 + turn→step 结构图。

## 教学点
- 为什么先有事件日志再有 SSE/持久化/回放（事件是唯一事实源）；collect 节点的折叠投影。
