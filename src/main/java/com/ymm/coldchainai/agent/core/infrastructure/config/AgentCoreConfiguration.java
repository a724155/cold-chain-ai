package com.ymm.coldchainai.agent.core.infrastructure.config;

import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.infrastructure.advisor.AgentLifecycleLoggingAdvisor;
import com.ymm.coldchainai.agent.core.infrastructure.advisor.ModelLifecycleLoggingAdvisor;
import com.ymm.coldchainai.agent.core.infrastructure.springai.model.SpringAiAgentRuntime;
import com.ymm.coldchainai.order.interfaces.tool.DriverOrderQueryTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent Core基础配置。
 *
 * <p>该配置类负责注册当前系统支持的Agent定义，
 * 并为每个Agent创建独立的Spring AI运行配置。</p>
 *
 * <p>后续增加新的订单Agent、支付Agent或知识Agent时，不能只增加AgentDefinition。
 * 每个可执行Agent都必须同时提供对应的ChatClient和SpringAiAgentRuntime，
 * 否则应用启动时会因为运行配置不完整而失败。</p>
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
     * 冷运综合业务助手系统提示词。
     *
     * <p>该提示词只属于cold-chain-general Agent。
     * 后续其他Agent必须定义自己的系统提示词，不能直接复用本提示词后声称具有不同业务能力。</p>
     */
    private static final String COLD_CHAIN_GENERAL_AGENT_SYSTEM_PROMPT = """
        你是冷运 AI 系统的企业综合业务助手。

        当前已经接入司机成交订单查询工具query_driver_deal_orders，
        但尚未接入定金支付查询和公司业务规则知识检索工具。

        请遵守以下规则：
        1. 回答必须准确、清晰、简洁。
        2. 用户询问指定司机今天或某天是否存在成交订单、成交了哪些订单时，必须调用query_driver_deal_orders工具。
        3. 用户说“今天”时，调用订单工具可以省略queryDate，由工具按照冷运业务时区计算当前日期。
        4. 订单事实必须以工具返回结果为准，不得根据常识、历史对话或示例订单号自行编造。
        5. 工具返回hasDealOrder=false时，应明确告诉用户该日期没有查询到成交订单。
        6. 工具返回success=false时，应根据errorMessage说明参数问题，不能伪造查询结果。
        7. 用户询问支付或公司规则时，必须说明当前尚未接入对应工具。
        8. 不确定的信息必须明确说明不确定，不能编造答案。
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
     * 创建冷运综合业务助手专属ChatClient。
     *
     * <p>Agent生命周期和模型生命周期Advisor作为默认Advisor注册，
     * 因此该ChatClient发起的每次请求都会自动经过两套日志链路。</p>
     *
     * @param chatClientBuilder Spring AI自动配置的ChatClient构建器
     * @param agentLifecycleLoggingAdvisor Agent完整调用链日志Advisor
     * @param modelLifecycleLoggingAdvisor 单次模型调用日志Advisor
     * @param driverOrderQueryTool 司机成交订单查询Tool
     * @return 设置系统提示词和默认Advisor的ChatClient
     */
    @Bean
    public ChatClient coldChainGeneralChatClient(ChatClient.Builder chatClientBuilder, AgentLifecycleLoggingAdvisor agentLifecycleLoggingAdvisor,
                                                 ModelLifecycleLoggingAdvisor modelLifecycleLoggingAdvisor,
                                                 DriverOrderQueryTool driverOrderQueryTool) {
        /*
         * defaultAdvisors会将Advisor注册为当前ChatClient的默认执行链。
         * 后续每次调用不需要重复传入Advisor实例，只需要传入requestId、agentCode等动态上下文参数。
         */
        return chatClientBuilder.defaultSystem(COLD_CHAIN_GENERAL_AGENT_SYSTEM_PROMPT)
                .defaultAdvisors(agentLifecycleLoggingAdvisor, modelLifecycleLoggingAdvisor)
                .defaultTools(driverOrderQueryTool)
                .build();
    }

    /**
     * 创建冷运综合业务助手的Spring AI运行配置。
     *
     * <p>参数名称分别与对应Bean方法名称保持一致。
     * Spring会将coldChainGeneralAgentDefinition和coldChainGeneralChatClient注入本方法。</p>
     *
     * @param coldChainGeneralAgentDefinition 冷运综合业务助手定义
     * @param coldChainGeneralChatClient 冷运综合业务助手专属ChatClient
     * @return 冷运综合业务助手运行配置
     */
    @Bean
    public SpringAiAgentRuntime coldChainGeneralAgentRuntime(AgentDefinition coldChainGeneralAgentDefinition, ChatClient coldChainGeneralChatClient) {
        // 使用Agent定义中的稳定编码绑定对应ChatClient，避免在两个位置重复手写agentCode。
        return SpringAiAgentRuntime.of(coldChainGeneralAgentDefinition.getAgentCode(), coldChainGeneralChatClient);
    }
}
