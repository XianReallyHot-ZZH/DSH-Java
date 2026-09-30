# L05 SSE 流式与最小对话页（S05 + S19a）[对拍]

- 前置：L04（tag L04）
- 大纲节：docs/lessons/README.md#L05
- 术语：SSE 事件帧 / 流回调 / 增量批 / 心跳

## vendor 精读清单
- `trigger/.../service/stream/AgentStreamApi.java`：`sendMessageStreaming()/emitAgentStream()/startHeartbeat()/sendDelta/sendReasoning/sendToolCall/sendFinish/sendDone/sendError`（L61-271）——**帧协议对拍基准**
- `trigger/.../service/stream/` 的 `DeltaBatch.java`、`DonePayloads.java`（currentTurnMessages 瘦身）
- `cases/agent/factory/AgentMessageDynamicContext.java#attachSinks()`（L94-121）
- `domain/.../ReactLoopAgent.java` 的 `setStream*Sink()`（L221-246，volatile 单实例）与 Resolve 节点流式互斥检查
- `app/src/main/resources/static/index.html` + `app.js`（只读对话页最小段：SSE 订阅与渲染）

## 实现增量
- 做：SseEmitter 流服务（超时 0、sseExecutor 缓存线程池）、60ms 增量批、15s 心跳、八种帧、error 收口 completeWithError、sink 挂载与互斥；静态最小对话页（meta/chunk/reasoning/finish/done 渲染）。
- 不做：step_break/tool_result 的真实触发（L06 接入，本课帧定义先就位）、工作流流（L20）、Gateway（L21）。

## 测试搬运
- 新增：帧序列 fake 测试（meta 先于 chunk；done 必达；error 后 complete）；断连清理测试。

## 验收（DoD 专项）
- [ ] 真端点 `curl -N POST /api/agent/stream` 逐 token 输出，序列 `meta → chunk* → finish → done`
- [ ] **对拍**：八种帧名、载荷字段、顺序约束逐一与 vendor 比对，结果记录进 lesson 文档
- [ ] 同 Agent 第二条流被拒（互斥）；断连无线程泄漏；心跳在长思考期可见

## 教学文档与配图
- `docs/lessons/L05-sse-console.md`；mermaid：SSE 序列图（对照报告 01 §3.2）。

## 教学点
- volatile sink 与互斥的因果；增量批与心跳的工程动机；错误路径必须 completeWithError 防悬挂。
