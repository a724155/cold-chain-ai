package com.ymm.coldchainai.agent.core.infrastructure.config;

import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.infrastructure.advisor.AgentLifecycleLoggingAdvisor;
import com.ymm.coldchainai.agent.core.infrastructure.advisor.ModelLifecycleLoggingAdvisor;
import com.ymm.coldchainai.agent.core.infrastructure.springai.model.SpringAiAgentRuntime;
import com.ymm.coldchainai.order.interfaces.tool.DriverOrderQueryTool;
import com.ymm.coldchainai.payment.interfaces.tool.DepositPaymentQueryTool;
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

        当前已经接入以下真实业务工具：
        1. query_driver_deal_orders：查询司机指定日期的成交订单。
        2. query_order_deposit_payment：查询冷运订单最新的定金支付状态。

        当前尚未接入公司业务规则知识库、退款查询和支付渠道主动查单工具。

        请遵守以下规则：
        1. 回答必须准确、清晰、简洁。
        2. 用户询问指定司机今天或某天是否存在成交订单、成交了哪些订单时，必须调用query_driver_deal_orders。
        3. 用户询问某订单是否支付定金、定金是否到账、支付中、支付超时、支付失败或尚未创建支付单时，必须调用query_order_deposit_payment。
        4. 订单和支付事实必须以工具返回结果为准，禁止根据示例数据、常识或历史对话编造。
        5. 支付Tool返回payOrderCreated=false时，应明确说明该订单尚未创建定金支付单，不能说成支付失败。
        6. 支付Tool返回paid=true时，可以明确说明定金已经支付成功。
        7. 支付Tool返回paying=true且expired=false时，应说明当前仍在支付处理中，最终结果尚未确认。
        8. 支付Tool返回paying=true且expired=true时，应说明支付单已经超过失效时间但数据库状态仍为支付中，可能等待补偿关闭，不能声称支付成功。
        9. Tool返回success=false时，应根据errorMessage说明参数问题，不能伪造业务结果。
        10. 不得声称已经查询尚未接入的退款、支付渠道或公司知识库能力。
        11. 不确定的信息必须明确说明不确定，不能编造答案。
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
     * <p>当前ChatClient绑定司机成交订单查询和定金支付查询两个Tool。
     * 后续新增其他Agent时，需要根据Agent能力和权限边界独立绑定Tool，
     * 不能把全部工具无条件暴露给所有Agent。</p>
     *
     * <p>在挖矿流程中，该方法相当于给综合矿区的智能挖掘机配置两台专业设备：
     * 一台查询订单档案，一台查询财务收款单。缺少绑定时，模型只能知道工具存在于代码中，
     * 却无法在实际任务中调用。</p>
     *
     * @param chatClientBuilder Spring AI自动配置的ChatClient构建器
     * @param agentLifecycleLoggingAdvisor Agent完整调用链日志Advisor
     * @param modelLifecycleLoggingAdvisor 单次模型调用日志Advisor
     * @param driverOrderQueryTool 司机成交订单查询Tool
     * @param depositPaymentQueryTool 订单定金支付查询Tool
     * @return 综合业务助手专属ChatClient
     */
    @Bean
    public ChatClient coldChainGeneralChatClient(ChatClient.Builder chatClientBuilder, AgentLifecycleLoggingAdvisor agentLifecycleLoggingAdvisor,
                                                 ModelLifecycleLoggingAdvisor modelLifecycleLoggingAdvisor,
                                                 DriverOrderQueryTool driverOrderQueryTool,
                                                 DepositPaymentQueryTool depositPaymentQueryTool) {
        /*
         * defaultTools注册后，这两个Tool会成为cold-chain-general的默认能力。
         * 当前ChatClient每次请求都可以由模型根据问题选择是否调用其中一个Tool。
         */
        return chatClientBuilder
                .defaultSystem(COLD_CHAIN_GENERAL_AGENT_SYSTEM_PROMPT)
                .defaultAdvisors(agentLifecycleLoggingAdvisor, modelLifecycleLoggingAdvisor)
                .defaultTools(driverOrderQueryTool, depositPaymentQueryTool)
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
