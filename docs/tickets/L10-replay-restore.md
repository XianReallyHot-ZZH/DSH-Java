# L10 回放、恢复与历史面板（S09b + S19b）

- 前置：L09（tag L09）
- 大纲节：docs/lessons/README.md#L10
- 术语：投影 / 会话恢复 / 写租约

## vendor 精读清单
- `cases/session/ConversationQueryCaseImpl.java`、`SessionRestoreCaseImpl.java`、`SessionEventQueryCaseImpl.java`
- `domain/.../session/event/service/SessionRebuilderService.java`、`SurfaceProjector.java`、`InMemorySessionProjectionCache.java`、`SessionWriteLeaseService.java`
- `trigger/.../http/query/HarnessConsoleQueryController.java`、`http/command/HarnessSessionCommandController.java`
- vendor `app.js` 历史会话列表/消息回放段（行为级对照）

## 实现增量
- 做：sessions/messages 查询、restore REST、投影缓存与写租约、前端历史面板（列表+回放+恢复按钮）。
- 不做：JSONL v3（L21）、token 计量（L21）。

## 测试搬运
- `InMemorySessionProjectionCacheTest`、`SessionWriteLeaseServiceTest`（改包名）。

## 验收（DoD 专项）
- [ ] `GET /api/harness/console/sessions` 列历史；messages 按投影正确配对工具调用
- [ ] restore 后继续对话；同一会话并发写被租约拦
- [ ] 前端面板可浏览历史并恢复

## 教学文档与配图
- `docs/lessons/L10-replay-restore.md`；mermaid：事件流→投影→查询/恢复的三视图关系。

## 教学点
- 投影（读模型）与事件（写模型）分离；写租约解决什么并发问题。
