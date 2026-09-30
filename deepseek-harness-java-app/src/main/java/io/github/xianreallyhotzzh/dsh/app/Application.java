package io.github.xianreallyhotzzh.dsh.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// L09 持久化核心时引入 mybatis 依赖并启用：
// org.mybatis.spring.annotation.MapperScan("io.github.xianreallyhotzzh.dsh.infrastructure.dao")

/**
 * 启动 DSH-Java（deepseek-harness-java 复刻件）。
 *
 * <p>app 模块是 Spring Boot 启动器：只做扫描入口与资源承载，不做装配——
 * 组合根在 infrastructure 的 config（见 docs/lessons/L01）。</p>
 *
 * <p>调用流程：Spring Boot 启动 → 扫描 io.github.xianreallyhotzzh.dsh 全包 → 加载各层组件。</p>
 */
@SpringBootApplication(scanBasePackages = "io.github.xianreallyhotzzh.dsh")
// TODO L09：@MapperScan("io.github.xianreallyhotzzh.dsh.infrastructure.dao")
public class Application {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
