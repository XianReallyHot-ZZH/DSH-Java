# 研究报告 3/3：deepseek-harness-java 的 plugins 与 skills 子系统机制

> 研究对象：`vendors/deepseek-harness-java`（git submodule，固定于 ff2d0a5，只读）。
> 所有路径相对 `vendors/deepseek-harness-java/`；引用格式为 `路径/ToFile.java#方法名()`。
> 下文区分两类内容：**机制代码**（harness 宿主自带，构成扩展点本身）与**示例/内置实现**（机制的一个实例，位于 `plugins/`、`skills/` 目录）。

---

## 0. 核心结论摘要

1. **插件（plugins）有两条完整独立的运行时通道**：`JAVA_NATIVE`（插件 JAR 以隔离 `URLClassLoader` 在宿主 JVM 内加载，工具直接注册进共享工具表）与 `DSH_NODE_BRIDGE`（Node 脚本作为子进程拉起，通过 stdio 上的 JSON-RPC 桥接工具）；另有 `CODEX_PLUGIN`/`CORDIS_PROFILE` 两种"识别但不执行"的占位类型（`deepseek-harness-java-domain/.../plugin/registry/model/valobj/PluginRuntimeTypeEnumVO.java`，`deepseek-harness-java-infrastructure/.../adapter/port/PluginRuntimeBridgePort.java#inspectNodeBridge()`）。
2. Java 插件的 **SPI 契约是 `JavaHarnessPlugin` 接口 + `META-INF/plugin.yaml` 清单**（entrypoint 优先，缺失时回退 ServiceLoader），发现与实例化在 `deepseek-harness-java-infrastructure/.../adapter/plugin/java/JavaPluginLoader.java#load()`；生命周期状态机 `PENDING→LOADING→ACTIVE→UNLOADING→DISPOSED/FAILED` 定义在 types 模块的 `PluginLifecycleState`。
3. 插件向宿主贡献能力的**唯一注册边界是 `PluginContext`**（注册工具/事件/Hook/配置/提示词段），宿主实现为 `SpringPluginContext`，它是一个事务性回滚边界（`close()` 逆序回收所有贡献项）。
4. **skills 是纯 Markdown 提示词包**（无代码、无数据库注册表），按"目录束 `<name>/SKILL.md` 或扁平 `<name>.md`"两种形态放在 4 个有优先级(rank)的根目录下，由 `FilesystemSkillProviderPort` 每次调用时即时发现，`SkillService` 按"rank 低者胜"合并。
5. skills 注入对话的时机**不是 system prompt 预注入，而是 `skill` 工具按需加载**：模型调用 `skill{name}` 后，技能正文以工具结果文本（`<skill>` 包裹）进入会话上下文（`deepseek-harness-java-domain/.../tool/skill/SkillTool.java#execute()`）。
6. **两条机制共享同一个主流程接缝**：全局单例 `ToolRegistry`（`AgentRunBeanConfig#toolRegistry()`）。插件工具、`skill` 工具、`extension_*` 管理工具、MCP 工具全部注册进它；每个 Agent 创建时 `AgentToolCatalog#createRegistry()` 把它包装成 `CompositeToolRegistry(local, shared)`，`ReactLoopAgent#resolveToolSchemas()` 在每个 LLM step 前取 `schemas()` 下发。
7. 管理面（安装/激活/运行/停用）在 **case 层用策略路由 Pipeline 编排**（`PluginRuntimePipelineFactory` 五节点链），领域规则在 **domain 层**（`PluginRegistryService`/`PluginBridgeService`/`PluginRuntimeService`），技术细节全部下沉到 **infrastructure 端口实现**（`PluginArtifactInstallerPort`/`PluginRuntimeBridgePort`/`PluginProcessPort`）。
8. 配置统一走 `harness.extensions.*`（`HarnessExtensionsProperties`），实际值在 `deepseek-harness-java-app/src/main/resources/harness.yml`；插件登记/桥接绑定/插件配置三张 MySQL 表持久化（`schema.sql`），技能启停名单在 `<home>/.dsh/extensions.json`。

---

## 1. plugins 机制（生命周期逐环节）

### 1.1 定义：SPI 契约（机制代码，types 模块）

插件作者只依赖 `deepseek-harness-java-types` 一个模块，不接触任何 Spring 类型：

| 契约 | 文件 | 关键成员 |
|---|---|---|
| 插件接口 | `deepseek-harness-java-types/src/main/java/cn/xiaofuge/deepseek/harness/domain/spi/JavaHarnessPlugin.java` | `String pluginId()`；`default List<ToolDefinition> tools()`；`default void configure(PluginContext)`（默认逐个 `context.registerTool(tool)`）；`default void onStart()`；`default void onStop()` |
| 便捷基类 | 同目录 `AbstractHarnessPlugin.java` | 构造器锁死 pluginId（`pluginId()` 为 final），装配 slf4j Logger |
| 注册上下文 | 同目录 `PluginContext.java` | `registerTool(ToolDefinition)` / `registerDisposer(AutoCloseable)` / `subscribe(String, PluginEventHandler)` / `emit(String, Object)` / `registerHook(String hookPoint, PluginHook)` / `getConfig(String[,String])` / `registerSystemPrompt(String name, int order, String text)` |
| 清单模型 | 同目录 `PluginManifest.java` | `id/name/version/author/description/entrypoint`（id、name、version 必填） |
| 状态机 | 同目录 `PluginLifecycleState.java` | `PENDING, LOADING, ACTIVE, UNLOADING, DISPOSED, FAILED` |
| 工具基类 | `deepseek-harness-java-types/.../domain/model/entity/AbstractTool.java` | 工具实现继承它，只需 `name/description/parameters/execute` |

配套但独立于 SPI 的次级契约：`PluginConfigStore`（`get/setOverride/setDefaults`，两层 DEFAULT/OVERRIDE）、`PluginEventBus`、`PluginHook/PluginHookResult`、`PluginEvent/PluginEventHandler`（均在同一 `domain/spi/` 目录）。

### 1.2 发现与实例化：JavaPluginLoader

`deepseek-harness-java-infrastructure/src/main/java/cn/xiaofuge/deepseek/harness/infrastructure/adapter/plugin/java/JavaPluginLoader.java`（`@Component`）。

