package com.ymm.coldchainai.agent.core.application.memory.enumtype;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent上下文记忆消息角色。
 *
 * <p>该枚举属于Agent Core Application层，不依赖Spring AI的MessageType，
 * 从而避免Application层与具体AI框架绑定。</p>
 *
 * <p>当前Chat Memory只需要USER和ASSISTANT两种角色：
 * USER表示历史用户问题，ASSISTANT表示模型已经完成的历史回答。</p>
 *
 * <p>在挖矿流程中，USER相当于客户此前提交的开采要求，
 * ASSISTANT相当于矿场针对该要求已经交付的开采报告。</p>
 */
@Getter
@AllArgsConstructor
public enum AgentMemoryMessageRoleEnum {

    /**
     * 历史用户问题。
     */
    USER("用户"),

    /**
     * 历史Agent回答。
     */
    ASSISTANT("AI助手");

    /**
     * 记忆消息角色说明。
     */
    private final String message;
}
