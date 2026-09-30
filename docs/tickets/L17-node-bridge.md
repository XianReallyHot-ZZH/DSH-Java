# L17 Node Bridge 通道（S15）

- 前置：L16（tag L16）
- 大纲节：docs/lessons/README.md#L17
- 术语：DSH_NODE_BRIDGE 通道 / 工具执行器

## vendor 精读清单
- `infrastructure/.../adapter/plugin/JsonRpcPluginToolBridge.java`（initialize/tools/list/tools/call + notifications/initialized + 60s 超时；Bean 定义在 `HarnessApplicationConfig.java#jsonRpcPluginToolBridge()`）
- `infrastructure/.../adapter/plugin/gateway/PluginSidecarGatewayService.java`（L45-85：nodeBridgeEnabled 开关、installRoot 边界、入口 .js/.mjs/.cjs 白名单、stderr 落 runtime-logs）
- `infrastructure/.../adapter/port/PluginProcessPort.java`（start 双通道分叉 L47-61；桥接失败立即 stop 重抛；stop 先 unbridge L90-98）
- `infrastructure/.../adapter/port/PluginRuntimeBridgePort.java#inspectNodeBridge()`（package.json→DSH_PACKAGE ready；.codex-plugin→CODEX 不 ready；cordis.yml→CORDIS 不 ready）
- `domain/.../tool/plugin/PluginToolBridgeService.java#bridgePlugin()`（L54-91：connect→listTools→注册→提示词段；失败 unbridge）
- `domain/.../plugin/bridge/`（包识别）+ `plugins/demo-plugin/index.js`（**故意不实现握手**）

## 实现增量
- 做：JSON-RPC 桥、sidecar 网关、进程端口双通道分叉、Node 包识别、demo 插件源码、harness.yml 预置 dsh-demo-plugin。
- 不做：真实可用的 Node 工具插件（vendor 的 demo 本身就不可用——如实复刻）。

## 测试搬运
- 新增：握手超时→失败回滚（子进程被回收、状态 FAILED、工具无残留）测试；用一个本地实现握手的 stub Node 脚本验证 tools/list→tools/call 全链（本机有 Node 才跑，CI 跳过）。

## 验收（DoD 专项）
- [ ] demo 插件：inspect ready → start 拉起 node → 握手超时 → 回滚 stop，状态 FAILED，可重新 activate
- [ ] stub Node 脚本（实现三方法）经完整链路注册工具并可被模型调用
- [ ] Node 不存在/被禁用时 FAILED 且报因清晰

## 教学文档与配图
- `docs/lessons/L17-node-bridge.md`；mermaid：宿主↔sidecar JSON-RPC 序列图（成功与超时回滚两分支）。

## 教学点
- 「示例演示的是生命周期编排而非可用插件」——诚实复刻失败路径本身就是测试；进程边界 vs 类加载器边界的对照。