- **发现**：不扫描目录。调用方给一个确定 JAR 路径；`#load(Path)` 校验文件存在 → `new URLClassLoader(new URL[]{jar}, Thread.currentThread().getContextClassLoader())`（第 62-65 行，父加载器为宿主 context classloader，即插件能看到宿主 types 类但宿主看不到插件类）。
- **清单解析**：`#readManifest()` 用 `classLoader.findResource("META-INF/plugin.yaml")` + SnakeYAML 读取；id/name/version 缺失即判无效清单（第 105-124 行）。
- **实例化**：`#instantiate()` 双通道——优先 `manifest.entrypoint` 反射 `getDeclaredConstructor().newInstance()`；为空时回退 `ServiceLoader.load(JavaHarnessPlugin.class, classLoader)` 扫 `META-INF/services/...JavaHarnessPlugin`，且要求 `candidate.pluginId() == manifest.id()` 且**恰好一个**匹配（第 131-164 行）。
- **初始化（插件侧）**：加载成功即调 `plugin.onStart()`，再取 `tools()`（第 76-77 行）。
- **卸载**：`#unload(LoadedPlugin)` 先 `onStop()`（异常只告警）再静默关闭 ClassLoader（第 92-99 行）。

### 1.3 装配进宿主：JavaPluginRuntimeManager（生命周期真正发生地）

`deepseek-harness-java-infrastructure/.../adapter/plugin/java/JavaPluginRuntimeManager.java`（`@Component`），进程内所有 Java 插件的生命周期管理器，持有 `Map<String, RunningJavaPlugin> runningPlugins`。

**`#start(pluginId, jarPath)`（第 96-141 行）的精确顺序：**
1. 重复启动：先回滚旧注册再返回"already running"；
2. `new SpringPluginContext(pluginId, toolRegistry, eventBus, hookRegistry, configStore, scopedPromptAssembler)`；
3. `loader.load(jarPath)`（内部已执行插件 `onStart`）；
4. `loaded.instance().configure(context)` —— **插件在此注册工具/事件/Hook/提示词**；
5. `context.registeredToolNames()` 收集本次注册的限定工具名；
6. `#registerPromptSection()`（第 223-240 行）：仅当 `promptAssembler instanceof ScopedSystemPromptAssembler` 且工具非空时，向系统提示词追加固定段 `## Plugin capabilities: <id>\n<description>\nAvailable tools: ...`（`PromptSection("plugin:<id>:capabilities", order=150)`），并把移除器挂进 context；
7. 状态置 `ACTIVE`，`hotReloader.watch(pluginId, jarPath)` 登记热重载；
8. `eventBus.publish(new PluginEvent("host", "plugin.started", pluginId, null))`；
9. 任一步异常 → `rollbackQuietly(context)`（逆序 close 全部贡献项）+ `loader.unload(loaded)`，返回失败结果。

**`#stop(pluginId)`（第 171-184 行）**：状态 `UNLOADING` → 发布 `plugin.stopping` → `context.close()`（`SpringPluginContext#close()` 第 165-179 行：复制列表后逆序 close，单个失败不阻断）→ `loader.unload()`（`onStop` + 关 ClassLoader）→ 状态 `DISPOSED` → `hookRegistry.unregisterAll(pluginId)` 兜底清 Hook → 发布 `plugin.stopped`。

**热重载**：`#hotReload()` = synchronized{ stop; start }（第 149-153 行）。触发源 `PluginHotReloader`（同目录）：`ApplicationReadyEvent` 后 `#start()` 起 daemon 线程，对 installRoot 目录注册 `WatchService`（`ENTRY_MODIFY`/`ENTRY_CREATE`），只匹配**已登记的 `.jar` 绝对路径**，命中即回调 `JavaPluginRuntimeManager#startHotReloader()` 传入的 `this::hotReload`（`JavaPluginRuntimeManager.java` 第 159-162 行）。

**路径解析**：`#resolveJarPath(sourcePath, entrypoint)`（第 202-216 行）四级回退——source 本身是 .jar → `source/<entrypoint>.jar` → `source/<目录名>.jar` → `source/META-INF/plugin.yaml` 存在则视为目录。

### 1.4 PluginContext 宿主实现：SpringPluginContext

`SpringPluginContext.java`（包私有，`implements PluginContext, AutoCloseable`）。核心是 `List<AutoCloseable> registrations` 事务边界：

- `#registerTool()`（第 62-75 行）：把插件的 `ToolDefinition` 包一层 `InProcessToolBridge`（`InProcessToolBridge.java`，把 JVM 内工具适配成 `IPluginToolBridge` 协议，`callTool` 直接 `delegate.execute()`，无序列化无子进程）→ 构造 `PluginToolDefinition`（名字变为 `plugin__<pluginId>__<toolName>`，见 `deepseek-harness-java-domain/.../tool/plugin/PluginToolDefinition.java#name()` 第 37 行）→ 注册进**共享 `ToolRegistry`** → 登记注销器。
- `#registerSystemPrompt()`（第 136-146 行）：段落名加前缀 `plugin__<pluginId>__<name>`，写入 `ScopedSystemPromptAssembler`。
- `#registerHook()`：`hookPoint` 必须是宿主 `HookPoint` 枚举名（PRE_TOOL_USE / POST_TOOL_USE / USER_PROMPT_SUBMIT / SESSION_START / STOP / NOTIFICATION，见 `PluginContext.java` 第 48-52 行注释），写入 `InProcessPluginHookRegistry`。
- `#subscribe()/#emit()`：走 `InProcessPluginEventBus`（`@Component`，进程内事件总线）。

### 1.5 管理面：从 REST 到领域服务到端口的完整链路（机制代码，三层）

**trigger 层**：`deepseek-harness-java-trigger/.../http/command/HarnessPluginCommandController.java`，`@RequestMapping("/api/harness/plugins")`：`POST /install`、`/activate`、`/run`、`/{pluginId}/disable|enable|uninstall`；查询侧 `HarnessPluginQueryController.java`：`GET ""`（已装列表）、`/inventory`、`/{pluginId}/status`。另有 `HarnessPluginAnalysisController`、`HarnessPluginConfigCommand/QueryController`（插件配置 CRUD，走 `cases/plugin/config/PluginConfigService`）。

**case 层（编排）**——安装与运行是两条策略路由 Pipeline：

