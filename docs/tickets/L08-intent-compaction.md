# L08 意图识别与上下文工程（S08）

- 前置：L07（tag L07）
- 大纲节：docs/lessons/README.md#L08
- 术语：意图 / 压缩 / 受控续写 / 系统提示词装配（见 CONTEXT.md「意图」等条）

## vendor 精读清单
- `cases/agent/node/AgentIntentNode.java#classify()`（L101-136，8 个正则 Pattern；`[no-tools]` 前缀）
- `cases/agent/node/AgentDispatchNode.java#prepareMessageText()`（L152-199：目录任务→ls 指令、`@插件`提及转指令）与图片转 ImageBlock（L76-111）
- `domain/.../agent/service/SystemPromptAssembler.java` + `ScopedSystemPromptAssembler.java#assemble()`（段落与 order）
- `infrastructure/.../config/AgentRunBeanConfig.java#systemPromptAssembler()`（L98-152：人设/工具规则文案——照抄语义，包名替换）
- `domain/.../agent/service/compaction/BasicCompactionEngine.java`（pressure 阈值、保留最近 1/4、min-messages）
- `ReactLoopAgent.java` 的 `runCompaction()`（L514-526）、max_tokens 受控续写（L459-480）、`truncateToBudget()`（L818-848，128K 估算裁剪）

## 实现增量
- 做：意图分类 + 消息改写 + 图片块、提示词装配器（段落 order 机制，为插件段 L14 预留）、压缩引擎、续写 ≤4 次、预算裁剪。
- 不做：压缩用的总结模型单独渠道（用当前渠道）。

## 测试搬运
- `AgentIntentNodeTest`（全部用例）；新增：压缩触发与摘要事件、续写拼接完整性测试（fake LLM 编排截断）。

## 验收（DoD 专项）
- [ ] 闲聊消息不触发工具；`[no-tools]` 前缀跳过工具
- [ ] 构造超阈值长会话（fake 大输出）触发压缩并出现摘要事件；压缩后上下文延续不断裂
- [ ] 截断回复自动续写 ≤4 次拼接完整

## 教学文档与配图
- `docs/lessons/L08-intent-compaction.md`；mermaid：token 预算时间线（增长→压缩→续写）。

## 教学点
- 正则意图分类的克制（不引第二个模型）；提示词段 order 机制是插件能力的地基。
