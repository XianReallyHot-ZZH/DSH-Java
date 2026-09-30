# L18 MCP 工具适配（S16 + S19f）

- 前置：L17（tag L17）
- 大纲节：docs/lessons/README.md#L18
- 术语：MCP / 共享工具表 / 工具目录

## vendor 精读清单
- `domain/.../tool/mcp/McpBootstrap.java`（L76 注册语义）、`McpServerConfig.java`、`McpToolAdapter.java`、`IMcpClient.java`
- `infrastructure/.../adapter/mcp/StdioMcpClient.java`（291 行）、`HttpMcpClient.java`（325 行，SSE/streamable-http）
- `infrastructure/.../config/McpBootstrapConfig.java`；`harness.yml` 的 `harness.extensions.mcp.*`
- `infrastructure/.../adapter/extension/ExtensionManagementService.java`：`upsertMcpServer()/testMcpServer()/connectedMcpServers()`
- `trigger/.../http/command/ExtensionCommandController.java` + `http/query/ExtensionQueryController.java`（MCP 区段）

## 实现增量
- 做：MCP bootstrap 三传输客户端、`mcp__<server>__<tool>` 注册进共享表、servers CRUD/test REST、启动自动连接、前端扩展面板 MCP 区。
- 不做：Skills（L19）；extension_skill_* 工具（L19）。

## 测试搬运
- `McpServerConfigTest`、`StdioMcpClientTest`、`HttpMcpClientTest`（改包名）。

## 验收（DoD 专项）
- [ ] 配置一个 stdio MCP server（用本地脚本 mock 或公开 echo server）启动后 `connected`
- [ ] 对话可调 `mcp__<server>__<tool>`；增删 server 运行期生效
- [ ] `POST .../mcp/servers/test` 连通性结果正确（通/断两态）

## 教学文档与配图
- `docs/lessons/L18-mcp.md`；mermaid：三传输→IMcpClient→Bootstrap→共享工具表汇入图（对照报告 03 §3.1）。

## 教学点
- MCP 工具与插件工具在注册表里的同构性——都汇入同一个共享 ToolRegistry，Agent 每 step 拉 schema 即时可见。