- 安装：`InstallHarnessPluginCaseImpl#execute()` → `PluginInstallPipelineFactory#pipeline()` → 单节点 `InstallPluginNode#doApply()` → `pluginRegistryService.installPlugin(...)` → 映射为 `InstallPluginResponseDTO`。
- 运行/激活：`RunHarnessPluginCaseImpl#execute()` → `PluginRuntimePipelineFactory#pluginPipeline()`，节点链（`PluginRuntimePipelineFactory.java` 第 48-61 行 + 各 node 的 `getNext()`）：
  `ResolvePluginNode`（按 pluginId 从登记服务加载实体）→ `InspectBridgeNode`（`pluginBridgeService.inspect(plugin)` 生成 `PluginBridgePlanVO`）→ `BindBridgeNode`（`refreshStatus(plugin, bridgeReady)` 落库 ACTIVE/FAILED + `recordBinding` 持久化绑定）→ 按 `PluginRuntimeKind` 分叉：`ACTIVATE → ActivatePluginNode`（仅返回计划），`RUN → RunPluginNode`（真正拉起）。

**domain 层（规则）**：

- `domain/plugin/registry/service/PluginRegistryService.java#installPlugin()`（第 53-97 行）：校验字段与 pluginId 字符集 `[A-Za-z0-9][A-Za-z0-9._-]*` → `PluginRuntimeTypeEnumVO.valueOf(runtimeType)` → `JAVA_NATIVE ⇒ NATIVE` 否则 `NODE_BRIDGE` → `pluginArtifactInstallerPort.install(...)` → 构造 `HarnessPluginEntity(status=REGISTERED)` 落库。`#refreshStatus()`（第 124-132 行）是登记状态与桥接检查的唯一汇合点；`#uninstallPlugin()` 先落终态再 `pluginArtifactInstallerPort.uninstall()` 清文件。
- `domain/plugin/bridge/service/PluginBridgeService.java`：`inspect()` 纯委托端口；`recordBinding()` 写桥接仓储。
- `domain/plugin/runtime/service/PluginRuntimeService.java#start()`：校验 plan 有 entrypointPath 后委托 `IPluginProcessPort`；`status()/stop()` 同理。

**infrastructure 层（端口实现，通道分叉点）**：

- `adapter/port/PluginArtifactInstallerPort.java#install()`（第 40-54 行）：`.jar` 文件 → 复制为 `<installRoot>/<pluginId>.jar`（`#installJar()`）；目录 → 整树复制到 `<installRoot>/<pluginId>/`（`#installDirectory()`，先删后拷），entrypoint 必须在源树内。`#uninstall()`（第 63-75 行）删除对应目录与 JAR，路径越界即抛异常。
- `adapter/port/PluginRuntimeBridgePort.java#inspect()`（第 43-61 行）——**通道判定的真实实现**：
  - `JAVA_NATIVE` → `#inspectJavaNative()`：`javaPluginRuntimeManager.resolveJarPath()` 找到 JAR 即 ready，`runtimeCommand="java-in-process:<pluginId>"`（第 88 行），包类型 `DSH_PACKAGE`；
  - 其他 → `#inspectNodeBridge()`：`.codex-plugin/plugin.json` → `CODEX_PLUGIN`（**bridgeReady=false**，"execution still depends on Codex host semantics"）；`package.json` → `#inspectDshPackage()`，entrypoint 依次取请求 entrypoint、manifest `main`，找到则 `bridgeReady=true`、`runtimeCommand="node <entrypoint>"`；仅有 `cordis.yml` → `CORDIS_PROFILE`，不 ready。
- `adapter/port/PluginProcessPort.java#start()`（第 47-61 行）——**执行分叉点**：
  - `JAVA_NATIVE` → `#startJavaPlugin()` → `javaPluginRuntimeManager.start(pluginId, plan.entrypointPath())`（JVM 内，processId 恒 0）；
  - Node → `pluginSidecarGatewayService.start(pluginId, workDir, entrypoint)` 拉起子进程，成功后 `runningProcess()` 取回 `Process` 交给 `pluginToolBridgeService.bridgePlugin(pluginId, process)`；桥接异常则立即 `stop()` 并重抛（第 53-59 行）。
  - `#stop()` 先 `unbridgePlugin` 再按 java/Node 双通道停（第 90-98 行）。
- `gateway/PluginSidecarGatewayService.java#start()`（第 45-85 行）：`nodeBridgeEnabled` 开关（`harness.extensions.plugins.node-bridge-enabled`）；workDir 与 entrypoint 都必须落在 installRoot 内；入口必须是 `.js/.mjs/.cjs`；`ProcessBuilder("node", entrypoint)`，stderr 追加到 `<installRoot>/runtime-logs/<pluginId>.log`。
- `adapter/plugin/JsonRpcPluginToolBridge.java`（Bean 定义在 `config/HarnessApplicationConfig.java#jsonRpcPluginToolBridge()` 第 92-95 行）：stdio 上的 JSON-RPC 2.0，约定 sidecar 实现 `initialize`/`tools/list`/`tools/call` 三方法，握手发 `notifications/initialized`，默认超时 60s。

**工具桥接落表**：`domain/tool/plugin/PluginToolBridgeService.java#bridgePlugin(pluginId, process)`（第 54-91 行）：`connect`（JSON-RPC 握手）→ `listTools` → 逐个 `new PluginToolDefinition(pluginId, bridge, desc)` 注册进共享 `ToolRegistry` → `#registerPromptSection()`（第 139-165 行）向系统提示词写 order=150 的能力段（工具名用限定全名，描述文本做 `sanitizePromptText` 换行清洗防提示注入）。`#unbridgePlugin()` 移除提示词段、注销工具、断开连接。

### 1.6 持久化

- 登记：`infrastructure/adapter/repository/HarnessPluginRepository.java`（`@Repository`，委托 `dao/IHarnessPluginDao`）→ MySQL 表 `harness_plugin_installation`（`deepseek-harness-java-app/src/main/resources/schema.sql` 第 30-40 行）。
- 桥接绑定：`adapter/repository/HarnessPluginRuntimeBindingRepository.java` → 表 `harness_plugin_runtime_binding`（schema.sql 第 42-51 行）。
- 插件配置：`adapter/plugin/java/DatabasePluginConfigStore.java`（`@Repository`，DEFAULT/OVERRIDE 两层）→ 表 `harness_plugin_config`（schema.sql 第 53-61 行）；`InMemoryPluginConfigStore` 是测试替身。Spring 注入的是 Database 版。

