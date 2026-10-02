package io.github.xianreallyhotzzh.dsh.domain.channel.service;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * OpenAI 兼容端点解析器。
 * <p>
 * Base URL 可能自带 /v1、/v3、/v4、/v1beta 等版本段，此时必须继续沿用该版本段，
 * 不能盲目追加 /v1。
 */
public final class OpenAiCompatibleUriResolver {

    private static final Pattern VERSIONED_PATH = Pattern.compile("/v\\d+(?:[a-z]+)?(?:/|$)");

    private OpenAiCompatibleUriResolver() {
    }

    /** 解析 OpenAI 兼容模型列表端点。 */
    public static URI resolveModelsUri(String baseUrl) {
        return resolve(baseUrl, "/models");
    }

    /** 解析 OpenAI 兼容 Chat Completions 端点。 */
    public static URI resolveChatCompletionsUri(String baseUrl) {
        return resolve(baseUrl, "/chat/completions");
    }

    private static URI resolve(String baseUrl, String path) {
        String normalized = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        if (normalized.endsWith(path)) {
            return URI.create(normalized);
        }
        if (VERSIONED_PATH.matcher(normalized).find()) {
            return URI.create(normalized + path);
        }
        return URI.create(normalized + "/v1" + path);
    }
}
