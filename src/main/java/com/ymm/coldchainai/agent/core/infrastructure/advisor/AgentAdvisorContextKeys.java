package com.ymm.coldchainai.agent.core.infrastructure.advisor;

import lombok.experimental.UtilityClass;

/**
 * Agent Advisor上下文键。
 *
 * <p>该类统一维护传递给Spring AI Advisor的上下文字段名称，
 * 避免执行器和多个Advisor分别手写字符串导致字段不一致。</p>
 *
 * <p>Advisor上下文只用于当前ChatClient执行链中的技术信息传递，
 * 不会自动拼接到用户Prompt中，也不会直接发送给大模型。</p>
 */
@UtilityClass
public class AgentAdvisorContextKeys {

    /**
     * Agent请求唯一标识对应的Advisor上下文键。
     */
    public static final String REQUEST_ID = "coldchain.agent.request-id";

    /**
     * Agent稳定编码对应的Advisor上下文键。
     */
    public static final String AGENT_CODE = "coldchain.agent.code";

    /**
     * Agent展示名称对应的Advisor上下文键。
     */
    public static final String AGENT_NAME = "coldchain.agent.name";

    /**
     * SLF4J MDC中的请求标识字段名称。
     *
     * <p>该名称与logback-spring.xml中的%X{requestId}保持一致。</p>
     */
    public static final String MDC_REQUEST_ID = "requestId";

    /**
     * SLF4J MDC中的Agent编码字段名称。
     *
     * <p>该名称与logback-spring.xml中的%X{agentCode}保持一致。</p>
     */
    public static final String MDC_AGENT_CODE = "agentCode";
}