### 1.7 启动时的预置与对账（app 层）

`deepseek-harness-java-app/src/main/java/cn/xiaofuge/deepseek/harness/app/plugin/PresetPluginLoader.java`（`ApplicationRunner`，`@Order(20)`）：

- `#run()`：先 `#reconcilePersistedPlugins(presetPluginIds)` 对账 DB 中全部非预置插件——运行时状态优先于登记状态：已在运行但登记非 ACTIVE 的纠正为 ACTIVE；登记 ACTIVE 但运行时缺失的重新 `runCase.execute()` 拉起（第 131-163 行）。这是重启后插件工具/提示词恢复的关键。
- 再遍历 `harness.extensions.plugins.preset`：未登记的走 `installCase.execute()` 安装，`auto-start` 的再走 `runCase.execute()` 启动；已登记但 DISABLED 且 autoStart 的 `enablePlugin` 复活（`#installPreset()` 第 90-121 行）。

---

## 2. skills 机制

### 2.1 skill 是什么

**纯 Markdown 提示词包，零代码、零数据库注册表。** 两种物理形态（`infrastructure/adapter/skill/FilesystemSkillProviderPort.java#discoverRoot()` 第 150-174 行）：

1. 目录束：`<skills-root>/<name>/SKILL.md`；
2. 扁平文件：`<skills-root>/<name>.md`（文件名去 `.md` 即技能名）。

文件可带 YAML frontmatter，`#parseSkill()`（第 201-230 行）只认两个键：`description`、`when_to_use`；无 frontmatter 时取首行（`#` 开头则去掉井号）当 description。正文（frontmatter 之后）即技能内容。

### 2.2 目录结构与命名约定

发现根与优先级（`FilesystemSkillProviderPort.java#discover()` 第 95-117 行，常量在第 25-28 行）：

| 优先级 rank | SkillSource | 路径 | 开关 |
|---|---|---|---|
| 100 | `PROJECT_DSH` | `<cwd>/.dsh/skills` | `harness.extensions.skills.project-enabled` |
| 300 | `CUSTOM` | `harness.extensions.skills.roots` 列表（逐个） | `harness.extensions.skills.enabled` |
| 400 | `USER_DSH` | `<dshHome>/skills`（dshHome 取 `skills.home`，空则 `harness.credentials.home` 即 `~/.dsh`） | 同上 |
| 600 | `BUNDLED` | `harness.extensions.skills.bundled-dir` | 同上 |

技能名必须匹配 `^[a-z0-9]+(?:-[a-z0-9]+)*$`（kebab-case，`domain/skill/service/SkillService.java#SKILL_NAME` 第 26 行，`#get()` 第 65 行强校验）。运行期停用名单 `disabledNames`（volatile Set，`#setDisabledNames()` 第 80-82 行）直接在发现层过滤。

### 2.3 注册与加载：port-provider-registry 三层

- 端口（机制接口）：`domain/skill/adapter/port/ISkillProviderPort.java` —— `List<SkillCandidate> discover(String cwd)` / `Optional<SkillDefinition> load(SkillCandidate)`。**注册方式是 Spring 构造器注入 `List<ISkillProviderPort>`**（`SkillService` 构造器第 36-38 行），目前唯一实现是 filesystem 版（Bean 工厂方法 `config/HarnessApplicationConfig.java#skillProviderPort()` 第 292-312 行）。
- 领域注册表：`domain/skill/service/SkillService.java`（`@Service`）——`#list(cwd)` 汇总所有 provider 的候选后按名字合并、**rank 低者胜**（第 44-61 行）；`#get(name, cwd)` 同规则选优胜候选，再让各 provider 尝试 `load()` 读全文（第 64-81 行）。每次调用都重新扫盘（无缓存生效；第 28 行声明的 `cache` 字段实际未被使用）。
- 数据模型：`SkillCandidate`（name/description/whenToUse/`SkillInvocationPolicy`/source/provider/path/rank/locator）、`SkillDefinition`（含 `content()` 全文）、`SkillSummary`；`SkillInvocationPolicy` 是 `record(modelInvocable, userInvocable)`，filesystem provider 固定 `both()`（`FilesystemSkillProviderPort.java#addCandidate()` 第 187 行）。

### 2.4 注入对话的时机：`skill` 工具（按需，非 system prompt 预注入）

- `domain/tool/skill/SkillTool.java`：`name()="skill"`，参数 schema 只有 `name`（kebab-case）。`#execute()`（第 97-118 行）：`skillService.get(name, cwd)` → 未命中时返回失败并列出前 50 个可用名（第 106-109 行）→ 命中时把正文包成
  `<skill name="..." source="..." path="...">\n<content>\n</skill>\nFollow the skill instructions above for the remainder of this task.`
  作为**工具结果文本块**返回（第 112-117 行）。技能由此进入当轮会话上下文，约束后续步骤——与 Claude Code 的 Skill 工具同构。
- 注册点：`domain/agent/service/run/tool/AgentToolCatalog.java#createRegistry()` 第 189-191 行——`skillService != null` 时把 `new SkillTool(skillService, context.cwd())` 注册进**每个 Agent 的 local registry**（注意 cwd 是 Agent 会话级，因此 project skills 按 cwd 解析）。
- 技能本身**不写进 system prompt**；系统提示词里只有 persona 中的一条使用指引（`config/AgentRunBeanConfig.java#systemPromptAssembler()` 第 114 行："用户要求安装/启用/停用技能……直接使用 extension_skill_install……"）。

### 2.5 技能管理工具（Agent 可自主装卸）

