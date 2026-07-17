package com.ymm.coldchainai.agent.core.infrastructure.config;

import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Core 基础配置。
 *
 * <p>该配置类负责创建正式冷运Agent使用的ChatClient，
 * 并注册当前系统已经支持的Agent定义。</p>
 *
 * <p>后续接入司机订单Agent、支付Agent和知识Agent时，
 * 每个Agent都需要提供独立的AgentDefinition，并根据实际能力配置提示词和Tool。</p>
 */
@Configuration(proxyBeanMethods = false)
public class AgentCoreConfiguration {

    /**
     * 冷运综合业务助手稳定编码。
     */
    private static final String GENERAL_AGENT_CODE = "cold-chain-general";

    /**
     * 冷运综合业务助手展示名称。
     */
    private static final String GENERAL_AGENT_NAME = "冷运综合业务助手";

    /**
     * 冷运综合业务助手能力说明。
     */
    private static final String GENERAL_AGENT_DESCRIPTION = "提供冷运业务基础问答，并作为未指定Agent时的默认助手";

    /**
     * 正式冷运Agent的基础系统提示词。
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
     * 注册冷运综合业务助手定义。
     *
     * @return 默认启用的冷运综合业务助手
     */
    @Bean
    public AgentDefinition coldChainGeneralAgentDefinition() {
        // 当前系统只有一个正式Agent，因此同时设置为启用状态和默认Agent。
        return AgentDefinition.of(GENERAL_AGENT_CODE, GENERAL_AGENT_NAME, GENERAL_AGENT_DESCRIPTION, true, true);
    }

    /**
     * 创建正式冷运Agent使用的ChatClient。
     *
     * @param chatClientBuilder Spring AI自动配置的ChatClient构建器
     * @return 设置正式Agent系统提示词的ChatClient
     */
    @Bean
    public ChatClient coldChainAgentChatClient(ChatClient.Builder chatClientBuilder) {
        // 将正式Agent系统提示词设置为默认值，避免每次模型调用时重复传入相同内容。
        return chatClientBuilder.defaultSystem(COLD_CHAIN_AGENT_SYSTEM_PROMPT).build();
    }
}
