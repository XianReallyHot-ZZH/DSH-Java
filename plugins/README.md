# plugins/

插件工程目录占位（L01）。vendor 在此存放两个子模块，复刻件随课加入根 `pom.xml` 的 `<modules>`：

| 子模块 | 引入课 | 用途 |
|--------|--------|------|
| `sample-tools-plugin` | L14 插件机制核心 | 示例插件 JAR，构建时复制到本目录供 preset 预装对账 |
| `deepseek-harness-plugin-archetype` | L16 插件工程化 | Maven archetype 脚手架 |

目录同时是运行期插件 JAR 的落点（`plugin.yaml` 清单、热重载监视范围，见 CONTEXT.md「插件」条目）。
