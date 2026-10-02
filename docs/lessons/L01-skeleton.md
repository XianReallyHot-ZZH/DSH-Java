# L01 - 七模块骨架与最小启动

**目标**：立起与 vendor 同构的 Maven 七模块骨架与依赖方向，`Response` 信封就位，应用可启动。走完本课，你拥有一个 `mvn clean verify` 全绿、`java -jar` 可启动、依赖方向被 ArchUnit 守护的空壳工程。

**课程位置**：无前置（首课）→ 本课 → 解锁 L02（配置中心与 API 认证——第一个真实 REST 端点与配置模型）。

## 概念与词汇

见 [CONTEXT.md](../../CONTEXT.md)：api 层 / types 模块 / domain 层 / case 层 / infrastructure 层 / trigger 层 / app 模块 / 统一响应信封（Response）。

## 配图：七模块依赖图（L01 视角）

对照 [报告 01 §2.3](../research/01-module-dependencies-and-request-lifecycle.md)。L01 与报告图的两处差异：外部插件工程（EXT 子图）随 L14 进 `plugins/`；trigger→infrastructure 的 pom 依赖边 **L01 即存在**（vendor trigger pom 原样搬运），但 L01 无任何类级使用——vendor 中真正用到这条边的只有 L23/L24 数字人/协作控制器（报告画作虚线例外的正是这层含义），届时在 lesson 中重提。

[![七模块依赖图（L01 视角）](assets/l01-modules.svg)](assets/l01-modules.html)

> 🔍 交互版 [assets/l01-modules.html](assets/l01-modules.html)：点击节点聚焦依赖、追踪关系边、切换明暗主题与「依赖主干 / 双契约根」章节视图（GitHub 网页端仅显示源码，请本地克隆中打开）。

依赖方向整体**离心式**：domain（+types）处于圆心零反向依赖；api 是独立契约片；case/infrastructure 只踩 domain（case 另持有 api 做DTO 出参）；trigger 把三者粘起来；app 只做启动与装配扫描。

## 精读路线（vendor 源码）

| 文件 | 导读问题 |
|------|---------|
| 根 `pom.xml` L14-24 | `<modules>` 顺序与实际构建顺序一致吗？plugins 子模块为何在列？（Maven 自己拓扑排序，modules 顺序无关） |
| `app/.../app/Application.java` | 为什么 scanBasePackages 是全包而非 app 自身？@MapperScan 扫的是哪个包？ |
| `api/.../api/response/Response.java` | 三码语义各对应哪类故障？为什么 code 是 String 不是 int？ |
| 各模块 `pom.xml` | 依赖方向的「原始出处」在哪一层声明？（pom 即架构图） |
| `infrastructure` pom + 5 个 `*ArchitectureTest.java` 存放位置 | ArchUnit 测试为何放在被约束模块自己的 test 里？（工单此行有笔误：vendor 的 5 个架构测试实际在 api/trigger/case/domain，infrastructure 只带 archunit 依赖、无架构测试——复刻件按 vendor 实际位置放置） |

## 实现增量

**做**：根 pom（七模块 + plugins 目录占位）；各模块 pom 依赖方向；`Application`（scanBasePackages，@MapperScan 留 TODO 至 L09）；`Response`；`GET /` 占位页（app 静态资源）；banner.txt；ArchUnit 三条起步规则 + 违规样例证明；`.gitignore` 增补。

**不做**：任何业务功能、MyBatis/jdbc/mysql/h2（L09）、配置文件（L02，启动走 Spring Boot 全默认：端口 8080）。

**与 vendor 的 pom 差异清单**（后续课逐项补齐）：

| 差异 | 理由 | 补齐课 |
|------|------|--------|
| 根 pom 去掉 `skipTests=true` / `maven.test.skip=false` | vendor 默认跳过测试（跑测试需 `-DskipTests=false`）；复刻件测试即课程，默认执行 | 不补齐（有意差异） |
| 根 pom 无 `mybatis-spring-boot.version` 属性 | MyBatis 属 L09 | L09 |
| types pom 去掉 scm/licenses/developers 与注释掉的 central 发布配置 | 复刻件不发布中央仓库 | 不补齐 |
| app pom 去掉 sample-tools-plugin 依赖与 copy-plugin-jars 执行 | 插件工程未建 | L14 |
| app pom 去掉 spring-boot-starter-actuator | 探活端点非骨架必需 | 收尾课评估 |
| case pom 去掉 snakeyaml | 勘误（L01 原注「harness.yml 解析属配置中心」归因有误，L02 更正）：vendor 的消费方是插件清单解析（PluginArtifactAnalyzerService 读 JAR 内 plugin.yaml） | L15 |
| infrastructure pom 去掉 starter-jdbc / mybatis / mysql / h2 | 持久化属 L09 | L09 |