- `domain/tool/extension/ExtensionTools.java`：`extension_skill_list` / `extension_skill_install` / `extension_skill_manage`（以及 `extension_mcp_*` 三个）。由 `infrastructure/adapter/extension/ExtensionAgentToolRegistrar.java`（`ApplicationRunner` `@Order(40)`）在启动时注册进**共享 ToolRegistry**（第 37-45 行），因此对所有 Agent 会话可见——与插件工具同层。
- 安装实现：`infrastructure/adapter/extension/ExtensionManagementService.java#installSkillFromGit()`（第 196 行起）——`git clone --depth 1 <url>` 到临时目录 → 定位 SKILL.md 所在目录（subdir 优先，两层深度自动搜）→ 落到 `<dshHome>/skills/<name>/`（即 USER_DSH 根）。`#setSkillEnabled()`（第 336 行）与启动时 `#run()`（第 102-109 行）通过 `FilesystemSkillProviderPort#setDisabledNames` 热更新停用名单；停用状态持久化在 `<configDir>/extensions.json`（`config/ExtensionBeanConfig.java#extensionConfigStore()`，`harness.config.dir` 默认 `~/.dsh`）。

### 2.6 内置/示例 skill

仓库仅有一个真实技能：`skills/baidu-ai-search/SKILL.md`（frontmatter 带 `description` + `when_to_use`，正文教模型如何调用 `mcp__baidu-ai-search__*` MCP 工具）。它通过 `harness.yml` 的 `harness.extensions.skills.roots: [./skills]` 被发现（CUSTOM, rank 300）。另有 `docs/skills/dsh-java-plugin-skills/SKILL.md`，是文档性质的技能说明（教 AI 助手用 Maven Archetype 生成插件工程），不在默认 roots 内。

---

## 3. 与主流程的接缝（扩展点接口 + 调用点）

### 3.1 接缝总图

```
                                    ┌────────────────────────── 共享 ToolRegistry（单例 Bean）──────────────────────────┐
 SpringPluginContext.registerTool ──▶ PluginToolDefinition("plugin__<id>__<tool>")  ◀── PluginToolBridgeService.bridgePlugin（Node 通道）
 ExtensionAgentToolRegistrar      ──▶ ExtensionTools.Skill*/Mcp* 管理工具
 McpBootstrap.connectAndRegister  ──▶ McpToolAdapter("mcp__<server>__<tool>")
                                    └──────────────────────────────┬───────────────────────────────────────────────┘
                                                                 每次创建 Agent 时
 AgentRunFactory#wireAgent ──▶ AgentToolCatalog#createRegistry(context, sharedToolRegistry)
        │  local: TodoWrite/Shell/Fs*/SkillTool/PluginStatusTool/SubagentTool …（内置工具）
        ▼
 CompositeToolRegistry(local, shared)   lookup: local 优先; all(): shared 先 local 后（local 覆盖同名）
        │
 ReactLoopAgent#resolveToolSchemas() ──▶ ToolRegistry#schemas()（default 方法，all()→ToolSchema 列表）
        │
 ScopedSystemPromptAssembler#assemble(id, options, cwd, tools) ──▶ system prompt（含插件能力段 order=150）+ tools schema
        │
 LLM 返回 tool_call ──▶ ToolCallExecutor#execute ──▶ registry.lookup(name) ──▶ PluginToolDefinition#execute ──▶ bridge.callTool
        │            （前置/后置 Hook：ToolCallExecutor#shouldBlock/#afterTool → CompositeHookService → 插件 Hook）
```

### 3.2 确切扩展点清单

| 扩展点 | 位置（相对 vendors/deepseek-harness-java/） | 方法签名（关键） | 实现方 |
|---|---|---|---|
| Java 插件 SPI | `deepseek-harness-java-types/.../domain/spi/JavaHarnessPlugin.java` | `String pluginId()`; `List<ToolDefinition> tools()`; `void configure(PluginContext)`; `void onStart()`; `void onStop()` | `plugins/sample-tools-plugin/.../SampleToolsPlugin.java` 等 |
| 插件注册边界 | `deepseek-harness-java-types/.../domain/spi/PluginContext.java` | `AutoCloseable registerTool(ToolDefinition)`; `void registerDisposer(AutoCloseable)`; `AutoCloseable subscribe(String, PluginEventHandler)`; `void emit(String, Object)`; `AutoCloseable registerHook(String, PluginHook)`; `Optional<String> getConfig(String)`; `AutoCloseable registerSystemPrompt(String, int, String)` | `infrastructure/.../java/SpringPluginContext.java`（宿主） |
| 工具桥协议 | `deepseek-harness-java-domain/.../tool/plugin/IPluginToolBridge.java` | `CompletableFuture<Void> connect(String, Process)`; `disconnect(String)`; `CompletableFuture<List<PluginToolDescriptor>> listTools(String)`; `CompletableFuture<PluginToolResult> callTool(String, String, Map)` | `InProcessToolBridge`（Java 通道）/ `JsonRpcPluginToolBridge`（Node 通道） |
| 技能提供方端口 | `deepseek-harness-java-domain/.../skill/adapter/port/ISkillProviderPort.java` | `List<SkillCandidate> discover(String cwd)`; `Optional<SkillDefinition> load(SkillCandidate)` | `infrastructure/adapter/skill/FilesystemSkillProviderPort.java` |
| 技能服务 | `deepseek-harness-java-domain/.../skill/ISkillService.java` | `List<SkillSummary> list(String cwd)`; `Optional<SkillDefinition> get(String name, String cwd)` | `domain/skill/service/SkillService.java` |
| 插件登记服务 | `deepseek-harness-java-domain/.../plugin/IPluginRegistryService.java` | `installPlugin(pluginId, displayName, pluginVersion, runtimeType, sourcePath, entrypoint)`; `refreshStatus(entity, bridgeReady)`; `disable/enable/uninstallPlugin` | `.../registry/service/PluginRegistryService.java` |
| 桥接/运行时端口 | `.../plugin/bridge/adapter/port/IPluginRuntimeBridgePort.java`; `.../plugin/runtime/adapter/port/IPluginProcessPort.java`; `.../plugin/registry/adapter/port/IPluginArtifactInstallerPort.java` | `inspect(HarnessPluginEntity)`; `start(HarnessPluginEntity, PluginBridgePlanVO)`; `install(pluginId, sourcePath, entrypoint)` | infrastructure 三个同名 Port 类 |

### 3.3 主流程调用点（何时、何地、何条件）

