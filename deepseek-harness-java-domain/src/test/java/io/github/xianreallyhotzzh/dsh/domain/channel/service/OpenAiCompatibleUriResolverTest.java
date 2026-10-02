package io.github.xianreallyhotzzh.dsh.domain.channel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleUriResolverTest {

    @Test
    void keepsExistingVersionForModelsEndpoint() {
        assertEquals(URI.create("https://open.bigmodel.cn/api/paas/v4/models"),
                OpenAiCompatibleUriResolver.resolveModelsUri("https://open.bigmodel.cn/api/paas/v4/"));
        assertEquals(URI.create("https://ark.cn-beijing.volces.com/api/v3/models"),
                OpenAiCompatibleUriResolver.resolveModelsUri("https://ark.cn-beijing.volces.com/api/v3"));
    }

    @Test
    void keepsExistingVersionForChatEndpoint() {
        assertEquals(URI.create("https://open.bigmodel.cn/api/paas/v4/chat/completions"),
                OpenAiCompatibleUriResolver.resolveChatCompletionsUri("https://open.bigmodel.cn/api/paas/v4"));
        assertEquals(URI.create("https://open.bigmodel.cn/api/paas/v4/chat/completions"),
                OpenAiCompatibleUriResolver.resolveChatCompletionsUri(
                        "https://open.bigmodel.cn/api/paas/v4/chat/completions"));
    }

    @Test
    void keepsPreReleaseVersions() {
        assertEquals(URI.create("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"),
                OpenAiCompatibleUriResolver.resolveChatCompletionsUri(
                        "https://generativelanguage.googleapis.com/v1beta/openai/"));
    }

    @Test
    void appendsV1OnlyWhenBaseUrlHasNoVersion() {
        assertEquals(URI.create("https://api.example.com/openai/v1/models"),
                OpenAiCompatibleUriResolver.resolveModelsUri("https://api.example.com/openai"));
        assertEquals(URI.create("https://api.example.com/openai/v1/chat/completions"),
                OpenAiCompatibleUriResolver.resolveChatCompletionsUri("https://api.example.com/openai"));
    }
}
