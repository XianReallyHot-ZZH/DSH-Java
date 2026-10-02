package io.github.xianreallyhotzzh.dsh.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xianreallyhotzzh.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import io.github.xianreallyhotzzh.dsh.domain.agent.model.entity.AgentOptions;
import io.github.xianreallyhotzzh.dsh.domain.agent.service.run.AgentRunFactory;
import io.github.xianreallyhotzzh.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import io.github.xianreallyhotzzh.dsh.infrastructure.adapter.llm.InMemoryLlmRuntimePort;
import io.github.xianreallyhotzzh.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapter;
import io.github.xianreallyhotzzh.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基础设施装配配置（组合根）。
 * <p>
 * 全工程的 Spring 装配中心在 infrastructure 的 config 而非 app（见 docs/lessons/L01
 * 教学点 1）。L02 注册扩展配置属性类；L03 填入第一批端口 Bean：默认渠道配置
 * （消费 harness.yml 的 llm 段）、DeepSeek 适配器、LLM 运行时端口与 Agent 运行工厂。
 * 后续课的 Bean 随各自端口逐课加入。</p>
 */
@Configuration
@EnableConfigurationProperties(HarnessExtensionsProperties.class)
public class HarnessApplicationConfig {

    /**
     * 默认渠道配置（harness.llm.deepseek.*，经占位符链解析 LLM_* 环境变量）。
     * 请求显式值在 AgentResolveNode 覆盖这里；数据库默认档随 L09 加入。
     */
    @Bean
    public AgentOptions defaultAgentOptions(
            @Value("${harness.llm.deepseek.base-url}") String baseUrl,
            @Value("${harness.llm.deepseek.api-key}") String apiKey,
            @Value("${harness.llm.deepseek.default-model}") String defaultModel,
            @Value("${harness.llm.deepseek.max-tokens:8192}") Integer maxTokens
    ) {
        return new AgentOptions("deepseek", "deepseek", defaultModel, maxTokens,
                null, baseUrl, apiKey, null);
    }

    /** DeepSeek 适配器（OpenAI 兼容协议）。 */
    @Bean
    public DeepSeekAdapter deepSeekAdapter(
            ObjectMapper objectMapper,
            @Value("${harness.llm.deepseek.base-url}") String baseUrl,
            @Value("${harness.llm.deepseek.api-key}") String apiKey,
            @Value("${harness.llm.deepseek.max-tokens:8192}") int maxTokens
    ) {
        DeepSeekAdapterOptions options = new DeepSeekAdapterOptions(baseUrl, apiKey, maxTokens);
        return new DeepSeekAdapter(options, objectMapper);
    }

    /** LLM 运行时端口：注册 DeepSeek 适配器；更多渠道的注册与协议路由随 L11 加入。 */
    @Bean
    public ILlmRuntimePort llmRuntimePort(DeepSeekAdapter deepSeekAdapter) {
        InMemoryLlmRuntimePort runtime = new InMemoryLlmRuntimePort();
        runtime.registerAdapter(deepSeekAdapter.providerId(), deepSeekAdapter);
        return runtime;
    }

    /** Agent 运行工厂（case 层经 AgentRunLifecycle 契约消费）。 */
    @Bean
    public AgentRunLifecycle agentRunFactory(ILlmRuntimePort llmRuntimePort) {
        return new AgentRunFactory(llmRuntimePort);
    }
}
