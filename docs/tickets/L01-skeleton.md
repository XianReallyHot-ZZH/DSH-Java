# L01 七模块骨架与最小启动（S01）

- 前置：无（首课）
- 大纲节：docs/lessons/README.md#L01
- 术语：api 层 / types 模块 / domain 层 / case 层 / infrastructure 层 / trigger 层 / app 模块 / 统一响应信封（CONTEXT.md）

## vendor 精读清单
- `pom.xml`（根，L14-24）——`<modules>` 全集与插件版本管理
- `deepseek-harness-java-app/.../app/Application.java`——scanBasePackages + @MapperScan（MapperScan 本课可留 TODO）
- `deepseek-harness-java-api/.../api/response/Response.java`——00000/40000/50000 信封
- 各模块 `pom.xml`——依赖方向声明的原始出处
- ArchUnit 参照：`deepseek-harness-java-infrastructure` 的 archunit 依赖与 5 个架构测试的存放位置（本课只立骨架测试，五件套随课到齐）

## 实现增量
- 做：根 pom（七模块 + plugins 目录占位）、各模块 pom 依赖方向、`Application`、`Response`、`GET /` 占位页、ArchUnit 首建（api 零内部依赖 / domain 只依赖 types / trigger 不依赖 app 三条规则起步）、`.gitignore` 增补（target/、data/、*.jar 产物等）。
- 不做：任何业务功能、MyBatis、配置文件（L02）。

## 测试搬运
- vendor 无对应单测；新增：架构违规样例测试（证明 ArchUnit 能抓住「case 直接依赖 trigger」）。

## 验收（DoD 专项）
- [ ] `mvn clean verify` 绿；fat-jar 可 `java -jar` 启动并出 banner
- [ ] `GET /` 返回占位页；`Response` 三码语义与 vendor 一致
- [ ] 架构测试故意违规会红（在分支上验证后移除）

## 教学文档与配图
- `docs/lessons/L01-skeleton.md`；mermaid：七模块依赖图（对照报告 01 §2.3）。

## 教学点
- 依赖离心方向与组合根位置（infrastructure 而非 app）；types 与 domain 的 split package 现象及其取舍。