| 调用点 | 位置 | 条件/时机 |
|---|---|---|
| 共享 ToolRegistry 创建 | `infrastructure/config/AgentRunBeanConfig.java#toolRegistry()` 第 88-90 行 | Spring 启动，单例 `InMemoryToolRegistry` |
| 插件/技能工具进入 Agent | `domain/agent/service/run/AgentRunFactory.java#wireAgent()` 第 161-164 行：`toolCatalog.createRegistry(new AgentToolContext(...), sharedToolRegistry)` | 每次 `AgentRunFactory#create()/resume()`（即每次对话会话建立） |
| SkillTool 注册 | `domain/agent/service/run/tool/AgentToolCatalog.java#createRegistry()` 第 189-191 行 | `skillService != null`（BeanConfig 恒注入） |
| PluginStatusTool 注册 | 同文件第 153 行 | 无条件，模型可随时查插件状态 |
| 工具 schema 下发 | `domain/agent/service/run/ReactLoopAgent.java#step()` 第 545-547 行 `systemPromptAssembler.assemble(id, options, cwd, resolveToolSchemas())`；`#resolveToolSchemas()` 第 938-943 行 `toolRegistry.schemas()` | **每个 LLM step 前**，插件工具动态增删即实时反映 |
| 插件能力提示词段 | `JavaPluginRuntimeManager.java#registerPromptSection()` / `PluginToolBridgeService.java#registerPromptSection()` | 插件 start/bridge 成功且 promptAssembler 是 Scoped 类型且有工具 |
| 插件 Hook 执行 | `domain/tool/service/ToolCallExecutor.java#shouldBlock()` 第 170-180 行（PRE_TOOL_USE，BLOCK/DENY 阻断）、`#afterTool()` 第 184-189 行（POST_TOOL_USE） | 每次工具调用前后；经 `CompositeHookService`（`@Primary IHookService`，infrastructure/.../java/CompositeHookService.java 第 44-62 行）先跑插件 Hook 再跑 shell Hook，`HookOutputMerger` 限制性合并（任一阻断即阻断） |
| 插件启动（对话外） | `app/plugin/PresetPluginLoader.java#run()`；trigger `HarnessPluginCommandController` → case Pipeline → `PluginProcessPort#start()` | 应用启动（Order 20）/ REST 调用 |
| 预置技能根绑定 | `infrastructure/config/HarnessApplicationConfig.java#skillProviderPort()` 第 292-312 行 | Spring 启动，读取 `HarnessExtensionsProperties.Skills` |

**分层归属结论**：插件/技能的**领域模型与规则在 domain 层**（`domain/plugin/*`、`domain/skill/*`、`domain/tool/plugin|skill|extension/*`）；**编排用例在 case 层**（`cases/plugin/*`）；**触发入口在 trigger 层**（HTTP Controller）；**装配与加载技术细节在 infrastructure/app 层**（`JavaPluginLoader` 等 + `PresetPluginLoader`）。主流程（Agent 运行时）消费这些扩展点只用 domain 层接口（`ToolRegistry`、`ISkillService`、`IPluginRuntimeService`、`IHookService`），符合依赖倒置。

---

## 4. 配置来源

### 4.1 配置文件加载链

`deepseek-harness-java-app/src/main/resources/application.yml` 第 33-38 行：`spring.config.import` 引入 `optional:classpath:harness.yml`、`optional:file:./harness.yml`、`optional:classpath:harness-extensions.yml`、`optional:file:./harness-extensions.yml`。插件/技能/MCP 的实际默认值在 `deepseek-harness-java-app/src/main/resources/harness.yml` 第 63-106 行；`harness-extensions.yml` 是部署时的可选覆盖文件（仓库内未提供）。

### 4.2 配置键总表（`@ConfigurationProperties(prefix = "harness.extensions")` → `infrastructure/config/HarnessExtensionsProperties.java`）

| 配置键 | 默认值（Properties 类 / harness.yml） | 读取位置 |
|---|---|---|
| `harness.extensions.skills.enabled` | true | `HarnessExtensionsProperties.Skills` 第 45 行 → `HarnessApplicationConfig#skillProviderPort()` |
| `harness.extensions.skills.project-enabled` | true | 同上 → `FilesystemSkillProviderPort` 构造参数 |
| `harness.extensions.skills.home` | `""`（空则回退 `harness.credentials.home` 即 `${user.home}/.dsh`） | 同上；harness.yml 里是 `${HARNESS_SKILL_HOME:}` |
| `harness.extensions.skills.bundled-dir` | `""`（空则不扫 bundled） | 同上；harness.yml 里是 `${HARNESS_BUNDLED_SKILLS:}` |
| `harness.extensions.skills.roots` | `[]`（harness.yml 给 `["./skills"]`） | 同上 → customRoots |
| `harness.extensions.plugins.install-root` | `"./plugins"`（Properties 第 189 行；harness.yml 同值） | `JavaPluginRuntimeManager` 构造器第 80 行、`PluginArtifactInstallerPort` 构造器第 36 行、`PluginSidecarGatewayService` 构造器第 39 行 |
| `harness.extensions.plugins.node-bridge-enabled` | true | `PluginSidecarGatewayService` 构造器第 38 行 |
| `harness.extensions.plugins.preset[].plugin-id/display-name/plugin-version/runtime-type/source-path/entrypoint/auto-start(auto-enable)` | `[]`（harness.yml 预置了 dsh-demo-plugin 与 sample-tools 两条） | `app/plugin/PresetPluginLoader.java#run()` 第 69-77 行 |
| `harness.extensions.mcp.enabled/servers[]` | true / `[]`（harness.yml 预置 baidu-ai-search SSE server） | `ExtensionManagementService`/`McpRuntime`（MCP 不在本报告展开范围） |
| `harness.config.dir` | `${user.home}/.dsh` | `ExtensionBeanConfig.java#extensionConfigStore()` 第 33 行 → `extensions.json`（技能停用名单等运行期扩展状态） |

环境变量仅作为 yml 占位符的上层来源：`HARNESS_SKILL_HOME`、`HARNESS_BUNDLED_SKILLS`（harness.yml 第 67-68 行）。**技能目录没有硬编码**——连 `./skills` 都是 yml 值；**插件 install-root 默认值 `./plugins` 是代码硬编码兜底**（`HarnessExtensionsProperties.java` 第 189 行）。

---

## 5. 端到端实例

### 5.1 Java 插件 sample-tools：从磁盘到对话生效

**（a）磁盘工件**（示例/内置插件，非机制代码）