保留的 vendor 原味：junit 版本钉子（api/domain 钉 5.8.2 + junit-bom，trigger/case/infrastructure/app 用 spring-boot-dependencies 管理的 5.10.2）、domain 的 spring-context/jackson `provided` 作用域、surefire 2.22.2。

## 验收清单

- [x] `mvn clean verify` 绿（api 1 测试、domain 1 测试、trigger 2 测试，共 4 个）
- [x] `java -jar deepseek-harness-java-app/target/deepseek-harness-java-app.jar` 启动出 banner，Tomcat 8080，`Started Application`
- [x] `GET /` 返回占位页；`Response` 三码（00000 成功 / 40000 参数错 / 50000 内部错）与 vendor 逐字段一致
- [x] 架构测试故意违规会红：在 `l01-red-check` 分支注入 domain→api 依赖（pom 加边 + `ViolatingProbe` import `Response`），`DomainArchitectureTest` 红并点名两处违规（分支按要求验证后删除、未入库，输出摘录为证）：

  ```text
  Architecture Violation ... was violated (2 times):
  Method <io.github.xianreallyhotzzh.dsh.domain.ViolatingProbe.probe()> calls method
      <io.github.xianreallyhotzzh.dsh.api.response.Response.success(java.lang.Object)>
  Method <io.github.xianreallyhotzzh.dsh.domain.ViolatingProbe.probe()> has return type
      <io.github.xianreallyhotzzh.dsh.api.response.Response>
  ```

  回到 master 复绿（`Tests run: 1, Failures: 0`）；「case 不得依赖 trigger」另有常驻 fixture 测试 `archRuleMustCatchCaseDependingOnTrigger` 持续证明规则命中会红。

## 踩坑与教学点

1. **组合根在 infrastructure 不在 app**：`Application` 只做 `scanBasePackages` 全包扫描；把纯 Java 的 domain 对象装配成对象图的 `@Bean` 配置在 vendor 位于 `infrastructure/config`（L03 起逐步复刻）。app 是「扫描入口」而非「装配中心」。
2. **types 与 domain 的 split package**：vendor types 模块的类全部住在 `cn.xiaofuge.deepseek.harness.domain.spi/model.valobj` 包段下，与 domain 模块**同包不同 jar**。好处：插件作者写 `import ...domain.spi.JavaHarnessPlugin` 时无 types/domain 包名割裂感；代价：包级规则无法区分两个模块、JPMS 下不可模块化。因此「domain 只依赖 types」的 ArchUnit 规则只禁其余四层，types 与 domain 同包段、无需也无法用包模式区分；「api 零内部依赖」禁 `domain..` 即同时覆盖了 types。
3. **Maven 是第一道架构防线，ArchUnit 守 Maven 看不见的**：case→trigger 这类跨模块反向依赖在 pom 依赖图上根本无法编译（加边即成环，reactor 直接报错），所以「case 不得依赖 trigger」的违规样例只能以测试 fixture 形式寄放在 trigger 模块（唯一能同时看见 cases/trigger 两个包段的测试 classpath），证明规则命中时会红。ArchUnit 的真正价值在于同 classpath 内的包纪律（如 vendor 用它守 api 的 DTO 命名、trigger 方法返回类型）。
4. **空模块与 `allowEmptyShould`**：ArchUnit 规则默认对「选择集为空」报错（防规则悄悄失效）。L01 的 domain/trigger 主源码为空，这两条规则显式 `allowEmptyShould(true)`，随课程填充自然生效；api 规则因 `Response` 在而无需此设置。
5. **vendor 的 skipTests 陷阱**：vendor 根 pom 默认 `skipTests=true`，照抄会让「verify 绿」形同虚设（根本没跑测试）——复刻件默认执行测试，这是有意的、需要记住的差异。
6. **`-pl` 单模块构建要带 `-am`**：模块构件从未 `install` 进本地仓库时，`mvn test -pl X` 解析不到兄弟依赖，需 `-am` 让 reactor 一并构建。
7. **启动零配置**：没有任何 application.yml 时 Spring Boot 全默认起（8080、banner.txt 生效、static/index.html 即 `GET /`）。配置文件从 L02 才开始存在。

## 自测题

1. 把 `@MapperScan` 提前到 L01 打开会发生什么？（提示：infrastructure 还没有 dao 包，也没有 MyBatis 依赖）
2. 为什么「api 零内部依赖」的 ArchUnit 规则不需要单独列 `types..` 包？
3. 若在 trigger 的 pom 里加一条对 app 的依赖，Maven 会怎么反应？ArchUnit 规则还有机会报红吗？
4. vendor 与复刻件对 `skipTests` 的默认值为何不同？各自的服务对象是什么？
5. `Response.success(data)` 返回的信封里 `info` 字段是什么值？`fail` 系列的 `data` 呢？
