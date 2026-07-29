package com.ymm.coldchainai.agent.conversation.domain.model;

import com.ymm.coldchainai.agent.conversation.domain.enumtype.ConversationStatusEnum;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * AI Agent会话领域模型。
 *
 * <p>Conversation代表用户与某个Agent之间的一整个连续聊天窗口，
 * 它不是某一次HTTP请求，也不是某一条ChatMessage。</p>
 *
 * <p>同一个Conversation内部可以产生多个requestId和多轮用户/AI消息，
 * 但conversationId、用户、租户和agentCode在会话生命周期内保持稳定。</p>
 *
 * <p>在挖矿流程中，该对象相当于一张长期项目任务单：
 * conversationId是任务单编号，用户和租户是客户身份，
 * agentCode是固定负责该项目的矿区，messageCount表示已经产生多少条作业记录。</p>
 */
@Getter
public class AgentConversation {

    /**
     * 数据库内部主键。
     *
     * <p>新建领域对象尚未落库时允许为空。</p>
     */
    private Long id;

    /**
     * 对外使用的会话业务唯一标识。
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
     * 当前会话固定绑定的Agent编码。
     */
    private String agentCode;

    /**
     * 会话标题。
     *
     * <p>初次创建允许为空，后续可以根据第一轮问题生成或者由用户修改。</p>
     */
    private String conversationTitle;

    /**
     * 当前会话状态。
     */
    private ConversationStatusEnum conversationStatus;

    /**
     * 当前会话累计消息数量。
     */
    private Integer messageCount;

    /**
     * 当前会话最近一条消息产生时间。
     */
    private LocalDateTime lastMessageTime;

    /**
     * 乐观锁版本号。
     */
    private Integer version;

    /**
     * 数据创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 数据最后更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 创建一个新的Agent会话。
     *
     * <p>新会话默认处于ACTIVE状态，消息数量从0开始，
     * 数据库主键、创建时间和更新时间由持久化层落库后补充。</p>
     *
     * @param conversationId 新会话业务唯一标识
     * @param currentUserId 当前用户ID
     * @param currentTenantId 当前租户ID
     * @param agentCode 当前会话绑定的Agent编码
     * @return 新建Agent会话
     */
    public static AgentConversation create(String conversationId, Long currentUserId, Long currentTenantId, String agentCode) {
        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("会话标识不能为空");
        }

        if (Objects.isNull(currentUserId)) {
            throw new IllegalArgumentException("当前用户ID不能为空");
        }

        if (Objects.isNull(currentTenantId)) {
            throw new IllegalArgumentException("当前租户ID不能为空");
        }

        if (StringUtils.isBlank(agentCode)) {
            throw new IllegalArgumentException("会话绑定的Agent编码不能为空");
        }

        AgentConversation conversation = new AgentConversation();

        // 会话业务标识一旦创建便保持不变，后续所有ChatMessage都通过该标识归属到同一聊天窗口。
        conversation.conversationId = StringUtils.trim(conversationId);

        // 用户和租户共同确定会话数据所有权，后续查询必须同时校验，防止水平越权。
        conversation.currentUserId = currentUserId;
        conversation.currentTenantId = currentTenantId;

        // 一个会话生命周期内固定绑定一个Agent，避免多轮上下文在不同Agent之间串用。
        conversation.agentCode = StringUtils.trim(agentCode);

        // 新会话尚未产生用户消息和AI回答，因此消息数量从0开始。
        conversation.messageCount = 0;

        // 新建会话默认允许继续问答。
        conversation.conversationStatus = ConversationStatusEnum.ACTIVE;

        // 新领域对象尚未执行数据库更新，因此乐观锁初始版本从0开始。
        conversation.version = 0;

