package com.ymm.coldchainai.agent.conversation.infrastructure.persistence.mapper;

import com.ymm.coldchainai.agent.conversation.infrastructure.persistence.dataobject.AgentConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent会话MyBatis Mapper。
 *
 * <p>该Mapper只负责数据库SQL映射，不承担领域业务规则。</p>
 */
@Mapper
public interface IAgentConversationMapper {

    /**
     * 新增Agent会话。
     *
     * @param conversationDO 会话数据库对象
     * @return 实际新增记录数
     */
    int insert(AgentConversationDO conversationDO);

    /**
     * 根据conversationId、用户和租户共同查询会话。
     *
     * <p>三个条件缺一不可，禁止退化成只根据conversationId查询，
     * 否则其他用户拿到会话ID后可能读取不属于自己的聊天记录。</p>
     *
     * @param conversationId 会话业务标识
     * @param currentUserId 当前用户ID
     * @param currentTenantId 当前租户ID
     * @return 找到的会话数据
     */
    AgentConversationDO selectByConversationIdAndOwner(
            @Param("conversationId") String conversationId,
            @Param("currentUserId") Long currentUserId,
            @Param("currentTenantId") Long currentTenantId);
}
