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

    /**
     * 根据会话标识和数据所有者加悲观锁查询Agent会话。
     *
     * <p>该方法专门用于“向Conversation追加ChatMessage”这类读后写场景。
     * 调用方需要先读取当前messageCount，再计算下一条消息的sequenceNo，
     * 最后保存ChatMessage并更新Conversation消息统计。</p>
     *
     * <p>如果不加锁，同一个Conversation同时收到两个请求时，两个线程可能都读取到messageCount=4，
     * 随后都计算出sequenceNo=5，最终触发uk_conversation_sequence唯一索引冲突，
     * 或者导致其中一条消息无法正常写入。</p>
     *
     * <p>该方法底层使用SELECT ... FOR UPDATE悲观锁。
     * FOR UPDATE锁定的是当前事务查询到的Conversation记录，
     * 锁不会在Mapper方法执行完后立即释放，而是在外围Spring事务提交或者回滚时释放。</p>
     *
     * <p>因此该方法必须在带有@Transactional的Application Service方法中调用。
     * 如果没有外围事务，数据库可能在SQL执行结束后立即提交并释放锁，
     * 后续保存ChatMessage和更新messageCount时就失去了并发保护意义。</p>
     *
     * <p>在挖矿流程中，该锁相当于档案管理员暂时锁住一张项目总任务单：
     * 当前工作人员读取任务单上的作业记录数量、登记新记录并更新总数期间，
     * 其他工作人员不能同时拿走同一张任务单计算新的记录编号。</p>
     *
     * @param conversationId 会话业务唯一标识，用于定位目标Conversation
     * @param currentUserId 当前受信任用户ID，用于校验会话数据所有权
     * @param currentTenantId 当前受信任租户ID，用于完成多租户数据隔离
     * @return 找到时返回已经在当前事务中加锁的会话领域对象，否则返回Optional.empty()
     */
    Optional<AgentConversation> findByConversationIdAndOwnerForUpdate(String conversationId, Long currentUserId, Long currentTenantId);

    /**
     * 更新Conversation累计消息数量和最近消息时间。
     *
     * <p>调用该方法前，Application Service应先调用AgentConversation.recordNewMessage()，
     * 由领域模型完成ACTIVE状态校验、messageCount递增和lastMessageTime更新，
     * Repository只负责把已经通过领域规则计算出的结果写入数据库。</p>
     *
     * <p>底层SQL同时使用version作为乐观锁条件。
     * 当前设计通过FOR UPDATE悲观锁保护主要并发链路，
     * 再通过version乐观锁防御绕过加锁查询或者未来错误调用造成的并发覆盖。</p>
     *
     * <p>在挖矿流程中，AgentConversation领域模型先在任务单上登记一条新作业，
     * Repository再由档案管理员将任务单最新的作业总数和最后作业时间同步到档案库。</p>
     *
     * @param conversation 已完成消息统计更新的Agent会话领域对象
     */
    void updateMessageStatistics(AgentConversation conversation);
}
