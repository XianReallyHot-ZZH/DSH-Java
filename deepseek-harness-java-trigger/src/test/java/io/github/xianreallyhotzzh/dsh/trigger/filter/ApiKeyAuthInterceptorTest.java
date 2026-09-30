package io.github.xianreallyhotzzh.dsh.trigger.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 复刻件增补测试（vendor 无对应单测）：钉住 L02 DoD 的拦截器行为——
 * 空 keys 全放行、配置后无凭据 401、X-API-Key / Bearer 双通道、OPTIONS 与静态资源白名单。
 */
class ApiKeyAuthInterceptorTest {

    private ApiKeyAuthInterceptor interceptor;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new ApiKeyAuthInterceptor();
        response = new MockHttpServletResponse();
    }

    private void configuredKeys(String keys) {
        ReflectionTestUtils.setField(interceptor, "configuredKeys", keys);
    }

    private MockHttpServletRequest request(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }

    /** 配置钥匙并构造请求——多数用例的公共前置。 */
    private MockHttpServletRequest gatedRequest(String keys, String method, String uri) {
        configuredKeys(keys);
        return request(method, uri);
    }

    @Test
    void emptyKeysAllowsEverythingForLocalDevelopment() throws Exception {
        assertTrue(interceptor.preHandle(
                gatedRequest("", "GET", "/api/harness/config/effective"), response, null));
        assertEquals(200, response.getStatus());
    }

    @Test
    void missingCredentialIsRejectedWith401WhenKeysConfigured() throws Exception {
        assertFalse(interceptor.preHandle(
                gatedRequest("k1,k2", "GET", "/api/harness/config/effective"), response, null));
        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
        assertTrue(response.getContentAsString().contains("Unauthorized"));
    }

    @Test
    void wrongKeyIsRejectedWith401() throws Exception {
        MockHttpServletRequest request = gatedRequest("k1,k2", "GET", "/api/harness/config/effective");
        request.addHeader("X-API-Key", "k3");
        assertFalse(interceptor.preHandle(request, response, null));
        assertEquals(401, response.getStatus());
    }

    @Test
    void xApiKeyHeaderIsAccepted() throws Exception {
        MockHttpServletRequest request = gatedRequest("k1,k2", "GET", "/api/harness/config/effective");
        request.addHeader("X-API-Key", "k1");
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    void bearerTokenIsAccepted() throws Exception {
        MockHttpServletRequest request = gatedRequest("k1, k2", "GET", "/api/harness/config/effective");
        request.addHeader("Authorization", "Bearer k2");
        assertTrue(interceptor.preHandle(request, response, null));
    }

    @Test
    void corsPreflightOptionsAlwaysPassesEvenWithoutCredential() throws Exception {
        assertTrue(interceptor.preHandle(
                gatedRequest("k1,k2", "OPTIONS", "/api/harness/config/effective"), response, null));
    }

    @Test
    void publicStaticResourcesAndWellKnownPassWithoutCredential() throws Exception {
        configuredKeys("k1,k2");
        for (String uri : new String[]{"/", "/index.html", "/app.js", "/app.css",
                "/lib/x.js", "/favicon.ico", "/actuator/health", "/.well-known/agent-card.json"}) {
            assertTrue(interceptor.preHandle(request("GET", uri), response, null), "应放行：" + uri);
        }
    }
}
