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
     * @param conversationId 会话业务标识
     * @param currentUserId 当前用户ID
     * @param currentTenantId 当前租户ID
     * @return 找到时返回会话领域对象，否则返回Optional.empty()
     */
    @Override
    public Optional<AgentConversation> findByConversationIdAndOwner(String conversationId, Long currentUserId, Long currentTenantId) {
        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("会话标识不能为空");
        }

        if (Objects.isNull(currentUserId)) {
            throw new IllegalArgumentException("当前用户ID不能为空");
        }

        if (Objects.isNull(currentTenantId)) {
            throw new IllegalArgumentException("当前租户ID不能为空");
        }

        // SQL同时使用conversationId、currentUserId和currentTenantId，保证会话数据所有权隔离。
        AgentConversationDO conversationDO = agentConversationMapper.selectByConversationIdAndOwner(
                StringUtils.trim(conversationId),
                currentUserId,
                currentTenantId);

        if (Objects.isNull(conversationDO)) {
            return Optional.empty();
        }

        // 将数据库DO重新恢复为具备领域行为的AgentConversation。
        return Optional.of(convertToDomain(conversationDO));
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