- 源码：`plugins/sample-tools-plugin/src/main/java/cn/walioffice/plugin/sample/SampleToolsPlugin.java` —— `extends AbstractHarnessPlugin`，构造 `super("sample-tools")`；`#tools()` 返回 `[WeatherQueryTool, FileSummaryTool]`（各自 `extends AbstractTool`）；`#configure(context)` 里 `registerSystemPrompt("capabilities",10,…)`、`subscribe("tool.called",…)`、`registerHook("PRE_TOOL_USE", …)`（只审计 `shell_execute`，读配置 `audit-enabled`）、`registerHook("POST_TOOL_USE", …)`（对自己工具 emit 事件）、`emit("plugin.custom-event", greeting)`。
- 打包声明：`plugins/sample-tools-plugin/src/main/resources/META-INF/plugin.yaml`（`id: sample-tools`，`entrypoint: cn.walioffice.plugin.sample.SampleToolsPlugin`）+ `META-INF/services/cn.xiaofuge.deepseek.harness.domain.spi.JavaHarnessPlugin`（同名类，ServiceLoader 回退通道）。
- 成品：`plugins/sample-tools-plugin-1.0.0.jar`（另有 `sample-tools.jar`、`mall-weekend-assistant.jar` 两个旧制品）；构建脚本 `scripts/package-jar.sh`，一键安装脚本 `scripts/install-plugin.sh`（读 JAR 内 plugin.yaml → curl `POST /api/harness/plugins/install` + `/run`）。

**（b）注册表配置**

`deepseek-harness-java-app/src/main/resources/harness.yml` 第 100-106 行：

```yaml
- plugin-id: sample-tools
  runtime-type: JAVA_NATIVE
  source-path: ./plugins/sample-tools-plugin-1.0.0.jar
  entrypoint: sample-tools-plugin-1.0.0.jar
  auto-start: true
```

**（c）启动安装（discovery=配置驱动，非扫描）**

`PresetPluginLoader.java#run()` → `#installPreset()`：`pluginRegistryService.loadPluginOptional("sample-tools")` 为空 → 构造 `InstallPluginRequestDTO` → `InstallHarnessPluginCaseImpl#execute()` → `InstallPluginNode#doApply()` → `PluginRegistryService.java#installPlugin()`：runtimeType=`JAVA_NATIVE` → mode=`NATIVE` → `PluginArtifactInstallerPort.java#installJar()` 把 JAR 复制为 `./plugins/sample-tools.jar`（installRoot + pluginId + ".jar"，第 83-88 行）→ `HarnessPluginRepository#save()` 落表 `harness_plugin_installation`（status=`REGISTERED`）。

**（d）启动运行**

`#installPreset()` 继续 `runCase.execute(RunPluginRequestDTO("sample-tools"))` → `RunHarnessPluginCaseImpl#execute()` → `ResolvePluginNode`（DB 读实体）→ `InspectBridgeNode` → `PluginBridgeService#inspect()` → `PluginRuntimeBridgePort.java#inspectJavaNative()`：`resolveJarPath("./plugins/sample-tools.jar", "sample-tools-plugin-1.0.0.jar")` 第一步命中（source 本身是 .jar）→ `PluginBridgePlanVO(bridgeReady=true, runtimeCommand="java-in-process:sample-tools")` → `BindBridgeNode`：`PluginRegistryService#refreshStatus()` 落 ACTIVE + `recordBinding()` 落 `harness_plugin_runtime_binding` → `RunPluginNode` → `PluginRuntimeService#start()` → `PluginProcessPort.java#startJavaPlugin()` → `JavaPluginRuntimeManager.java#start("sample-tools", ./plugins/sample-tools.jar)`。

**（e）加载与贡献注册**

`JavaPluginRuntimeManager#start()` 第 110-131 行：`SpringPluginContext` → `JavaPluginLoader#load()`：URLClassLoader → 读 plugin.yaml → entrypoint 反射实例化 → `onStart()` → `tools()` 取 2 个工具 → `SampleToolsPlugin#configure(context)`：
- `super.configure()` → `context.registerTool(weather_query)` → `SpringPluginContext#registerTool()` 包 `InProcessToolBridge` → `PluginToolDefinition` 名为 **`plugin__sample-tools__weather_query`**（`PluginToolDefinition.java#name()`）→ 共享 `ToolRegistry#register()`；`file_summary` 同理；
- `context.registerSystemPrompt("capabilities",10,…)` → 段名 `plugin__sample-tools__capabilities` 进 `ScopedSystemPromptAssembler`；
- Hook 进 `InProcessPluginHookRegistry`；`getConfig("audit-enabled")`/`getConfig("greeting")` 走 `DatabasePluginConfigStore` → `harness_plugin_config` 表。

随后 `registerPromptSection()` 追加宿主自动能力段（order=150，列出两个工具全名），状态 `ACTIVE`，`hotReloader.watch()` 登记 JAR，发布 `plugin.started`。

**（f）对话请求中生效**

1. 前端 `POST /api/agent/message`（或 `/api/agent/stream`，`trigger/http/AgentController.java` 第 29-35 行）→ 最终进入 `AgentRunFactory.java#wireAgent()` 第 161-164 行，把共享 ToolRegistry（此时已含 2 个插件工具）与 local registry（含 `skill`、`plugin_status` 等）合成 `CompositeToolRegistry`。
2. `ReactLoopAgent.java#step()`（第 540-547 行）：`resolveToolSchemas()` → `toolRegistry.schemas()`（`ToolRegistry.java#schemas()` default 方法第 50-54 行）→ LLM 的 tools 数组里出现 `plugin__sample-tools__weather_query`；system prompt 里出现两段 sample-tools 能力说明。
3. 模型决定调用 `plugin__sample-tools__weather_query{"city":"北京"}` → `ToolCallExecutor#execute()` → `CompositeToolRegistry#lookup()`（local 未命中 → shared 命中）→ `PluginToolDefinition#execute()`（第 83-103 行）→ `InProcessToolBridge#callTool()` → `WeatherQueryTool#execute()` → 内容块回写会话。
4. 前置/后置 Hook：`ToolCallExecutor#shouldBlock()`/`#afterTool()` → `CompositeHookService#runHooks()` → `InProcessPluginHookRegistry#runHooks()` 命中 sample-tools 的 PRE Hook（仅 `shell_execute` 时返回审计 context）与 POST Hook（对自家工具 emit `tool.called` → 它自己 `subscribe` 的回调打日志）。
5. 若模型调用 `shell_execute`：PRE Hook 返回 `PluginHookResult.context("Audit notice: …")`，经 `toHookOutput()`（非 BLOCK/DENY → allowed=true）→ `HookOutputMerger.merge()` 合并为"放行 + 附加上下文"。

