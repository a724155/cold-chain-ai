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
    AgentConversationDO selectByConversationIdAndOwner(@Param("conversationId") String conversationId, @Param("currentUserId") Long currentUserId,
                                                       @Param("currentTenantId") Long currentTenantId);

    /**
     * 根据会话标识、用户和租户加悲观锁查询Conversation。
     *
     * <p>底层SQL使用SELECT ... FOR UPDATE锁定满足条件的Conversation行。
     * 该方法用于追加ChatMessage前读取最新messageCount和version，
     * 防止同一Conversation的多个并发请求生成重复sequenceNo。</p>
     *
     * <p>该Mapper方法本身不会创建一个覆盖完整业务流程的事务。
     * 必须由上层带有@Transactional的方法统一包住：</p>
     *
     * <p>加锁查询Conversation
     * → 计算并保存ChatMessage
     * → 更新Conversation消息统计
     * → 提交事务并释放行锁。</p>
     *
     * <p>如果脱离事务单独调用，FOR UPDATE的锁可能在查询结束后很快释放，
     * 无法保护后面的消息插入和统计更新。</p>
     *
     * @param conversationId 会话业务唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @return 当前事务中已经加锁的Conversation数据库对象
     */
    AgentConversationDO selectByConversationIdAndOwnerForUpdate(@Param("conversationId") String conversationId, @Param("currentUserId") Long currentUserId,
                                                                @Param("currentTenantId") Long currentTenantId);

    /**
     * 使用乐观锁更新Conversation消息统计。
     *
     * <p>SQL根据数据库主键、conversationId、用户、租户和旧version共同更新，
     * 成功后将message_count写入最新值、更新last_message_time，并执行version=version+1。</p>
     *
     * <p>返回1表示当前版本匹配且更新成功；
     * 返回0通常表示Conversation已经不存在、数据所有权不匹配或者version发生并发变化。</p>
     *
     * @param conversationDO 包含最新消息数量、最近消息时间和旧version的Conversation数据库对象
     * @return 实际更新记录数，正常情况下必须为1
     */
    int updateMessageStatistics(AgentConversationDO conversationDO);
}
