# L19 Skills 系统（S17）

- 前置：L18（tag L18）
- 大纲节：docs/lessons/README.md#L19
- 术语：技能 / 技能根 / 技能（对照：插件——轻重扩展对比）

## vendor 精读清单
- `infrastructure/.../adapter/skill/FilesystemSkillProviderPort.java`：`discover()`（L95-117）、`discoverRoot()`（L150-174 目录束/扁平两形态）、`parseSkill()`（L201-230 只认 description/when_to_use）、`addCandidate()`（invocationPolicy=both）
- `domain/.../skill/service/SkillService.java`：四根 rank（PROJECT_DSH 100/CUSTOM 300/USER_DSH 400/BUNDLED 600）、`list()/get()` rank 低者胜、`SKILL_NAME` kebab-case 强校验、未用上的 cache 字段（如实复刻）
- `domain/.../tool/skill/SkillTool.java#execute()`（L97-118：未命中列前 50 名；命中包 `<skill>` 进工具结果）
- `domain/.../tool/extension/ExtensionTools.java`（extension_skill_list/install/manage）+ `infrastructure/.../adapter/extension/ExtensionAgentToolRegistrar.java`（ApplicationRunner @Order(40) 注册进共享表）
- `ExtensionManagementService.java`：`installSkillFromGit()`（L196 起 clone --depth 1）、`installSkillFromZip()`、`setSkillEnabled()` + `extensions.json` 停用持久化
- `harness.yml` skills 配置段 + 示例 `skills/baidu-ai-search/SKILL.md`

## 实现增量
- 做：filesystem provider、SkillService 合并、SkillTool 按 Agent cwd 注册（进 local registry）、extension_skill_* 三工具、git/zip 安装与停用热生效、示例 skill、前端扩展面板 Skills 区。
- 不做：缓存（vendor 未用，如实省略）。

## 测试搬运
- `FilesystemSkillProviderPortTest`（改包名）。

## 验收（DoD 专项）
- [ ] 目录束与扁平两形态都被发现；同名技能 rank 低者胜
- [ ] 对话中 `skill{"name":"..."}` 加载正文进上下文（工具结果含 `<skill>` 包裹）；未命中返回可用名列表
- [ ] 停用后即时消失（无重启）；技能正文不进系统提示词
- [ ] 非 kebab-case 名被拒

## 教学文档与配图
- `docs/lessons/L19-skills.md`；mermaid：四根发现→rank 合并→按需加载链路；「轻扩展 vs 重扩展」对照表（skill vs plugin）。

## 教学点
- 「文件系统即注册表」的克制设计；与插件的重机制形成课程中最鲜明的一组对照。
