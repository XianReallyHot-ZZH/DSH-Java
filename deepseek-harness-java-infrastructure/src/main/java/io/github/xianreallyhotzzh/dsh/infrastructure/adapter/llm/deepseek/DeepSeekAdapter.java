package io.github.xianreallyhotzzh.dsh.infrastructure.adapter.llm.deepseek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.xianreallyhotzzh.dsh.domain.channel.service.OpenAiCompatibleUriResolver;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.LlmAdapter;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.FinishReason;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.GenerateOptions;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.LlmErrorCodes;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.LlmFailure;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.TokenUsage;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ContentBlock;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.ReasoningBlock;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DeepSeek LLM 适配器，兼容 OpenAI 协议。
 * <p>
 * 通过 HTTP POST 调用 {@code /chat/completions}，接收 SSE 流式分片并转换为
 * 领域 {@link StreamChunk}。异常一律收敛为 Error Finish 分片，不向订阅方抛出。
 * <p>
 * 与 vendor 的差异（后续课收敛）：工具调用序列化与 tool_calls 分片解析（L06）、
 * 图片多模态消息（L07）、上游模型目录发现与请求模型回退（L11）、重试策略（L11）。
 */
public class DeepSeekAdapter implements LlmAdapter {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAdapter.class);

    /**
     * 单次 LLM HTTP 请求总超时（秒）。长上下文推理容易超过 120s，默认放宽到 300s；
     * 可用系统属性 harness.llm.request-timeout-seconds 或环境变量覆盖。
     */
    private static final long REQUEST_TIMEOUT_SECONDS = Long.parseLong(
            System.getProperty("harness.llm.request-timeout-seconds",
                    System.getenv().getOrDefault("HARNESS_LLM_REQUEST_TIMEOUT_SECONDS", "300")));

    /** 流式请求等待上游首字节的超时；超时视为空流，降级非流式。 */
    static final long FIRST_BYTE_TIMEOUT_MS = 10_000L;

    private final DeepSeekAdapterOptions options;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekAdapter(DeepSeekAdapterOptions options, ObjectMapper objectMapper) {
        this.options = options;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String providerId() {
        return "deepseek";
    }

    @Override
    public Flow.Publisher<StreamChunk> stream(GenerateOptions genOptions) {
        SubmissionPublisher<StreamChunk> publisher = new SubmissionPublisher<>();
        Thread worker = new Thread(() -> {
            try {
                doStream(genOptions, publisher);
            } catch (Exception e) {
                publishError(publisher, e);
            } finally {
                publisher.close();
            }
        }, "deepseek-adapter-stream");
        worker.setDaemon(true);
        worker.start();
        return publisher;
    }

    /**
     * 发起流式请求并翻译 SSE 分片；流内没有任何数据时降级为非流式请求。
     */
    private void doStream(GenerateOptions genOptions, SubmissionPublisher<StreamChunk> publisher) throws Exception {
        String apiKey = genOptions.apiKey() != null && !genOptions.apiKey().isBlank()
                ? genOptions.apiKey() : options.resolveApiKey();
        String baseUrl = genOptions.baseUrl() != null && !genOptions.baseUrl().isBlank()
                ? genOptions.baseUrl() : options.baseUrl();
        String effectiveModel = resolveEffectiveModel(genOptions.model());
        ObjectNode body = serializeRequest(withModel(genOptions, effectiveModel));
        String payload = objectMapper.writeValueAsString(body);
        if (log.isDebugEnabled()) {
            log.debug("LLM request model={} temperature={} maxTokens={} payloadLen={}",
                    effectiveModel,
                    body.path("temperature").isMissingNode() ? "<omitted>" : body.path("temperature").asText(),
                    body.path("max_tokens").asInt(-1), payload.length());
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(OpenAiCompatibleUriResolver.resolveChatCompletionsUri(baseUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        if (genOptions.sessionId() != null) {
            builder.header("x-deepseek-harness-session-id", genOptions.sessionId());
        }

        HttpResponse<java.io.InputStream> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new LlmAdapterException(
                    extractErrorMessage(errorBody),
                    httpErrorCode(response.statusCode(), errorBody),
                    response.statusCode()
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            boolean streamed = translateSse(reader, publisher);
            if (!streamed) {
                log.warn("Upstream returned empty stream for model={}, falling back to non-stream request",
                        effectiveModel);
                doNonStreamFallback(genOptions, effectiveModel, publisher);
            }
        }
    }

    /**
     * 非流式兜底：部分网关接受 stream=true 但不吐任何数据，改为一次性请求取全文。
     */
    private void doNonStreamFallback(GenerateOptions genOptions, String effectiveModel,
                                     SubmissionPublisher<StreamChunk> publisher) throws Exception {
        String apiKey = genOptions.apiKey() != null && !genOptions.apiKey().isBlank()
                ? genOptions.apiKey() : options.resolveApiKey();
        String baseUrl = genOptions.baseUrl() != null && !genOptions.baseUrl().isBlank()
                ? genOptions.baseUrl() : options.baseUrl();
        ObjectNode body = serializeRequest(withModel(genOptions, effectiveModel));
        body.put("stream", false);

        String payload = objectMapper.writeValueAsString(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(OpenAiCompatibleUriResolver.resolveChatCompletionsUri(baseUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload));

        HttpResponse<String> response = httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new LlmAdapterException(
                    extractErrorMessage(response.body()),
                    httpErrorCode(response.statusCode(), response.body()),
                    response.statusCode()
            );
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new LlmAdapterException("non-stream response has no choices", LlmErrorCodes.EMPTY_RESPONSE);
        }

        JsonNode message = choices.get(0).path("message");
        String content = message.path("content").asText(null);
        String reasoning = message.path("reasoning_content").asText(null);

        int nextIdx = 0;
        Integer textIdx = null;
        Integer reasoningIdx = null;
        if (content != null && !content.isEmpty()) {
            textIdx = nextIdx++;
            publisher.submit(new StreamChunk.BlockStart(textIdx, "text"));
            publisher.submit(new StreamChunk.TextDelta(textIdx, content));
        }
        if (reasoning != null && !reasoning.isEmpty()) {
            reasoningIdx = nextIdx++;
            publisher.submit(new StreamChunk.BlockStart(reasoningIdx, "reasoning"));
            publisher.submit(new StreamChunk.ReasoningDelta(reasoningIdx, reasoning));
            if (textIdx == null) {
                // 仅推理无正文：合成一个文本块让下游 extractText 可见
                int tIdx = nextIdx++;
                publisher.submit(new StreamChunk.BlockStart(tIdx, "text"));
                publisher.submit(new StreamChunk.TextDelta(tIdx, reasoning));
            }
        }

        if (textIdx == null && reasoningIdx == null) {
            throw new LlmAdapterException("non-stream response has no content", LlmErrorCodes.EMPTY_RESPONSE);
        }

        List<OpenBlock> blocks = new ArrayList<>();
        if (textIdx != null) {
            OpenBlock tb = new OpenBlock(textIdx, "text");
            tb.text.append(content);
            blocks.add(tb);
        }
        if (reasoningIdx != null) {
            OpenBlock rb = new OpenBlock(reasoningIdx, "reasoning");
            rb.text.append(reasoning);
            blocks.add(rb);
            if (textIdx == null) {
                OpenBlock synth = new OpenBlock(reasoningIdx + 1, "text");
                synth.text.append(reasoning);
                blocks.add(synth);
            }
        }
        for (OpenBlock block : blocks) {
            publisher.submit(new StreamChunk.BlockEnd(block.index, block.toContentBlock()));
        }

        JsonNode usageNode = root.path("usage");
        if (!usageNode.isMissingNode()) {
            publisher.submit(new StreamChunk.Usage(mapUsage(usageNode)));
        }

        String finishReason = choices.get(0).path("finish_reason").asText("stop");
        publisher.submit(new StreamChunk.Finish(mapFinishReason(finishReason)));
    }

    /**
     * 序列化请求体（L03 子集：system / user / assistant 纯文本消息 + 采样参数；
     * tool 消息、tool_calls 与图片内容块随 L06 / L07 加入）。
     */
    private ObjectNode serializeRequest(GenerateOptions options) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", options.model());
        root.put("stream", true);

        ArrayNode messages = objectMapper.createArrayNode();
        if (options.system() != null && !options.system().isBlank()) {
            ObjectNode sysMsg = objectMapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", options.system());
            messages.add(sysMsg);
        }
        for (Message msg : options.messages()) {
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("role", msg.role());
            StringBuilder textContent = new StringBuilder();
            for (var block : msg.content()) {
                if (block instanceof TextBlock tb) {
                    textContent.append(tb.text());
                }
            }
            msgNode.put("content", textContent.toString());
            messages.add(msgNode);
        }
        root.set("messages", messages);

        if (options.temperature() != null) root.put("temperature", options.temperature());
        if (options.reasoningEffort() != null && !options.reasoningEffort().isBlank()) {
            root.put("reasoning_effort", options.reasoningEffort());
        }
        if (options.maxTokens() != null) root.put("max_tokens", options.maxTokens());
        if (options.stop() != null && !options.stop().isEmpty()) {
            root.set("stop", objectMapper.valueToTree(options.stop()));
        }

        return root;
    }

    private GenerateOptions withModel(GenerateOptions options, String model) {
        return new GenerateOptions(
                options.provider(),
                model,
                options.reasoningEffort(),
                options.messages(),
                options.system(),
                options.tools(),
                options.temperature(),
                options.maxTokens(),
                options.stop(),
                options.sessionId(),
                options.purpose(),
                options.baseUrl(),
                options.apiKey(),
                options.protocol()
        );
    }

    /**
     * 请求未指定模型时按配置默认回退——L03 的默认模型经组合根写入 AgentOptions
     * （harness.llm.deepseek.default-model），正常路径不会为空；上游目录发现与
     * 请求模型回退随 L11 接入。
     */
    private String resolveEffectiveModel(String requestedModel) {
        if (requestedModel != null && !requestedModel.isBlank()) {
            return requestedModel;
        }
        throw new LlmAdapterException("no model resolved for request", LlmErrorCodes.INVALID_REQUEST);
    }

    /**
     * 翻译 SSE 流。
     * <p>流程：等待首字节（10s 超时视为空流）→ 逐行解析 data: 帧 → 增量发布 →
     * [DONE] 时收口全部打开的块。收不到任何数据返回 false（触发非流式兜底）；
     * 收到过数据但流中断（无 [DONE]）按 STREAM_CLOSED 失败。
     */
    private boolean translateSse(BufferedReader reader, SubmissionPublisher<StreamChunk> publisher) throws IOException {
        int nextIndex = 0;
        Integer textBlockIndex = null;
        Integer reasoningBlockIndex = null;
        List<OpenBlock> openBlocks = new ArrayList<>();
        FinishReason pendingFinish = null;
        TokenUsage pendingUsage = null;
        boolean receivedData = false;

        // 等待首字节：上游可能接受 stream=true 但从不吐数据
        long firstByteDeadline = System.currentTimeMillis() + FIRST_BYTE_TIMEOUT_MS;
        while (!reader.ready()) {
            if (System.currentTimeMillis() > firstByteDeadline) {
                return false;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if (data.equals("[DONE]")) {
                // 仅推理无正文：合成文本块，让下游 extractText 能看到内容
                if (textBlockIndex == null && reasoningBlockIndex != null) {
                    OpenBlock reasoningBlock = openBlocks.get(reasoningBlockIndex);
                    int newIdx = nextIndex++;
                    OpenBlock textBlock = new OpenBlock(newIdx, "text");
                    textBlock.text.append(reasoningBlock.text.toString());
                    openBlocks.add(textBlock);
                    publisher.submit(new StreamChunk.BlockStart(newIdx, "text"));
                    publisher.submit(new StreamChunk.TextDelta(newIdx, reasoningBlock.text.toString()));
                }

                // 关闭全部打开的块
                for (OpenBlock block : openBlocks) {
                    publisher.submit(new StreamChunk.BlockEnd(block.index, block.toContentBlock()));
                }
                if (pendingUsage != null) {
                    publisher.submit(new StreamChunk.Usage(pendingUsage));
                }
                FinishReason finish = pendingFinish != null ? pendingFinish : new FinishReason.Stop();
                if (finish instanceof FinishReason.Stop && openBlocks.isEmpty()) {
                    finish = new FinishReason.Error(new LlmFailure(
                            "model returned a completed response with no content",
                            LlmErrorCodes.EMPTY_RESPONSE
                    ));
                }
                publisher.submit(new StreamChunk.Finish(finish));
                return true;
            }

            receivedData = true;

            JsonNode chunk;
            try {
                chunk = objectMapper.readTree(data);
            } catch (Exception e) {
                throw new LlmAdapterException(
                        "malformed SSE payload: " + data.substring(0, Math.min(data.length(), 120)),
                        LlmErrorCodes.MALFORMED_RESPONSE
                );
            }

            JsonNode choices = chunk.path("choices");
            for (JsonNode choiceNode : choices) {
                JsonNode delta = choiceNode.path("delta");

                // 推理内容
                String reasoning = delta.path("reasoning_content").asText(null);
                if (reasoning != null && !reasoning.isEmpty()) {
                    if (reasoningBlockIndex == null) {
                        reasoningBlockIndex = nextIndex++;
                        openBlocks.add(new OpenBlock(reasoningBlockIndex, "reasoning"));
                        publisher.submit(new StreamChunk.BlockStart(reasoningBlockIndex, "reasoning"));
                    }
                    OpenBlock block = openBlocks.get(reasoningBlockIndex);
                    block.text.append(reasoning);
                    publisher.submit(new StreamChunk.ReasoningDelta(reasoningBlockIndex, reasoning));
                }

                // 正文内容
                String content = delta.path("content").asText(null);
                if (content != null && !content.isEmpty()) {
                    if (textBlockIndex == null) {
                        textBlockIndex = nextIndex++;
                        openBlocks.add(new OpenBlock(textBlockIndex, "text"));
                        publisher.submit(new StreamChunk.BlockStart(textBlockIndex, "text"));
                    }
                    OpenBlock block = openBlocks.get(textBlockIndex);
                    block.text.append(content);
                    publisher.submit(new StreamChunk.TextDelta(textBlockIndex, content));
                }

                // tool_calls 分片解析随 L06 工具内核接入

                String finishReason = choiceNode.path("finish_reason").asText(null);
                if (finishReason != null) {
                    pendingFinish = mapFinishReason(finishReason);
                }
            }

            JsonNode usageNode = chunk.path("usage");
            if (!usageNode.isMissingNode()) {
                pendingUsage = mapUsage(usageNode);
            }
        }

        // 流结束但没有 [DONE]
        if (!receivedData) {
            return false;
        }
        throw new LlmAdapterException("SSE payload stream ended without [DONE]", LlmErrorCodes.STREAM_CLOSED);
    }

    /** 上游 finish_reason → 领域 FinishReason。 */
    private static FinishReason mapFinishReason(String reason) {
        return switch (reason) {
            case "stop" -> new FinishReason.Stop();
            case "tool_calls" -> new FinishReason.ToolCalls();
            case "length" -> new FinishReason.MaxTokens();
            default -> new FinishReason.Error(new LlmFailure(
                    "model stopped: " + reason, reason.toUpperCase()
            ));
        };
    }

    /** 上游 usage → 领域 TokenUsage（含缓存命中与推理 token 的双写法兼容）。 */
    private static TokenUsage mapUsage(JsonNode usage) {
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
        Integer cacheRead = null;
        JsonNode details = usage.path("prompt_tokens_details");
        if (!details.isMissingNode()) {
            cacheRead = details.path("cached_tokens").asInt(0);
            if (cacheRead == 0) cacheRead = null;
        }
        if (cacheRead == null) {
            cacheRead = usage.path("prompt_cache_hit_tokens").asInt(0);
            if (cacheRead == 0) cacheRead = null;
        }
        Integer reasoning = null;
        JsonNode compDetails = usage.path("completion_tokens_details");
        if (!compDetails.isMissingNode()) {
            reasoning = compDetails.path("reasoning_tokens").asInt(0);
            if (reasoning == 0) reasoning = null;
        }
        int inputTokens = cacheRead != null ? promptTokens - cacheRead : promptTokens;
        return new TokenUsage(inputTokens, completionTokens, cacheRead, null, reasoning);
    }

    /** HTTP 状态码 → 稳定错误码。 */
    private String httpErrorCode(int status, String errorBody) {
        if (status == 401 || status == 403) return LlmErrorCodes.AUTH;
        if (status == 429) return LlmErrorCodes.RATE_LIMIT;
        if (status == 400) return LlmErrorCodes.INVALID_REQUEST;
        if (status >= 500) return LlmErrorCodes.SERVER;
        return "HTTP_" + status;
    }

    /** 从上游错误体提取 error.message；解析失败回退原文。 */
    private String extractErrorMessage(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String msg = root.path("error").path("message").asText(null);
            if (msg != null && !msg.isBlank()) return msg;
        } catch (Exception ignored) {
            return body;
        }
        return body;
    }

    /** 把适配器异常收敛为 Error Finish 分片发布。 */
    private void publishError(SubmissionPublisher<StreamChunk> publisher, Exception e) {
        LlmFailure failure;
        if (e instanceof LlmAdapterException lae) {
            failure = new LlmFailure(lae.getMessage(), lae.code(), lae.status(), null, null);
        } else {
            failure = new LlmFailure(e.getMessage() != null ? e.getMessage() : "LLM adapter failed",
                    LlmErrorCodes.UNKNOWN);
        }
        log.error("DeepSeek adapter stream error: {}", failure.message(), e);
        publisher.submit(new StreamChunk.Finish(new FinishReason.Error(failure)));
    }

    // ── 内部类型 ────────────────────────────────────────────

    /** 流式翻译过程中的累积块。 */
    private static final class OpenBlock {
        final int index;
        final String kind;
        final StringBuilder text = new StringBuilder();

        OpenBlock(int index, String kind) {
            this.index = index;
            this.kind = kind;
        }

        ContentBlock toContentBlock() {
            return switch (kind) {
                case "text" -> new TextBlock(text.toString());
                case "reasoning" -> new ReasoningBlock(text.toString());
                default -> throw new IllegalStateException("cannot close block of type: " + kind);
            };
        }
    }

    /** 适配器内部异常：携带稳定错误码与上游 HTTP 状态。 */
    public static final class LlmAdapterException extends RuntimeException {
        private final String code;
        private final Integer status;

        public LlmAdapterException(String message, String code) {
            this(message, code, null);
        }

        public LlmAdapterException(String message, String code, Integer status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        public String code() {
            return code;
        }

        public Integer status() {
            return status;
        }
    }
}
