package io.github.xianreallyhotzzh.dsh.trigger.filter;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置：注册 API Key 拦截器 + CORS 全开。
 *
 * <p>调用流程：HTTP/Web 层 → API 适配器 → 应用门面。</p>
 * <p>示例：配置类只负责 MVC 装配，不写业务规则。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;

    /**
     * 注入 API Key 认证拦截器。
     *
     * @param apiKeyAuthInterceptor 拦截器
     */
    public WebMvcConfig(ApiKeyAuthInterceptor apiKeyAuthInterceptor) {
        this.apiKeyAuthInterceptor = apiKeyAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyAuthInterceptor).addPathPatterns("/**");
    }

    /**
     * 放开 CORS：桌面端 webview（Tauri v2，origin 为 tauri://localhost 或
     * http(s)://tauri.localhost）自 2026-09 起改用原生 fetch 直连本服务
     * （绕开 plugin-http 的 IPC 流转发缺陷），浏览器侧会校验 CORS，
     * SSE 流式接口（agent stream 与房间事件流）也依赖这里的响应头。
     * 服务只绑本机回环地址，来源放开不引入外网暴露面。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
