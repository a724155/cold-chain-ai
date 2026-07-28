package com.ymm.coldchainai.agent.conversation.application.command;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 获取或者创建Agent会话的应用命令。
 *
 * <p>conversationId允许为空：
 * 为空表示用户准备开启一个全新聊天窗口；非空表示用户准备继续已有Conversation。</p>
 *
 * <p>currentUserId和currentTenantId不接受前端直接传入，
 * 而是继续复用认证链路产生的AgentInvocationContext，避免用户伪造身份。</p>
 */
@Getter
public class ResolveAgentConversationCommand {

    /**
     * 已存在的会话标识，新会话允许为空。
     */
    private final String conversationId;

    /**
     * 本次聊天使用的Agent编码。
     */
    private final String agentCode;

    /**
     * 本次请求受信任用户和租户上下文。
     */
    private final AgentInvocationContext agentInvocationContext;

    /**
     * 创建获取或者创建Conversation的应用命令。
     *
     * @param conversationId 已有会话标识，新会话允许为空
     * @param agentCode Agent编码
     * @param agentInvocationContext 受信任调用上下文
     * @return 标准Application Command
     */
    public static ResolveAgentConversationCommand create(
            String conversationId,
            String agentCode,
            AgentInvocationContext agentInvocationContext) {

        if (StringUtils.isBlank(agentCode)) {
            throw new IllegalArgumentException("Agent编码不能为空");
        }

        if (Objects.isNull(agentInvocationContext)) {
            throw new IllegalArgumentException("Agent调用上下文不能为空");
        }

        return new ResolveAgentConversationCommand(StringUtils.trimToNull(conversationId), StringUtils.trim(agentCode), agentInvocationContext);
    }

    /**
     * 创建应用命令。
     */
    private ResolveAgentConversationCommand(String conversationId, String agentCode, AgentInvocationContext agentInvocationContext) {
        this.conversationId = conversationId;
        this.agentCode = agentCode;
        this.agentInvocationContext = agentInvocationContext;
    }
}
