package io.github.xianreallyhotzzh.dsh.infrastructure.adapter.llm.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.FinishReason;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.GenerateOptions;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.LlmErrorCodes;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.MessageSource;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * L03 工单钉住的三条适配器行为（脚本化本地端点，不外呼）：
 * 正常 SSE 回复、上游 5xx 收敛、空流降级非流式。
 * vendor 无同名测试（其适配器靠真端点手工验收）——本测试为复刻件自建。
 */
class DeepSeekAdapterTest {

    private HttpServer server;
    private ObjectMapper objectMapper;
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    private final AtomicReference<java.util.function.BiConsumer<HttpExchange, String>> handlerScript =
            new AtomicReference<>();

    @BeforeEach
    void startScriptedServer() throws IOException {
        objectMapper = new ObjectMapper();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requestBodies.add(body);
            handlerScript.get().accept(exchange, body);
        });
        server.start();
    }

    @AfterEach
    void stopScriptedServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private DeepSeekAdapter adapter() {
        return new DeepSeekAdapter(new DeepSeekAdapterOptions(baseUrl(), "test-key", 4096), objectMapper);
    }

    @Test
    void streamsSseReplyAsChunks() throws Exception {
        handlerScript.set((exchange, body) -> {
            try {
                // 请求侧对拍：流式请求体与鉴权/会话头
                assertTrue(body.contains("\"stream\":true"));
                assertTrue(body.contains("\"model\":\"deepseek-chat\""));
                assertTrue(body.contains("\"role\":\"user\""));
                assertTrue(body.contains("讲个笑话"));
                assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
                assertEquals("session-1", exchange.getRequestHeaders().getFirst("x-deepseek-harness-session-id"));
                respond(exchange, 200, "text/event-stream", String.join("\n",
                        "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}",
                        "",
                        "data: {\"choices\":[{\"delta\":{\"content\":\"，世界\"}}]}",
                        "",
                        "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":6}}",
                        "",
                        "data: [DONE]",
                        "",
                        ""));
            } catch (Throwable t) {
                respond(exchange, 500, "application/json", t.toString());
            }
        });

        List<StreamChunk> chunks = collect(adapter().stream(options()));

        assertEquals(List.of(
                new StreamChunk.BlockStart(0, "text"),
                new StreamChunk.TextDelta(0, "你好"),
                new StreamChunk.TextDelta(0, "，世界"),
                new StreamChunk.BlockEnd(0, new TextBlock("你好，世界")),
                new StreamChunk.Usage(new io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.TokenUsage(12, 6, null, null, null)),
                new StreamChunk.Finish(new FinishReason.Stop())
        ), chunks);
    }

    @Test
    void mapsUpstream5xxToServerErrorFinish() throws Exception {
        handlerScript.set((exchange, body) ->
                respond(exchange, 500, "application/json",
                        "{\"error\":{\"message\":\"upstream exploded\"}}"));

        List<StreamChunk> chunks = collect(adapter().stream(options()));

        assertEquals(1, chunks.size());
        StreamChunk.Finish finish = assertInstanceOf(StreamChunk.Finish.class, chunks.get(0));
        FinishReason.Error error = assertInstanceOf(FinishReason.Error.class, finish.reason());
        assertEquals("upstream exploded", error.failure().message());
        assertEquals(LlmErrorCodes.SERVER, error.failure().code());
        assertEquals(500, error.failure().status());
    }

    @Test
    void fallsBackToNonStreamWhenStreamYieldsNoData() throws Exception {
        handlerScript.set((exchange, body) -> {
            if (body.contains("\"stream\":true")) {
                // 接受流式请求但只回 SSE 注释行、从不吐数据 → 适配器应判定空流
                respond(exchange, 200, "text/event-stream", ": keepalive\n\n");
            } else {
                respond(exchange, 200, "application/json",
                        "{\"choices\":[{\"message\":{\"content\":\"降级成功\"},\"finish_reason\":\"stop\"}],"
                                + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}");
            }
        });

        List<StreamChunk> chunks = collect(adapter().stream(options()));

        // 两次请求：先流式后非流式，第二次请求体 stream=false
        assertEquals(2, requestBodies.size());
        assertTrue(requestBodies.get(0).contains("\"stream\":true"));
        assertTrue(requestBodies.get(1).contains("\"stream\":false"));

        assertTrue(chunks.stream().anyMatch(c -> c instanceof StreamChunk.TextDelta td && td.text().equals("降级成功")));
        assertTrue(chunks.stream().anyMatch(c -> c instanceof StreamChunk.BlockEnd be
                && be.block() instanceof TextBlock tb && tb.text().equals("降级成功")));
        StreamChunk.Finish finish = assertInstanceOf(StreamChunk.Finish.class, chunks.get(chunks.size() - 1));
        assertInstanceOf(FinishReason.Stop.class, finish.reason());
    }

    // ── 测试基建 ────────────────────────────────────────────

    private GenerateOptions options() {
        return new GenerateOptions(
                "deepseek", "deepseek-chat", null,
                List.of(Message.createUser(List.of(new TextBlock("讲个笑话")), new MessageSource.UserSource())),
                null, null, null, 4096, null, "session-1", null, null, null, null
        );
    }

    private List<StreamChunk> collect(Flow.Publisher<StreamChunk> publisher) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<StreamChunk> chunks = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }
            @Override public void onNext(StreamChunk item) {
                chunks.add(item);
            }
            @Override public void onError(Throwable throwable) {
                error.set(throwable);
                done.countDown();
            }
            @Override public void onComplete() {
                done.countDown();
            }
        });
        assertTrue(done.await(15, TimeUnit.SECONDS), "适配器应在超时内收尾");
        assertEquals(null, error.get(), "适配器承诺异常收敛为 Error Finish，不向订阅方抛出");
        return chunks;
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        } catch (IOException ignored) {
            // 客户端已断开
        }
    }
}
