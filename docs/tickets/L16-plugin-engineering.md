# L16 插件工程化（S14）

- 前置：L15（tag L15）
- 大纲节：docs/lessons/README.md#L16
- 术语：插件 / 插件上下文 / 登记状态/运行时状态

## vendor 精读清单
- `cases/plugin/analysis/PluginArtifactAnalyzerService.java`（JAR 解析与 Maven 依赖候选发现）
- `trigger/.../http/HarnessPluginAnalysisController.java`（analyze-jar / analyze-maven）
- `infrastructure/.../adapter/plugin/java/DatabasePluginConfigStore.java`（DEFAULT/OVERRIDE 两层）+ `HarnessPluginConfigCommand/QueryController`
- `cases/plugin/PluginInventoryQueryFactory.java` + `HarnessPluginQueryController`（inventory/status 完整元数据）
- `infrastructure/.../adapter/plugin/java/PluginHotReloader.java`（ApplicationReadyEvent 后 WatchService daemon，只匹配已登记 .jar）
- `plugins/deepseek-harness-plugin-archetype/`（archetype-metadata.xml 与 archetype-resources）

## 实现增量
- 做：产物分析两端点、插件配置持久化与 REST、inventory/status 完整化、热重载（WatchService ENTRY_MODIFY/CREATE）、archetype 脚手架模块。
- 不做：Node/Codex/Cordis 包类型的完整识别语义（L17 补 Node）。

## 测试搬运
- `DatabasePluginConfigStoreTest`（改包名）。

## 验收（DoD 专项）
- [ ] analyze-jar 传 JAR 路径返回候选 DTO（用 sample 插件 JAR 与一个无清单 JAR 各验一次）
- [ ] 插件配置 setDefaults/setOverride 后重启仍在；插件侧 getConfig 读到
- [ ] `GET /api/harness/plugins/inventory` 含安装态/入口/来源/作者/scope
- [ ] 触碰已登记 JAR 触发热重载（工具短暂消失再回来）

## 教学文档与配图
- `docs/lessons/L16-plugin-engineering.md`；mermaid：插件工程化全景（分析→安装→配置→热重载生命周期环）。

## 教学点
- 两层配置（DEFAULT/OVERRIDE）的覆盖语义；热重载只看已登记路径——安全边界。
