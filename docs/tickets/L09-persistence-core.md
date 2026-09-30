# L09 持久化核心：事件落库（S09a）[对拍]

- 前置：L08（tag L08）
- 大纲节：docs/lessons/README.md#L09
- 术语：会话日志 / 事件存储 / 会话恢复

## vendor 精读清单
- `app/src/main/resources/schema.sql`——**会话四表逐列对拍基准**：`harness_session`(L1)、`harness_session_event`(L21)、`harness_session_header`(L95)、`harness_session_event_log`(L107)；只建本课所属表，其余表留到所属课
- `app/src/main/resources/application-standalone.yml`（H2 MODE=MySQL）+ `application.yml` 的 `spring.sql.init.mode=always`
- `infrastructure/.../dao/ISessionEventRowDao.java`（注解 SQL）及 po；`adapter/eventstore/JdbcSessionEventStore.java#append()`
- `domain/.../agent/service/run/PersistingSessionLog.java`（异步镜像、单线程执行器；注意其 javadoc 自称 infrastructure 层已过时——教学点）
- `cases/agent/node/AgentResolveNode.java#findPersistedSession()`（L138-160 重启恢复）

## 实现增量
- 做：会话四表建表、MyBatis dao/repository、JDBC 事件存储、PersistingSessionLog 异步镜像、standalone profile、Resolve 节点接持久化恢复。
- 不做：MySQL profile 联调（可选加练）、JSONL 轨（L21）、控制台查询/restore REST（L10）。

## 测试搬运
- 新增（H2）：重启恢复用例（写事件→重建 store→readAll 逐事件一致）；异步镜像不阻塞收尾的时序断言。

## 验收（DoD 专项）
- [ ] standalone 启动自动建表幂等（重复启动无错）
- [ ] **对拍**：四表 DDL 表名/列名/类型逐列与 vendor 一致，结果记录进 lesson 文档
- [ ] 对话若干轮→重启→历史事件完整、恢复会话继续对话上下文不断裂

## 教学文档与配图
- `docs/lessons/L09-persistence-core.md`；mermaid：内存 WAL → 异步镜像 → JDBC 事件存储数据流。

## 教学点
- 为什么镜像必须异步（远程 MySQL 延迟不能叠进流式收尾关键路径）；「进程重启 ≠ 会话丢失」的完整闭环。
