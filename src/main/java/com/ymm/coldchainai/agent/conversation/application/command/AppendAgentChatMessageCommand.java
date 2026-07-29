package com.ymm.coldchainai.agent.conversation.application.command;

import com.ymm.coldchainai.agent.conversation.domain.enumtype.ChatMessageRoleEnum;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 向Conversation追加一条聊天消息的Application命令。
 *
 * <p>该命令携带会话标识、Agent请求标识、消息角色、消息正文以及受信任调用上下文。
 * currentUserId和currentTenantId不能由前端直接传入，必须从认证上下文获得。</p>
 *
 * <p>在挖矿流程中，该命令相当于一张“新增作业记录申请单”：
 * conversationId指定项目任务单，requestId指定本次开采任务，
 * messageRole说明记录来自客户还是矿场，messageContent是实际记录内容。</p>
 */
@Getter
public class AppendAgentChatMessageCommand {

    /**
     * 消息所属Conversation业务唯一标识。
     */
    private final String conversationId;

    /**
     * 产生当前消息的Agent请求唯一标识。
     *
     * <p>同一轮USER问题和ASSISTANT回答应使用相同requestId，
     * 便于后续关联AgentExecution和ToolExecution审计记录。</p>
     */
    private final String requestId;

    /**
     * 当前消息角色。
     */
    private final ChatMessageRoleEnum messageRole;

    /**
     * 当前消息完整正文。
     */
    private final String messageContent;

    /**
     * 当前受信任用户和租户上下文。
     */
    private final AgentInvocationContext agentInvocationContext;

    /**
     * 创建追加聊天消息命令。
     *
     * @param conversationId 会话业务唯一标识
     * @param requestId Agent请求唯一标识
     * @param messageRole 消息角色
     * @param messageContent 消息正文
     * @param agentInvocationContext 受信任调用上下文
     * @return 已完成基础参数校验的Application命令
     */
    public static AppendAgentChatMessageCommand create(
            String conversationId,
            String requestId,
            ChatMessageRoleEnum messageRole,
            String messageContent,
            AgentInvocationContext agentInvocationContext) {

        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("会话标识不能为空");
        }

        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("Agent请求标识不能为空");
        }

        if (Objects.isNull(messageRole)) {
            throw new IllegalArgumentException("聊天消息角色不能为空");
        }

        if (StringUtils.isBlank(messageContent)) {
            throw new IllegalArgumentException("聊天消息正文不能为空");
        }

        if (Objects.isNull(agentInvocationContext)) {
            throw new IllegalArgumentException("Agent调用上下文不能为空");
        }

        return new AppendAgentChatMessageCommand(
                StringUtils.trim(conversationId),
                StringUtils.trim(requestId),
                messageRole,
                messageContent,
                agentInvocationContext);
    }

    /**
     * 创建追加聊天消息命令。
     */
    private AppendAgentChatMessageCommand(
            String conversationId,
            String requestId,
            ChatMessageRoleEnum messageRole,
            String messageContent,
            AgentInvocationContext agentInvocationContext) {
        this.conversationId = conversationId;
        this.requestId = requestId;
        this.messageRole = messageRole;
        this.messageContent = messageContent;
        this.agentInvocationContext = agentInvocationContext;
    }
}
