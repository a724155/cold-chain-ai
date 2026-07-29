package com.ymm.coldchainai.agent.conversation.infrastructure.persistence.repository;

import com.ymm.coldchainai.agent.conversation.domain.enumtype.ConversationStatusEnum;
import com.ymm.coldchainai.agent.conversation.domain.model.AgentConversation;
import com.ymm.coldchainai.agent.conversation.domain.repository.IAgentConversationRepository;
import com.ymm.coldchainai.agent.conversation.infrastructure.persistence.dataobject.AgentConversationDO;
import com.ymm.coldchainai.agent.conversation.infrastructure.persistence.mapper.IAgentConversationMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/**
 * Agent会话Repository MyBatis实现。
 *
 * <p>该类负责完成Domain模型与MyBatis DO之间的转换，
 * Domain层不会直接依赖Mapper或者数据库表结构。</p>
 *
 * <p>在挖矿流程中，该组件相当于档案仓库管理员：
 * Domain交给它的是完整项目任务单，它负责转换成数据库档案；
 * 查询时再把数据库档案恢复成领域对象。</p>
 */
@Repository
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentConversationRepositoryImpl implements IAgentConversationRepository {

    /**
     * Agent会话MyBatis Mapper。
     */
    private final IAgentConversationMapper agentConversationMapper;

    /**
     * 保存新创建的Agent会话。
     *
     * @param conversation 待保存会话
     */
    @Override
    public void save(AgentConversation conversation) {
        if (Objects.isNull(conversation)) {
            throw new IllegalArgumentException("待保存Agent会话不能为空");
        }

        // 将Domain领域对象转换成数据库持久化DO。
        AgentConversationDO conversationDO = convertToDO(conversation);

        // 新建Conversation理论上必须且只能插入一条记录。
        int insertCount = agentConversationMapper.insert(conversationDO);

        if (insertCount != 1) {
            throw new IllegalStateException("Agent会话保存失败，conversationId=%s".formatted(conversation.getConversationId()));
        }
    }

    /**
     * 根据会话标识以及用户、租户所有权查询Agent会话。
     *
     * <p>该方法用于普通读取场景，只获取当前用户拥有的Conversation数据，不对数据库记录加锁。</p>
     *
     * <p>适用于查看历史会话、展示聊天列表等只读场景。
     * 因为后续不会立即修改该Conversation，所以不需要占用数据库锁，避免降低系统并发能力。</p>
     *
     * @param conversationId 会话业务标识
     * @param currentUserId 当前用户ID
     * @param currentTenantId 当前租户ID
     * @return 找到时返回会话领域对象，否则返回Optional.empty()
     */
    @Override
    public Optional<AgentConversation> findByConversationIdAndOwner(String conversationId, Long currentUserId, Long currentTenantId) {
        validateOwnerQueryParameter(conversationId, currentUserId, currentTenantId);
        // 查询时同时校验conversationId、currentUserId和currentTenantId，保证不同用户和租户之间的数据隔离，避免水平越权读取其他人的会话。
        AgentConversationDO conversationDO = agentConversationMapper.selectByConversationIdAndOwner(
                StringUtils.trim(conversationId),currentUserId,currentTenantId);
        // 当前用户没有对应Conversation属于正常业务结果，因此返回Optional.empty()，不使用null表示不存在。
        if (Objects.isNull(conversationDO)) {
            return Optional.empty();
        }
        // Repository负责把数据库DO转换成领域对象，避免Application层直接依赖数据库结构。
        return Optional.of(convertToDomain(conversationDO));
    }

    /**
     * 加锁查询指定所有者的Agent会话。
     *
     * <p>该方法用于读取后马上修改Conversation的场景。底层SQL通常使用SELECT ... FOR UPDATE，对当前会话记录加数据库行锁。</p>
     *
     * <p>例如新增聊天消息时，需要更新Conversation最后消息时间、消息数量或状态。
     * 如果多个请求同时修改同一个Conversation，没有锁可能出现更新覆盖或消息顺序异常。</p>
     *
     * <p>调用该方法时必须处于有效数据库事务中，否则FOR UPDATE锁无法发挥预期效果。
     * 因此通常由Application Service开启短事务，查询加锁、修改数据、提交事务后释放锁。</p>
     *
     * @param conversationId 会话业务唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @return 找到时返回当前事务内已经锁定的会话领域对象
     */
    @Override
    public Optional<AgentConversation> findByConversationIdAndOwnerForUpdate(String conversationId, Long currentUserId, Long currentTenantId) {
        validateOwnerQueryParameter(conversationId, currentUserId, currentTenantId);
        // 查询当前用户拥有的Conversation，并通过FOR UPDATE锁住数据库记录，防止其他事务同时修改同一会话。
        AgentConversationDO conversationDO = agentConversationMapper.selectByConversationIdAndOwnerForUpdate(
                StringUtils.trim(conversationId),
                currentUserId,
                currentTenantId);

        if (Objects.isNull(conversationDO)) {
            return Optional.empty();
        }

        return Optional.of(convertToDomain(conversationDO));
    }

    /**
     * 更新Conversation消息数量、最近消息时间和乐观锁版本。
     *
     * <p>调用方必须先通过AgentConversation.recordNewMessage()修改领域状态，
     * Repository不能自行计算messageCount，否则会把业务规则重新散落到持久化层。</p>
     *
     * <p>在挖矿流程中，领域对象已经在项目任务单上登记了最新作业数量和作业时间，
     * 当前方法只是让档案管理员把任务单最新状态同步到MySQL档案库。</p>
     *
     * @param conversation 已完成消息统计变更的Agent会话领域对象
     */
    @Override
    public void updateMessageStatistics(AgentConversation conversation) {
        if (Objects.isNull(conversation)) {
            throw new IllegalArgumentException("待更新Agent会话不能为空");
        }

        AgentConversationDO conversationDO = convertToDO(conversation);

        // 更新记录数必须为1，否则说明版本冲突、数据所有权变化或者Conversation已经不存在。
        int updateCount = agentConversationMapper.updateMessageStatistics(conversationDO);

        if (updateCount != 1) {
            throw new IllegalStateException("Agent会话消息统计更新失败，conversationId=%s，version=%s"
                    .formatted(conversation.getConversationId(), conversation.getVersion()));
        }
    }

    /**
     * 校验按数据所有者查询Conversation的参数。
     */
    private void validateOwnerQueryParameter(String conversationId, Long currentUserId, Long currentTenantId) {
        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("会话标识不能为空");
        }

        if (Objects.isNull(currentUserId)) {
            throw new IllegalArgumentException("当前用户ID不能为空");
        }

        if (Objects.isNull(currentTenantId)) {
            throw new IllegalArgumentException("当前租户ID不能为空");
        }
    }

    /**
     * 将AgentConversation领域模型转换成数据库DO。
     *
     * @param conversation Agent会话领域对象
     * @return MyBatis持久化对象
     */
    private AgentConversationDO convertToDO(AgentConversation conversation) {
        AgentConversationDO conversationDO = new AgentConversationDO();

        conversationDO.setId(conversation.getId());
        conversationDO.setConversationId(conversation.getConversationId());
        conversationDO.setCurrentUserId(conversation.getCurrentUserId());
        conversationDO.setCurrentTenantId(conversation.getCurrentTenantId());
        conversationDO.setAgentCode(conversation.getAgentCode());
        conversationDO.setConversationTitle(conversation.getConversationTitle());
        conversationDO.setConversationStatus(conversation.getConversationStatus().getCode());
        conversationDO.setMessageCount(conversation.getMessageCount());
        conversationDO.setLastMessageTime(conversation.getLastMessageTime());
        conversationDO.setVersion(conversation.getVersion());
        conversationDO.setCreateTime(conversation.getCreateTime());
        conversationDO.setUpdateTime(conversation.getUpdateTime());

        return conversationDO;
    }

    /**
     * 将数据库DO恢复成AgentConversation领域模型。
     *
     * @param conversationDO MyBatis查询结果
     * @return 恢复完成的会话领域对象
     */
    private AgentConversation convertToDomain(AgentConversationDO conversationDO) {
        return AgentConversation.restore(
                conversationDO.getId(),
                conversationDO.getConversationId(),
                conversationDO.getCurrentUserId(),
                conversationDO.getCurrentTenantId(),
                conversationDO.getAgentCode(),
                conversationDO.getConversationTitle(),
                ConversationStatusEnum.fromCode(conversationDO.getConversationStatus()),
                conversationDO.getMessageCount(),
                conversationDO.getLastMessageTime(),
                conversationDO.getVersion(),
                conversationDO.getCreateTime(),
                conversationDO.getUpdateTime());
    }
}
