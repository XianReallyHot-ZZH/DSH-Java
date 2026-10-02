package io.github.xianreallyhotzzh.dsh.infrastructure.adapter.llm;

import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.LlmAdapter;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.FinishReason;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.GenerateOptions;
import io.github.xianreallyhotzzh.dsh.domain.llm.model.valobj.StreamChunk;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.Message;
import io.github.xianreallyhotzzh.dsh.domain.model.entity.MessageSource;
import io.github.xianreallyhotzzh.dsh.domain.model.valobj.TextBlock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * 端口注册与错误包装的行为钉子（脚本化 fake 适配器，不外呼）；
 * vendor 同名测试的重试用例随 L11 重试机制接入后搬运。
 */
class InMemoryLlmRuntimePortTest {

    @Test
    void routesToRegisteredAdapterAndForwardsChunks() throws Exception {
        List<StreamChunk> script = List.of(
                new StreamChunk.BlockStart(0, "text"),
                new StreamChunk.TextDelta(0, "ok"),
                new StreamChunk.BlockEnd(0, new TextBlock("ok")),
                new StreamChunk.Finish(new FinishReason.Stop())
        );
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        runtime.registerAdapter("test", new FakeAdapter(() -> script));

        List<StreamChunk> chunks = collect(runtime.stream(options("test")));

        assertEquals(script, chunks);
    }

    @Test
    void unknownProviderEndsWithErrorFinishInsteadOfThrowing() throws Exception {
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        runtime.registerAdapter("test", new FakeAdapter(() -> List.of()));

        List<StreamChunk> chunks = collect(runtime.stream(options("ghost")));

        assertEquals(1, chunks.size());
        StreamChunk.Finish finish = assertInstanceOf(StreamChunk.Finish.class, chunks.get(0));
        FinishReason.Error error = assertInstanceOf(FinishReason.Error.class, finish.reason());
        assertTrue(error.failure().message().contains("No adapter registered for provider 'ghost'"));
    }

    @Test
    void wrapsAdapterFailureAsErrorFinish() throws Exception {
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        runtime.registerAdapter("test", new FailingAdapter(new RuntimeException("boom")));

        List<StreamChunk> chunks = collect(runtime.stream(options("test")));

        assertEquals(1, chunks.size());
        StreamChunk.Finish finish = assertInstanceOf(StreamChunk.Finish.class, chunks.get(0));
        FinishReason.Error error = assertInstanceOf(FinishReason.Error.class, finish.reason());
        assertEquals("boom", error.failure().message());
        assertEquals("UNKNOWN", error.failure().code());
    }

    private GenerateOptions options(String provider) {
        return new GenerateOptions(
                provider, "test-model", null,
                List.of(Message.createUser(List.of(new TextBlock("hello")), new MessageSource.UserSource())),
                null, null, null, null, null, null, null, null, null, null
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
        assertTrue(done.await(5, TimeUnit.SECONDS));
        assertEquals(null, error.get(), "端口承诺不向订阅方抛异常");
        return chunks;
    }

    /** 按脚本同步回放的适配器。 */
    private record FakeAdapter(Supplier<List<StreamChunk>> script) implements LlmAdapter {
        @Override
        public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
            List<StreamChunk> chunks = script.get();
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { }
                    @Override public void cancel() { }
                });
                chunks.forEach(subscriber::onNext);
                subscriber.onComplete();
            };
        }

        @Override
        public String providerId() {
            return "test";
        }
    }

    /** 订阅即 onError 的适配器（模拟适配器线程崩溃）。 */
    private record FailingAdapter(RuntimeException failure) implements LlmAdapter {
        @Override
        public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
            return subscriber -> {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { }
                    @Override public void cancel() { }
                });
                subscriber.onError(failure);
            };
        }

        @Override
        public String providerId() {
            return "test";
        }
    }
}
