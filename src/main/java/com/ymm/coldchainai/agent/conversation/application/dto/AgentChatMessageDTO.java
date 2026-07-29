package com.ymm.coldchainai.agent.conversation.application.dto;

import com.ymm.coldchainai.agent.conversation.domain.model.AgentChatMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Agent聊天消息Application返回DTO。
 *
 * <p>该DTO是Application层对外提供的稳定消息结果，不直接暴露AgentChatMessage领域对象或者MyBatis DO。</p>
 *
 * <p>在挖矿流程中，该DTO相当于档案管理员完成归档后开具的回执，用于告诉上层本次记录的编号、顺序、角色和归档时间。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentChatMessageDTO {

    /**
     * 消息业务唯一标识。
     */
    private final String messageId;

    /**
     * 消息所属Conversation业务唯一标识。
     */
    private final String conversationId;

    /**
     * 产生当前消息的Agent请求标识。
     */
    private final String requestId;

    /**
     * 消息角色数据库编码。
     */
    private final Integer messageRoleCode;

    /**
     * 消息角色说明。
     */
    private final String messageRoleMessage;

    /**
     * 消息完整正文。
     */
    private final String messageContent;

    /**
     * 当前消息在Conversation中的顺序号。
     */
    private final Integer sequenceNo;

    /**
     * 消息创建时间。
     */
    private final LocalDateTime createTime;

    /**
     * 将聊天消息领域模型转换成Application DTO。
     *
     * @param chatMessage 聊天消息领域模型
     * @return Application层消息DTO
     */
    public static AgentChatMessageDTO fromDomain(AgentChatMessage chatMessage) {
        if (Objects.isNull(chatMessage)) {
            throw new IllegalArgumentException("Agent聊天消息领域对象不能为空");
        }

        if (Objects.isNull(chatMessage.getMessageRole())) {
            throw new IllegalArgumentException("Agent聊天消息角色不能为空");
        }

        return AgentChatMessageDTO.of(
                chatMessage.getMessageId(),
                chatMessage.getConversationId(),
                chatMessage.getRequestId(),
                chatMessage.getMessageRole().getCode(),
                chatMessage.getMessageRole().getMessage(),
                chatMessage.getMessageContent(),
                chatMessage.getSequenceNo(),
                chatMessage.getCreateTime());
    }
}
