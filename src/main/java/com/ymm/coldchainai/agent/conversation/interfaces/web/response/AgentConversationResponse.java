package com.ymm.coldchainai.agent.conversation.interfaces.web.response;


import com.ymm.coldchainai.agent.conversation.application.dto.AgentConversationDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * Agent Conversation验证响应。
 */
@Getter
@AllArgsConstructor
public class AgentConversationResponse {

    /**
     * 当前Conversation业务唯一标识。
     */
    private final String conversationId;

    /**
     * 当前会话绑定Agent。
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
     * 本次是否新建Conversation。
     */
    private final Boolean newlyCreated;

    /**
     * 将Application DTO转换成接口响应。
     *
     * @param conversationDTO Conversation DTO
     * @return HTTP响应
     */
    public static AgentConversationResponse fromDTO(AgentConversationDTO conversationDTO) {
        if (Objects.isNull(conversationDTO)) {
            throw new IllegalArgumentException("Agent会话DTO不能为空");
        }

        return new AgentConversationResponse(
                conversationDTO.getConversationId(),
                conversationDTO.getAgentCode(),
                conversationDTO.getConversationTitle(),
                conversationDTO.getMessageCount(),
                conversationDTO.getNewlyCreated());
    }
}