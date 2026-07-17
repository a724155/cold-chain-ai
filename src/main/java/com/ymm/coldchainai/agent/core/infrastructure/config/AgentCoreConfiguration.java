package com.ymm.coldchainai.agent.core.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Core 基础配置。
 *
 * <p>该配置类负责创建正式冷运 Agent 使用的 ChatClient。
 * 第一阶段的 basicChatClient 继续用于环境验证，两者职责不同。</p>
 *
 * <p>后续接入订单 Tool、支付 Tool、知识检索 Tool 和 Advisor 时，
 * 统一在正式 Agent Core 配置中完成，不修改验证模块。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AgentCoreConfiguration {

    /**
     * 正式冷运 Agent 的基础系统提示词。
     *
     * <p>当前版本尚未接入任何 Tool，因此必须明确限制模型不得伪造实时业务数据。
     * 后续订单、支付和知识检索能力会通过 Tool Calling 逐步开放。</p>
     */
    private static final String COLD_CHAIN_AGENT_SYSTEM_PROMPT = """
            你是冷运 AI 系统的企业业务助手。

            当前版本只具备普通模型问答能力，尚未接入司机订单查询、定金支付查询和业务规则知识检索工具。

            请遵守以下规则：
            1. 回答必须准确、清晰、简洁。
            2. 不得伪造司机、订单、支付单、用户、租户或公司内部业务数据。
            3. 用户询问实时订单或支付数据时，必须明确说明当前尚未接入对应查询工具。
            4. 不得声称已经查询数据库、调用接口或读取公司文件。
            5. 不确定的信息必须明确说明不确定，不能编造答案。
            """;

    /**
     * 创建正式冷运 Agent 使用的 ChatClient。
     *
     * @param chatClientBuilder Spring AI 自动配置的 ChatClient 构建器
     * @return 设置正式 Agent 系统提示词的 ChatClient
     */
    @Bean
    public ChatClient coldChainAgentChatClient(ChatClient.Builder chatClientBuilder) {
        // 将正式 Agent 的系统提示词设置为默认值，避免每次模型调用时重复传入相同内容。
        return chatClientBuilder.defaultSystem(COLD_CHAIN_AGENT_SYSTEM_PROMPT).build();
    }
}
