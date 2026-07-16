package com.ymm.coldchainai.bootstrap.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模型基础配置。
 *
 * <p>该配置类负责基于 Spring AI 自动创建的 {@link ChatClient.Builder}
 * 构建项目统一使用的基础 {@link ChatClient}。</p>
 *
 * <p>当前阶段仅用于验证普通模型调用。第二阶段开发 Agent Core 时，
 * 会在此基础上增加不同 Agent 对应的系统提示词、Advisor、Tool 和生命周期日志。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AiModelConfiguration {

    /**
     * 基础模型系统提示词。
     *
     * <p>系统提示词用于约束模型的默认身份和回答方向。
     * 当前只设置最基础的冷运业务助手身份，不添加订单查询、支付查询或知识检索能力。</p>
     */
    private static final String BASIC_SYSTEM_PROMPT = """
            你是冷运 AI 系统的基础模型验证助手。
            当前阶段只需要准确、简洁地回答用户问题。
            不得伪造订单、司机、支付或公司业务数据。
            """;

    /**
     * 创建基础 ChatClient。
     *
     * @param chatClientBuilder Spring AI 自动配置并注入的 ChatClient 构建器
     * @return 已设置基础系统提示词的 ChatClient
     */
    @Bean
    public ChatClient basicChatClient(ChatClient.Builder chatClientBuilder) {
        // 使用自动配置的 Builder 构建 ChatClient，保留 Spring AI 提供的模型配置和可观测能力。
        return chatClientBuilder.defaultSystem(BASIC_SYSTEM_PROMPT).build();
    }
}
