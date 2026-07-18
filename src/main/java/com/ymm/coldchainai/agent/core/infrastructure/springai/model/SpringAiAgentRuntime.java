package com.ymm.coldchainai.agent.core.infrastructure.springai.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.ai.chat.client.ChatClient;

/**
 * Spring AI Agent运行配置。
 *
 * <p>该对象负责将一个稳定的agentCode与具体ChatClient绑定，
 * 使不同Agent可以拥有不同的系统提示词、模型参数和后续Tool配置。</p>
 *
 * <p>该对象属于Infrastructure层，因为它直接依赖Spring AI的ChatClient。
 * Domain层的AgentDefinition不能直接持有ChatClient，否则领域模型将依赖具体AI框架。</p>
 *
 * <p>当前阶段只绑定ChatClient。后续接入Tool Calling时，可以继续为运行配置增加
 * Tool提供器、Advisor配置或其他Spring AI执行参数，而不污染AgentDefinition。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class SpringAiAgentRuntime {

    /**
     * 当前运行配置对应的Agent编码。
     *
     * <p>该编码必须与AgentDefinition中的agentCode保持一致。</p>
     */
    private final String agentCode;

    /**
     * 当前Agent专属的ChatClient。
     *
     * <p>ChatClient内部已经包含当前Agent的默认系统提示词，
     * 后续也可以包含当前Agent专属的Advisor等基础配置。</p>
     */
    private final ChatClient chatClient;
}