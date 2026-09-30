# L24 协作房间（S22b）

- 前置：L23（tag L23）
- 大纲节：docs/lessons/README.md#L24
- 术语：协作房间 / 数字人 / A2A / 审批门（远端聚合）

## vendor 精读清单
- `infrastructure/.../digitalhuman/CollaborationService.java`（1342 行：房间/参与者/消息/任务 cancel-resume-retry-reassign/事件 SSE）
- `infrastructure/.../digitalhuman/PlannerService.java`（自动分工）
- `infrastructure/.../digitalhuman/RemoteAgentGateway.java`（631 行，调远端 A2A）
- `infrastructure/.../digitalhuman/AggregatingRuntimeApprovalGateway.java`（本地 Broker + 远端端点聚合——L13 预留的接入点）
- 表五张：`collaboration_room`(L197)、`room_participant`(L208)、`collaboration_task`(L221)、`collaboration_artifact`(L242)、`room_event`(L257) + dao/po
- `trigger/.../http/CollaborationController.java`（10 个端点）

## 实现增量
- 做：协作域五表、房间全生命周期、消息与事件 SSE、任务编排（Planner 分工 + 失败恢复三动作）、远端网关接 A2A 客户端、远端审批聚合接 L13 网关。
- 不做：前端房间 UI（vendor 控制台无此面板，桌面端才有——走读 dsh-java-desktop 即可）。

## 测试搬运
- `RemoteAgentGatewayTest`、`RemoteAgentGatewayA2aRunTest`（改包名）。

## 验收（DoD 专项）
- [ ] 建房→加两个数字人（其一可为复刻件自身的 card）→发消息→事件 SSE 可见
- [ ] 任务失败后 reassign/retry 生效；cancel 级联终止
- [ ] 远端成员的审批请求经聚合网关可达并回传裁决

## 教学文档与配图
- `docs/lessons/L24-collaboration-room.md`；mermaid：房间协作全景图（Planner→任务 DAG→远端 A2A→事件流）。

## 教学点
- L22 的协议端点 + L13 的审批网关在此合流——课程后半段的伏笔回收课。