### 5.2 技能 baidu-ai-search：从磁盘到对话生效

1. **磁盘**：`skills/baidu-ai-search/SKILL.md`，frontmatter `description`/`when_to_use`，正文是"如何用 `mcp__baidu-ai-search__*` 工具做检索"的指令集。
2. **配置绑定**：`harness.yml` 第 63-70 行 `harness.extensions.skills.roots: ["./skills"]` → `HarnessApplicationConfig.java#skillProviderPort()` 构造 `FilesystemSkillProviderPort(dshHome=~/.dsh, bundledDir=null, "filesystem", [./skills], true, true)` → 作为 `ISkillProviderPort` 注入 `SkillService`。
3. **工具注册**：每次创建 Agent，`AgentToolCatalog.java#createRegistry()` 第 190 行注册 `SkillTool(skillService, cwd)`（cwd 为会话工作目录）。
4. **对话中**：模型看到 `skill` 工具（描述：按 kebab-case 名加载技能指令集）→ 判断用户需要最新网络信息 → 调 `skill{"name":"baidu-ai-search"}` → `SkillTool#execute()` 第 104 行 `skillService.get("baidu-ai-search", cwd)` → `SkillService.java#get()`：校验 kebab-case → `discoverAll()` 调 `FilesystemSkillProviderPort#discover()` → `#discoverRoot("./skills")` 命中目录束 `baidu-ai-search/SKILL.md`（CUSTOM, rank 300）→ `#load()` 读文件、`#parseSkill()` 剥 frontmatter → `SkillDefinition(content=正文)` → `SkillTool` 包 `<skill name="baidu-ai-search" source="CUSTOM" path="…">` 返回 → 技能指令进入上下文，模型随后按其指引调用 MCP 工具（MCP server 由 `harness.extensions.mcp.servers` 预置，`McpBootstrap` 以 `mcp__<server>__<tool>` 名注册进同一共享 ToolRegistry，`domain/tool/mcp/McpBootstrap.java` 第 13、76 行）。
5. **管理面**：模型或前端可用 `extension_skill_list` 列出它；`extension_skill_manage{action:disable}` → `ExtensionManagementService#setSkillEnabled()` → `setDisabledNames` → 下一次 `discover()` 即过滤，无需重启。

### 5.3 Node 通道示例 dsh-demo-plugin（诚实记录实际行为）

`harness.yml` 第 92-99 行预置 `plugin-id: dsh-demo-plugin`（`DSH_NODE_BRIDGE`，source `./plugins/demo-plugin`，entrypoint `index.js`）。链路：`PluginArtifactInstallerPort#installDirectory()` 整树复制 → `PluginRuntimeBridgePort#inspectDshPackage()` 读 `package.json` 的 `main: index.js` → bridgeReady=true → `PluginSidecarGatewayService#start()` 拉起 `node <installRoot>/dsh-demo-plugin/index.js`。但 `plugins/demo-plugin/index.js` 只有 `console.log + setInterval`，**没有实现 JSON-RPC `initialize`/`tools/list`**，因此 `JsonRpcPluginToolBridge#connect()` 的握手会超时，`PluginToolBridgeService#bridgePlugin()` 抛异常，`PluginProcessPort#start()` 第 56-59 行随即 stop 子进程并向上传递失败——该示例演示的是"Node 通道生命周期编排"，不是可用的工具插件；它也证明了 Node 通道的失败回滚路径确实会执行。

---

## 6. 复刻视角的机制要点（供课程拆解参考）

- **三组接缝、一个汇点**：插件工具、技能工具、管理工具最终全部汇入 `ToolRegistry` 单例，由 `AgentToolCatalog#createRegistry()` + `CompositeToolRegistry` 在 Agent 创建时织入，`ReactLoopAgent` 每 step 拉取 schemas——复刻时只要复刻"可增删的 ToolRegistry + 每 step 重建 schema"这一条，三条扩展通道就都有了挂点。
- **生命周期与贡献注册分离**：`JavaPluginLoader`（类加载/实例化）与 `JavaPluginRuntimeManager`（装配/状态/热重载）职责切开，`SpringPluginContext` 用 `AutoCloseable` 列表做贡献项事务——这是"启动失败不留悬挂工具"的关键设计。
- **状态双轨**：登记状态（MySQL，声明式意图）与运行时状态（内存 runningPlugins，真实进程/类加载器）分离，靠 `PresetPluginLoader#reconcilePersistedPlugins()` 启动对账、`BindBridgeNode#refreshStatus()` 运行期对齐。
- **skills 刻意"零机制"**：无注册 API、无持久化注册表、无热加载器——文件系统即注册表，每次调用即发现；唯一的运行期状态是停用名单。与插件的重机制形成鲜明对比，适合作为课程中"轻扩展 vs 重扩展"的对照案例。

## 附录：vendors/dsh-java-plugin-skills 如何消费这些机制

该 submodule（`vendors/dsh-java-plugin-skills`，HEAD 2dc1440）是一个**面向 AI 编程助手的技能包**：`SKILL.md` frontmatter 完全符合本报告 §2.1 的 filesystem 约定（name/description，触发词写在 description 里），被发现后会指导助手完成"创建 DSH Java Native 插件工程"等任务。其 `references/` 沉淀插件开发规范，`scripts/install_plugin.sh` 等脚本封装的是 §1.5 的 REST 管理面（`POST /api/harness/plugins/install` + `/run`），`runtime/deepseek-harness-java-app.jar` 则是宿主可执行件。也就是说，它同时消费了两套机制：**以 skill 形态注入工作流**（`ISkillProviderPort` 发现 → `skill` 工具加载），**以插件形态产出可安装工件**（`JavaHarnessPlugin` SPI + plugin.yaml + REST 安装链路），从外部验证了扩展点确实可脱离 harness 仓库独立挂接。harness 仓库内 `docs/skills/dsh-java-plugin-skills/SKILL.md` 是它的一个文档快照。
