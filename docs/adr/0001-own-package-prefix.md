# 复刻件采用自有包名（前缀替换）

复刻 deepseek-harness-java 时，不沿用原包名，而是做纯前缀替换：`cn.xiaofuge.deepseek.harness.*` → `io.github.xianreallyhotzzh.dsh.*`，前缀之后的包结构、类名、artifactId 与 vendor 完全一致（groupId 用自有坐标区分）。选自有包名而非原名，是为了让复刻件保有「这是我的工程」的身份、避免与 vendor 源码混淆；代价由以下后果显式承接。

## Consequences

- 与 vendor 的对拍只能走**契约级**（REST 路由与响应信封、SSE 帧序列、schema.sql、会话事件形态），不能做文件级 diff；搬运代码与测试时用机械查找替换改包名。
- 编译期绑定 vendor `cn.xiaofuge` types 包的第三方插件 JAR（如 dsh-java-mysql 插件）**装不进本复刻件**；课程内的插件验收一律用本仓库从源码构建的 sample 插件。
- 事后改回原包名需改写全部 872 个文件的 package/import，成本高昂——本决定事实上不可逆。

## Considered Options

- **与 vendor 完全同包名**（被拒）：文件级 diff 与测试零改动搬运最方便，但复刻件失去独立身份，双工程并读时极易混淆。
