package com.ymm.coldchainai.agent.conversation.interfaces.web.response;

import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatMessageDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 *场景一：Conversation 1（Java后端学习）
 *
 * +----------------+------------------------+--------------+------------+
 * | messageId      | conversationId         | requestId    | sequenceNo |
 * +----------------+------------------------+--------------+------------+
 * | msg-java-001   | conversation-java-001  | request-001  | 1          |
 * | msg-java-002   | conversation-java-001  | request-001  | 2          |
 * | msg-java-003   | conversation-java-001  | request-002  | 3          |
 * | msg-java-004   | conversation-java-001  | request-002  | 4          |
 * | msg-java-005   | conversation-java-001  | request-003  | 5          |
 * | msg-java-006   | conversation-java-001  | request-003  | 6          |
 * | msg-java-007   | conversation-java-001  | request-004  | 7          |
 * | msg-java-008   | conversation-java-001  | request-004  | 8          |
 * | msg-java-009   | conversation-java-001  | request-005  | 9          |
 * | msg-java-010   | conversation-java-001  | request-005  | 10         |
 * +----------------+------------------------+--------------+------------+
 *
 *
 * 场景二：Conversation 2（AI Agent项目设计）
 *
 * +----------------+-------------------------+--------------+------------+
 * | messageId      | conversationId          | requestId    | sequenceNo |
 * +----------------+-------------------------+--------------+------------+
 * | msg-agent-001  | conversation-agent-001  | request-101  | 1          |
 * | msg-agent-002  | conversation-agent-001  | request-101  | 2          |
 * | msg-agent-003  | conversation-agent-001  | request-102  | 3          |
 * | msg-agent-004  | conversation-agent-001  | request-102  | 4          |
 * | msg-agent-005  | conversation-agent-001  | request-103  | 5          |
 * | msg-agent-006  | conversation-agent-001  | request-103  | 6          |
 * | msg-agent-007  | conversation-agent-001  | request-104  | 7          |
 * | msg-agent-008  | conversation-agent-001  | request-104  | 8          |
 * | msg-agent-009  | conversation-agent-001  | request-105  | 9          |
 * | msg-agent-010  | conversation-agent-001  | request-105  | 10         |
 * +----------------+-------------------------+--------------+------------+
 *
 * 字段含义：
 *
 * messageId：
 * 每一条消息自己的唯一编号。
 * 用户一句话和AI一句回答，都拥有自己的messageId。
 *
 * conversationId：
 * 代表一个完整聊天窗口。
 * 同一个聊天窗口里面的所有消息conversationId相同。
 * 不同聊天窗口可以拥有不同sequenceNo。
 *
 * requestId：
 * 代表一次完整Agent执行请求。
 * 通常一次用户提问 + 一次AI回答，共享同一个requestId。
 * 用于关联日志、模型调用、Tool调用和异常排查。
 *
 * sequenceNo：
 * 代表当前Conversation内部消息顺序。
 * 同一个conversationId内必须连续递增。
 * 不同conversationId可以都从1开始。
 *
 */

/**
 * Chat History追加消息验证响应。
 *
 * <p>该响应返回消息业务ID、角色和sequenceNo，
 * 方便通过Postman直接验证同一Conversation中的消息顺序是否连续。</p>
 */
@Getter
@AllArgsConstructor
public class AgentChatMessageResponse {

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
     * 消息角色码。
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
     * 消息在当前Conversation中的顺序号。
     */
    private final Integer sequenceNo;

    /**
     * 消息创建时间。
     */
    private final LocalDateTime createTime;

    /**
     * 将Application DTO转换为local验证接口响应。
     *
     * @param chatMessageDTO 聊天消息Application DTO
     * @return HTTP响应对象
     */
    public static AgentChatMessageResponse fromDTO(AgentChatMessageDTO chatMessageDTO) {
        if (Objects.isNull(chatMessageDTO)) {
            throw new IllegalArgumentException("Agent聊天消息DTO不能为空");
        }

        return new AgentChatMessageResponse(
                chatMessageDTO.getMessageId(),
                chatMessageDTO.getConversationId(),
                chatMessageDTO.getRequestId(),
                chatMessageDTO.getMessageRoleCode(),
                chatMessageDTO.getMessageRoleMessage(),
                chatMessageDTO.getMessageContent(),
                chatMessageDTO.getSequenceNo(),
                chatMessageDTO.getCreateTime());
    }
}
