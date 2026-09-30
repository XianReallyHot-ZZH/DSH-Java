# L14 插件机制核心（S13a）

- 前置：L13（tag L13）
- 大纲节：docs/lessons/README.md#L14
- 术语：插件 / JAVA_NATIVE 通道 / 插件 SPI / 插件清单 / 插件上下文 / 插件生命周期 / 共享工具表

## vendor 精读清单
- **types 模块全套**（`deepseek-harness-java-types/.../domain/spi/`）：`JavaHarnessPlugin`、`AbstractHarnessPlugin`、`PluginContext`（L48-52 HookPoint 注释）、`PluginManifest`、`PluginLifecycleState`、`PluginEventBus/PluginEvent/PluginEventHandler`、`PluginHook/PluginHookResult`、`PluginConfigStore`；`model/entity/AbstractTool` + valobj
- `infrastructure/.../adapter/plugin/java/JavaPluginLoader.java`：`load()`（URLClassLoader L62-65）、`readManifest()`（L105-124）、`instantiate()`（entrypoint 优先/ServiceLoader 恰好一个，L131-164）、`unload()`（L92-99）
- `infrastructure/.../adapter/plugin/java/JavaPluginRuntimeManager.java`：`start()` 九步（L96-141）、`stop()` 逆序（L171-184）、`registerPromptSection()`（order=150）、`rollbackQuietly`
- `infrastructure/.../adapter/plugin/java/SpringPluginContext.java`：`registerTool()`（L62-75 包 InProcessToolBridge→PluginToolDefinition）、`close()` 逆序回收（L165-179）
- `domain/.../tool/plugin/PluginToolDefinition.java#name()`（`plugin__<id>__<tool>` 命名）
- 示例：`plugins/sample-tools-plugin/`（源码 + META-INF/plugin.yaml + services 文件 + 打包复制配置）

## 实现增量
- 做：types SPI 全量（本课最重交付）、Loader/RuntimeManager/SpringPluginContext/InProcessToolBridge、plugin__ 命名与提示词能力段、sample 插件在本仓库从源码构建并随 app 复制；用 L02 的最小 case 链路直接启动插件（管理面 REST 是 L15）。
- 不做：插件管理 REST/pipeline/落库/preset（L15）、Node 通道（L17）、Hook 注册通道接插件（本课 registry 就位即可）。

## 测试搬运
- `SpringPluginContextTest`（改包名）；新增：清单缺失必填字段的无效判定、ServiceLoader 多候选拒绝、卸载后工具表无残留。

## 验收（DoD 专项）
- [ ] 启动预装 sample 插件：对话可调 `plugin__sample-tools__weather_query`，系统提示词出现能力段（order=150）
- [ ] 插件 configure 里 registerSystemPrompt/subscribe/registerHook 均生效（Hook 走 L13 引擎）
- [ ] stop 后工具/提示词/Hook 全部回收无悬挂；start 中途失败回滚干净

## 教学文档与配图
- `docs/lessons/L14-plugin-core.md`；mermaid：插件 start 九步时序 + PluginContext 事务边界图。

## 教学点
- 类加载器父子方向（插件看得见宿主 types、宿主看不见插件）；AutoCloseable 注册列表 = 「失败不留悬挂」的关键设计。
- 拉伸练习见工单 README（dsh-java-mysql 插件前缀替换外部验证）。
