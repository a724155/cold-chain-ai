package com.ymm.coldchainai.agent.conversation.domain.model;

import com.ymm.coldchainai.agent.conversation.domain.enumtype.ChatMessageRoleEnum;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Agent聊天消息领域模型。
 *
 * <p>该对象代表Conversation中的一条稳定消息，可以是用户提出的问题，
 * 也可以是Agent最终交付给用户的回答。</p>
 *
 * <p>在挖矿流程中，Conversation是一张长期项目任务单，
 * AgentChatMessage则是任务单中的单条作业记录：
 * sequenceNo说明这是第几条记录，requestId说明它来自哪次开采任务。</p>
 */
@Getter
public class AgentChatMessage {

    /**
     * 数据库内部主键，新消息尚未落库时允许为空。
     */
    private Long id;

    /**
     * 消息业务唯一标识。
     */
    private String messageId;

    /**
     * 消息所属Conversation业务标识。
     */
    private String conversationId;

    /**
     * 会话所属用户ID。
     */
    private Long currentUserId;

    /**
     * 会话所属租户ID。
     */
    private Long currentTenantId;

    /**
     * 产生当前消息的Agent请求标识。
     *
     * <p>同一轮用户问题和AI回答可以使用同一个requestId，
     * 便于后续根据一次Agent执行还原完整问答。</p>
     */
    private String requestId;

    /**
     * 当前消息角色。
     */
    private ChatMessageRoleEnum messageRole;

    /**
     * 当前消息完整正文。
     */
    private String messageContent;

    /**
     * 当前消息在Conversation中的顺序，从1开始。
     */
    private Integer sequenceNo;

    /**
     * 消息创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 创建一条新的聊天消息。
     *
     * @param messageId 消息业务标识
     * @param conversationId 会话业务标识
     * @param currentUserId 当前用户ID
     * @param currentTenantId 当前租户ID
     * @param requestId Agent请求标识
     * @param messageRole 消息角色
     * @param messageContent 消息正文
     * @param sequenceNo 当前Conversation内消息顺序
     * @param createTime 消息产生时间
     * @return 新聊天消息
     */
    public static AgentChatMessage create(
            String messageId,
            String conversationId,
            Long currentUserId,
            Long currentTenantId,
            String requestId,
            ChatMessageRoleEnum messageRole,
            String messageContent,
            Integer sequenceNo,
            LocalDateTime createTime) {

        validate(messageId, conversationId, currentUserId, currentTenantId, requestId, messageRole, messageContent, sequenceNo, createTime);

        AgentChatMessage chatMessage = new AgentChatMessage();

        // messageId相当于单条挖矿作业记录编号，用于日志、审计和问题定位。
        chatMessage.messageId = StringUtils.trim(messageId);

        // conversationId确定当前消息属于哪一张长期会话任务单。
        chatMessage.conversationId = StringUtils.trim(conversationId);

        // 用户和租户共同确定消息所有权，查询消息历史时三个条件缺一不可。
        chatMessage.currentUserId = currentUserId;
        chatMessage.currentTenantId = currentTenantId;

        // requestId把同一轮用户问题和AI回答关联到一次Agent执行。
        chatMessage.requestId = StringUtils.trim(requestId);

        // 消息角色决定该内容是用户要求还是Agent最终交付报告。
        chatMessage.messageRole = messageRole;
        chatMessage.messageContent = messageContent;
        chatMessage.sequenceNo = sequenceNo;
        chatMessage.createTime = createTime;

        return chatMessage;
    }

    /**
     * 根据数据库记录恢复已经存在的聊天消息。
     *
     * @param id 数据库主键
     * @param messageId 消息业务标识
     * @param conversationId 会话业务标识
     * @param currentUserId 当前用户ID
     * @param currentTenantId 当前租户ID
     * @param requestId Agent请求标识
     * @param messageRole 消息角色
     * @param messageContent 消息正文
     * @param sequenceNo 消息顺序
     * @param createTime 创建时间
     * @return 恢复完成的聊天消息
     */
    public static AgentChatMessage restore(
            Long id,
            String messageId,
            String conversationId,
            Long currentUserId,
            Long currentTenantId,
            String requestId,
            ChatMessageRoleEnum messageRole,
            String messageContent,
            Integer sequenceNo,
            LocalDateTime createTime) {

        if (Objects.isNull(id)) {
            throw new IllegalArgumentException("聊天消息数据库主键不能为空");
        }

        validate(messageId, conversationId, currentUserId, currentTenantId, requestId, messageRole, messageContent, sequenceNo, createTime);

        AgentChatMessage chatMessage = create(
                messageId,
                conversationId,
                currentUserId,
                currentTenantId,
                requestId,
                messageRole,
                messageContent,
                sequenceNo,
                createTime);

        chatMessage.id = id;

        return chatMessage;
    }

    /**
     * 校验聊天消息核心字段。
     */
    private static void validate(
            String messageId,
            String conversationId,
            Long currentUserId,
            Long currentTenantId,
            String requestId,
            ChatMessageRoleEnum messageRole,
            String messageContent,
            Integer sequenceNo,
            LocalDateTime createTime) {

        if (StringUtils.isBlank(messageId)) {
            throw new IllegalArgumentException("聊天消息标识不能为空");
        }

        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("会话标识不能为空");
        }

        if (Objects.isNull(currentUserId)) {
            throw new IllegalArgumentException("当前用户ID不能为空");
        }

        if (Objects.isNull(currentTenantId)) {
            throw new IllegalArgumentException("当前租户ID不能为空");
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

        if (Objects.isNull(sequenceNo) || sequenceNo <= 0) {
            throw new IllegalArgumentException("聊天消息顺序必须大于0");
        }

        if (Objects.isNull(createTime)) {
            throw new IllegalArgumentException("聊天消息创建时间不能为空");
        }
    }
}
