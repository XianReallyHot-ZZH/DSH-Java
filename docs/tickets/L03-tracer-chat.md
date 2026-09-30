# L03 阻塞式对话 tracer（S03）

- 前置：L02（tag L02）
- 大纲节：docs/lessons/README.md#L03
- 术语：Agent / 驱动循环 / 回合 / 步 / LLM 端口 / 协议路由 / 策略树

## 开工前
- 向用户确认 `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_DEFAULT_MODEL` 已在环境中配置（勿索取明文）。

## vendor 精读清单
- `api/dto/AgentMessageRequestDTO.java` + `AgentMessageResponseDTO.java`（字段全集——对拍基准）
- `trigger/.../http/AgentController.java#sendMessage()`；`trigger/.../service/AgentApi.java#execute()`（异常→信封收敛）
- `cases/agent/AgentUseCase.java` + `cases/orchestration/CasePipeline.java`（本课只搭管线骨架，节点最小实现）
- `domain/.../agent/service/run/ReactLoopAgent.java` 的 `send()/kick()/turn()/step()`（本课交付单 turn 单步子集，保留多步扩展点）
- `domain/.../agent/service/run/AgentRunFactory.java`（最小装配）
- `infrastructure/.../adapter/llm/InMemoryLlmRuntimePort.java`（端口注册与错误包装）+ `deepseek/DeepSeekAdapter.java#doStream()/doNonStreamFallback()` + `OpenAiCompatibleUriResolver`

## 实现增量
- 做：HTTP→Api→UseCase→策略树→ReactLoopAgent(单步)→LLM 端口→DeepSeek 适配器全链；流式内部消费为阻塞结果；harness.yml 的 llm 段接入。
- 不做：多轮/事件日志/Inbox 完整语义（L04）、SSE（L05）、工具（L06）、压缩/意图（L08）。

## 测试搬运
- 新增脚本化 fake 适配器（按 vendor 测试风格，不外呼）覆盖：正常回复、上游 5xx、空流降级非流式。

## 验收（DoD 专项）
- [ ] 真端点 `curl POST /api/agent/message` 返回模型文本，信封 `00000`
- [ ] 请求/响应 DTO 字段集与 vendor 逐一对齐（对拍点，记录进 lesson 文档）
- [ ] 上游异常收敛为 `50000` 不裸抛

## 教学文档与配图
- `docs/lessons/L03-tracer-chat.md`；mermaid：本课链路时序（对照报告 01 §3.1 前 15 跳的子集）。

## 教学点
- tracer bullet 的意义：一条竖切打穿六边形；端口-适配器在 LLM 上的第一次真实运用。
