package com.ymm.coldchainai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 冷运 AI 系统启动类。
 *
 * <p>该类位于项目顶层包 {@code com.ymm.coldchainai} 下，
 * Spring Boot 默认会从当前包开始向下扫描 Controller、Service、
 * Configuration、Mapper 等 Spring Bean。</p>
 *
 * <p>后续新增的 agent、driver、order、payment、knowledge 等业务模块，
 * 都必须放在 {@code com.ymm.coldchainai} 包下面，确保能够被 Spring 自动扫描。</p>
 *
 * @author ymm
 */
@SpringBootApplication
public class ColdChainAiApplication {

    /**
     * 启动冷运 AI 系统。
     *
     * @param args JVM 启动参数，可以用于传入 Spring Profile、端口号等运行配置
     */
    public static void main(String[] args) {
        // 创建 Spring 容器、执行自动配置并启动内嵌 Web 服务器。
        SpringApplication.run(ColdChainAiApplication.class, args);
    }

}
