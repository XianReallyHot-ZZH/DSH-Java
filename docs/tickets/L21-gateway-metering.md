# L21 Gateway SSE、Token 计量与会话 v3（S20）[对拍]

- 前置：L20（tag L20）
- 大纲节：docs/lessons/README.md#L21
- 术语：网关流 / SSE 事件帧 / Token 计量 / 事件存储 / 写租约

## vendor 精读清单
- `trigger/.../http/GatewayStreamController.java` + `trigger/.../service/stream/GatewayStreamApi.java#stream()`（L69+，按 requestType 分派 streamAgent/streamWorkflow）——**帧契约对拍基准**
- `api/.../gateway/dto/GatewayStreamEventDTO.java`（统一信封）
- `domain/.../runtime/meter/service/SessionTokenMeterService.java`（输入/输出/缓存/推理四类聚合）
- `infrastructure/.../adapter/eventstore/JsonlSessionEventStore.java`（SESSION_FORMAT_VERSION=3 版本守卫）
- `domain/.../session/event/service/SessionWriteLeaseService.java`、`InMemorySessionProjectionCache.java`（L10 已建，本课接 JSONL 轨与版本守卫）

## 实现增量
- 做：Gateway 双分派流、统一信封 DTO、token 计量服务接入会话事件、JSONL 事件存储（v3 守卫）作为 JDBC 之外的备选轨。
- 不做：数字人网关的远端语义（L23/L24 消费）。

## 测试搬运
- `JsonlSessionEventStoreTest`、`SessionTokenMeterServiceTest`（改包名；L10 已搬的两件勿重复）。

## 验收（DoD 专项）
- [ ] **对拍**：`POST /api/gateway/stream` 对 agent 与 workflow 两种 requestType 的帧序列/信封字段与 vendor 一致（记录进 lesson 文档）
- [ ] 会话级四类 token 用量可查且数值与 fake 流一致
- [ ] JSONL 轨：写入→重启→readAll 逐事件一致；旧格式版本被守卫拒绝

## 教学文档与配图
- `docs/lessons/L21-gateway-metering.md`；mermaid：两路 SSE 汇入 Gateway 统一信封图 + 会话存储双轨（JDBC/JSONL）图。

## 教学点
- 统一信封是为 A2A/数字人消费做的提前收敛；v3 版本守卫是「事件格式即契约」的体现。
