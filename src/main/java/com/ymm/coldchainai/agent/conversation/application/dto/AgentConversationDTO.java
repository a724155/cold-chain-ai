package com.ymm.coldchainai.agent.conversation.application.dto;

import com.ymm.coldchainai.agent.conversation.domain.model.AgentConversation;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * Agent会话Application返回DTO。
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentConversationDTO {

    /**
     * 会话业务唯一标识。
     */
    private final String conversationId;

    /**
     * 当前会话绑定Agent编码。
     */
    private final String agentCode;

    /**
     * 当前会话标题。
     */
    private final String conversationTitle;

    /**
     * 当前累计消息数量。
     */
    private final Integer messageCount;

    /**
     * 是否为本次请求新创建的Conversation。
     */
    private final Boolean newlyCreated;

    /**
     * 将领域模型转换为Application DTO。
     *
     * @param conversation Agent会话领域模型
     * @param newlyCreated 是否本次新建
     * @return Conversation DTO
     */
    public static AgentConversationDTO fromDomain(AgentConversation conversation, Boolean newlyCreated) {
        if (Objects.isNull(conversation)) {
            throw new IllegalArgumentException("Agent会话领域对象不能为空");
        }

        return AgentConversationDTO.of(
                conversation.getConversationId(),
                conversation.getAgentCode(),
                conversation.getConversationTitle(),
                conversation.getMessageCount(),
                Boolean.TRUE.equals(newlyCreated));
    }
}
