package com.ymm.coldchainai.agent.conversation.infrastructure.persistence.dataobject;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI Agent会话数据库持久化对象。
 *
 * <p>该类只负责映射cold_chain_ai_conversation表，
 * 不承载会话业务规则，业务行为统一放在AgentConversation领域模型中。</p>
 */
@Getter
@Setter
public class AgentConversationDO {

    /**
     * 数据库主键。
     */
    private Long id;

    /**
     * 会话业务唯一标识。
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
     * 会话绑定Agent编码。
     */
    private String agentCode;

    /**
     * 会话标题。
     */
    private String conversationTitle;

    /**
     * 会话状态码。
     */
    private Integer conversationStatus;

    /**
     * 累计消息数量。
     */
    private Integer messageCount;

    /**
     * 最近消息时间。
     */
    private LocalDateTime lastMessageTime;

    /**
     * 乐观锁版本号。
     */
    private Integer version;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;
}
