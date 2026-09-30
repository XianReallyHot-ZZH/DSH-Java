package io.github.xianreallyhotzzh.dsh.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 基础设施装配配置（组合根）。
 *
 * <p>全工程的 Spring 装配中心在 infrastructure 的 config 而非 app（见 docs/lessons/L01
 * 教学点 1）。L02 只注册扩展配置属性类；把纯 Java 的 domain 对象装配成对象图的
 * {@code @Bean} 自 L03 起逐课填充（vendor 同名类最终承载全部端口绑定）。</p>
 */
@Configuration
@EnableConfigurationProperties(HarnessExtensionsProperties.class)
public class HarnessApplicationConfig {

}
