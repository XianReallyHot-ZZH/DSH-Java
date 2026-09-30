# L15 插件管理面与预装对账（S13b + S19e）

- 前置：L14（tag L14）
- 大纲节：docs/lessons/README.md#L15
- 术语：登记状态/运行时状态 / 策略树 / 插件生命周期

## vendor 精读清单
- `trigger/.../http/command/HarnessPluginCommandController.java` + `http/query/HarnessPluginQueryController.java`——**REST 契约对拍基准**
- `cases/plugin/`：`InstallHarnessPluginCaseImpl`、`RunHarnessPluginCaseImpl` + `PluginRuntimePipelineFactory`（L48-61 五节点链：Resolve→InspectBridge→BindBridge→Activate/Run 分叉）
- `domain/.../plugin/registry/service/PluginRegistryService.java`：`installPlugin()`（L53-97，pluginId 字符集校验）、`refreshStatus()`（L124-132 状态汇合点）、`uninstallPlugin()`
- `domain/.../plugin/bridge/service/PluginBridgeService.java` + `infrastructure/.../adapter/port/PluginRuntimeBridgePort.java#inspectJavaNative()`
- 三张表 + dao：`harness_plugin_installation`(schema.sql L30)、`harness_plugin_runtime_binding`(L42)、`harness_plugin_config`(L53)
- `app/plugin/PresetPluginLoader.java`：`run()` 对账（L131-163）、`installPreset()`（L90-121）
- `domain/.../tool/plugin/PluginToolBridgeService.java`（进程内桥也走此服务落表）

## 实现增量
- 做：插件 REST 全链、五节点 pipeline、三表持久化、登记/运行时双轨状态与 refreshStatus 汇合、preset 预装 + 启动对账复活、前端插件面板（列表/状态/启停）。
- 不做：analyze（L16）、Node 桥接 inspect 分叉（L17 起补）。

## 测试搬运
- `PresetPluginLoaderTest`、`PluginInventoryServiceTest`（inventory 部分本课先就位查询，完整元数据 L16）。

## 验收（DoD 专项）
- [ ] **对拍**：插件 REST 路由与响应契约一致（记录进 lesson 文档）
- [ ] install→activate→run→disable→uninstall 全链 REST 可用；uninstall 清文件且路径越界被拒
- [ ] 重启后：ACTIVE 插件对账自动复活；登记 ACTIVE 但运行时缺失的被拉起；DISABLED 不复活

## 教学文档与配图
- `docs/lessons/L15-plugin-management.md`；mermaid：登记状态×运行时状态双轨对账矩阵 + 五节点链。

## 教学点
- 双轨状态为什么必须分离（声明意图 vs 进程事实）；对账方向（运行时纠正登记）。
