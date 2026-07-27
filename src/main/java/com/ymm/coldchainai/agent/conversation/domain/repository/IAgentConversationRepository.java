package com.ymm.coldchainai.agent.conversation.domain.repository;

import com.ymm.coldchainai.agent.conversation.domain.model.AgentConversation;

import java.util.Optional;

/**
 * Agent会话Repository领域端口。
 *
 * <p>Domain/Application层只依赖该接口，不关心底层最终使用MyBatis、MySQL还是其他存储实现。</p>
 *
 * <p>所有按conversationId查询会话的方法必须同时校验currentUserId和currentTenantId，
 * 禁止仅凭conversationId读取会话，避免发生水平越权。</p>
 */
public interface IAgentConversationRepository {

    /**
     * 保存新创建的Agent会话。
     *
     * @param conversation 待保存会话
     */
    void save(AgentConversation conversation);

    /**
     * 根据会话标识和数据所有者查询Agent会话。
     *
     * <p>conversationId只负责定位会话，currentUserId和currentTenantId负责确认调用者是否拥有该会话。</p>
     *
     * @param conversationId 会话业务标识
     * @param currentUserId 当前用户ID
     * @param currentTenantId 当前租户ID
     * @return 找到时返回会话，否则返回Optional.empty()
     */
    Optional<AgentConversation> findByConversationIdAndOwner(String conversationId, Long currentUserId, Long currentTenantId);
}