        return conversation;
    }

    /**
     * 根据数据库持久化数据恢复已经存在的Agent会话。
     *
     * <p>该方法只供Repository持久化适配层使用，不代表创建一个新会话。
     * 数据库中的主键、状态、消息数量、版本号以及时间字段都会按照持久化结果恢复。</p>
     *
     * <p>create()负责“创建新项目”，restore()负责“从档案库恢复已经存在的项目”，
     * 二者语义不能混用。</p>
     *
     * @param id 数据库主键
     * @param conversationId 会话业务唯一标识
     * @param currentUserId 会话所属用户ID
     * @param currentTenantId 会话所属租户ID
     * @param agentCode 会话绑定Agent编码
     * @param conversationTitle 会话标题
     * @param conversationStatus 会话状态
     * @param messageCount 累计消息数量
     * @param lastMessageTime 最近消息时间
     * @param version 乐观锁版本
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 从数据库恢复完成的会话领域对象
     */
    public static AgentConversation restore(
            Long id,
            String conversationId,
            Long currentUserId,
            Long currentTenantId,
            String agentCode,
            String conversationTitle,
            ConversationStatusEnum conversationStatus,
            Integer messageCount,
            LocalDateTime lastMessageTime,
            Integer version,
            LocalDateTime createTime,
            LocalDateTime updateTime) {

        if (Objects.isNull(id)) {
            throw new IllegalArgumentException("会话数据库主键不能为空");
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

        if (StringUtils.isBlank(agentCode)) {
            throw new IllegalArgumentException("会话绑定的Agent编码不能为空");
        }

        if (Objects.isNull(conversationStatus)) {
            throw new IllegalArgumentException("会话状态不能为空");
        }

        AgentConversation conversation = new AgentConversation();

        // 恢复数据库已经存在的会话主键和稳定业务标识。
        conversation.id = id;
        conversation.conversationId = conversationId;

        // 恢复会话数据所有权，后续Repository查询必须继续使用用户和租户共同校验。
        conversation.currentUserId = currentUserId;
        conversation.currentTenantId = currentTenantId;

        // 恢复当前会话固定绑定的Agent以及可变展示标题。
        conversation.agentCode = agentCode;
        conversation.conversationTitle = conversationTitle;

        // 恢复会话生命周期状态和消息统计信息。
        conversation.conversationStatus = conversationStatus;
        conversation.messageCount = Objects.isNull(messageCount) ? 0 : messageCount;
        conversation.lastMessageTime = lastMessageTime;

        // 恢复乐观锁版本以及数据库审计时间。
        conversation.version = Objects.isNull(version) ? 0 : version;
        conversation.createTime = createTime;
        conversation.updateTime = updateTime;

        return conversation;
    }

    /**
     * 更新会话标题。
     *
     * @param conversationTitle 新会话标题
     */
    public void updateTitle(String conversationTitle) {
        if (StringUtils.isBlank(conversationTitle)) {
            throw new IllegalArgumentException("会话标题不能为空");
        }

        this.conversationTitle = StringUtils.trim(conversationTitle);
    }

    /**
     * 计算当前Conversation下一条ChatMessage应使用的顺序号。
     *
     * <p>该方法必须在Conversation已经通过SELECT ... FOR UPDATE加锁后调用。
     * messageCount表示当前已经成功持久化的消息数量，因此下一条消息的sequenceNo等于messageCount + 1。</p>
     *
     * <p>不能简单执行SELECT MAX(sequence_no) + 1：
     * 一方面每次都需要扫描或者查询消息表；
     * 另一方面同一Conversation发生并发请求时，两个事务可能同时读取到相同MAX值，
     * 从而生成重复sequenceNo。</p>
     *
     * <p>当前实现通过锁定Conversation主记录，将同一会话的“读取messageCount → 生成sequenceNo
     * → 保存消息 → 更新messageCount”串行化，避免uk_conversation_sequence唯一索引冲突。</p>
     *
     * <p>这与订单场景中的SELECT ... FOR UPDATE原理相同：
     * 都是在一个本地事务中保护“先读取、再判断、最后更新”的读后写链路。
     * 区别是订单加锁通常保护订单状态流转、防止重复支付处理、重复扣减或者资损；
     * Conversation加锁主要保护ChatMessage顺序号和messageCount统计一致性。</p>
     *
     * <p>在挖矿流程中，这相当于档案管理员锁定项目总任务单后，
     * 根据任务单上已有作业记录数量分配下一张记录编号，避免两个工作人员同时拿到同一个编号。</p>
     *
     * @return 下一条消息在当前Conversation中的顺序号
     */
    public Integer calculateNextMessageSequenceNo() {
        if (!isActive()) {
            throw new IllegalStateException("已关闭会话不能继续追加消息");
        }

        // 历史异常数据出现null时按0处理，但负数说明数据库数据已经损坏，必须立即暴露。
        int currentMessageCount = Objects.isNull(messageCount) ? 0 : messageCount;

        if (currentMessageCount < 0) {
            throw new IllegalStateException("会话消息数量不能小于0，messageCount=%s".formatted(currentMessageCount));
        }

        return currentMessageCount + 1;
    }

    /**
     * 记录当前会话新增一条聊天消息。
     *
     * <p>无论是USER消息还是ASSISTANT消息，都属于Conversation内部的一条ChatMessage，
     * 因此成功持久化一条消息后调用一次该方法。</p>
     *
     * @param messageTime 新消息实际产生时间
     */
    public void recordNewMessage(LocalDateTime messageTime) {
        if (conversationStatus != ConversationStatusEnum.ACTIVE) {
            throw new IllegalStateException("已关闭会话不能继续追加消息");
        }

        if (Objects.isNull(messageTime)) {
            throw new IllegalArgumentException("消息时间不能为空");
        }

        // messageCount理论上创建时一定为0，但仍然对历史异常数据进行防御，避免执行加法时产生NPE。
        this.messageCount = Objects.isNull(this.messageCount) ? 1 : this.messageCount + 1;

        // 最近消息时间始终更新为本次成功写入消息的时间，供会话列表按照最近聊天时间排序。
        this.lastMessageTime = messageTime;
    }

    /**
     * 关闭当前会话。
     *
     * <p>关闭后的会话保留历史数据，但不再允许追加新的ChatMessage。</p>
     */
    public void close() {
        this.conversationStatus = ConversationStatusEnum.CLOSED;
    }

    /**
     * 判断当前会话是否仍然允许继续聊天。
     *
     * @return true表示会话仍处于进行中
     */
    public boolean isActive() {
        return conversationStatus == ConversationStatusEnum.ACTIVE;
    }
}
