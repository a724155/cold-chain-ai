package com.ymm.coldchainai.agent.conversation.infrastructure.persistence.repository;

import com.ymm.coldchainai.agent.conversation.domain.enumtype.ChatMessageRoleEnum;
import com.ymm.coldchainai.agent.conversation.domain.model.AgentChatMessage;
import com.ymm.coldchainai.agent.conversation.domain.repository.IAgentChatMessageRepository;
import com.ymm.coldchainai.agent.conversation.infrastructure.persistence.dataobject.AgentChatMessageDO;
import com.ymm.coldchainai.agent.conversation.infrastructure.persistence.mapper.IAgentChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * Agent聊天消息Repository MyBatis实现。
 *
 * <p>该组件负责AgentChatMessage领域模型与AgentChatMessageDO数据库对象之间的转换，
 * 并通过IAgentChatMessageMapper完成Chat History持久化。</p>
 *
 * <p>Repository不负责计算sequenceNo，也不负责判断Conversation是否允许追加消息。
 * 这些规则必须由带有事务的Application Service和AgentConversation领域模型完成。</p>
 *
 * <p>在挖矿流程中，该组件相当于项目档案管理员：
 * Application交给它的是已经审核通过的作业记录，
 * 它负责转换成档案格式并保存；读取时再把纸质档案恢复成业务人员能够理解的领域记录。</p>
 */
@Repository
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentChatMessageRepositoryImpl implements IAgentChatMessageRepository {

    /**
     * Agent聊天消息MyBatis Mapper。
     *
     * <p>该Mapper负责执行实际INSERT和SELECT SQL，
     * Repository负责参数防御、领域对象转换以及持久化结果校验。</p>
     */
    private final IAgentChatMessageMapper agentChatMessageMapper;

    /**
     * 保存一条USER问题或者ASSISTANT回答。
     *
     * <p>在挖矿流程中，这相当于档案管理员收到一条已经编号完成的作业记录，
     * 将其转换成数据库档案格式并正式归档。</p>
     *
     * @param chatMessage 待保存的聊天消息领域对象
     */
    @Override
    public void save(AgentChatMessage chatMessage) {
        if (Objects.isNull(chatMessage)) {
            throw new IllegalArgumentException("待保存Agent聊天消息不能为空");
        }

        // 将包含领域语义的AgentChatMessage转换为只负责数据库映射的DO。
        AgentChatMessageDO chatMessageDO = convertToDO(chatMessage);

        // MyBatis执行INSERT并返回实际受影响记录数。
        int insertCount = agentChatMessageMapper.insert(chatMessageDO);

        // 一条消息只能成功插入一条记录，返回值异常时不能继续伪装成保存成功。
        if (insertCount != 1) {
            throw new IllegalStateException("Agent聊天消息保存失败，messageId=%s".formatted(chatMessage.getMessageId()));
        }
    }

    /**
     * 查询指定Conversation最近若干条聊天消息。
     *
     * <p>该方法返回的消息已经按照sequenceNo升序排列，
     * 后续Application或者Chat Memory可以直接按照List顺序恢复多轮上下文。</p>
     *
     * <p>在挖矿流程中，这相当于档案管理员按照客户、租户和项目编号核验权限后，
     * 从项目档案中取出最后N条记录，并按真实作业先后顺序交给智能设备阅读。</p>
     *
     * @param conversationId 会话业务唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @param limit 最多查询的最近消息数量
     * @return 按sequenceNo升序排列的聊天消息领域对象列表
     */
    @Override
    public List<AgentChatMessage> listRecentMessages(String conversationId, Long currentUserId, Long currentTenantId, Integer limit) {

        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("会话标识不能为空");
        }

        if (Objects.isNull(currentUserId)) {
            throw new IllegalArgumentException("当前用户ID不能为空");
        }

        if (Objects.isNull(currentTenantId)) {
            throw new IllegalArgumentException("当前租户ID不能为空");
        }

        if (Objects.isNull(limit) || limit <= 0) {
            throw new IllegalArgumentException("消息查询数量必须大于0");
        }

        /*
         * Mapper可能因为异常实现或者框架扩展返回null，
         * ListUtils.emptyIfNull保证Repository不会直接对null List执行stream而发生NPE。
         */
        List<AgentChatMessageDO> chatMessageDOList = ListUtils.emptyIfNull(
                agentChatMessageMapper.selectRecentByConversationIdAndOwner(StringUtils.trim(conversationId), currentUserId, currentTenantId, limit));

        /*
         * 每一条DO都转换成领域对象。
         * convertToDomain会检查DO本身是否为空，并通过领域restore方法校验核心字段。
         */
        return chatMessageDOList.stream()
                .map(this::convertToDomain)
                .toList();
    }

    /**
     * 将AgentChatMessage领域模型转换成MyBatis数据库对象。
     *
     * <p>该方法只负责字段映射，不重新计算sequenceNo，也不改变消息角色和正文。</p>
     *
     * @param chatMessage 已通过领域规则创建的聊天消息
     * @return 可交给MyBatis Mapper保存的数据库对象
     */
    private AgentChatMessageDO convertToDO(AgentChatMessage chatMessage) {
        AgentChatMessageDO chatMessageDO = new AgentChatMessageDO();

        // 数据库主键在新消息插入前允许为空，插入成功后由MyBatis回填。
        chatMessageDO.setId(chatMessage.getId());

        // 映射消息业务标识和所属Conversation。
        chatMessageDO.setMessageId(chatMessage.getMessageId());
        chatMessageDO.setConversationId(chatMessage.getConversationId());

        // 映射消息数据所有权，保证数据库能够直接执行用户和租户隔离查询。
        chatMessageDO.setCurrentUserId(chatMessage.getCurrentUserId());
        chatMessageDO.setCurrentTenantId(chatMessage.getCurrentTenantId());

        // 映射本轮Agent请求标识，便于后续关联一问一答和AgentExecution。
        chatMessageDO.setRequestId(chatMessage.getRequestId());

        // 领域枚举转换成数据库整数角色码。
        chatMessageDO.setMessageRole(chatMessage.getMessageRole().getCode());

        // 映射完整消息正文、会话内顺序和创建时间。
        chatMessageDO.setMessageContent(chatMessage.getMessageContent());
        chatMessageDO.setSequenceNo(chatMessage.getSequenceNo());
        chatMessageDO.setCreateTime(chatMessage.getCreateTime());

        return chatMessageDO;
    }

    /**
     * 将MyBatis查询到的数据库对象恢复成聊天消息领域模型。
     *
     * <p>恢复过程中会把messageRole整数编码转换为ChatMessageRoleEnum，
     * 并由AgentChatMessage.restore()统一校验messageId、conversationId、
     * requestId、正文、sequenceNo和创建时间等核心字段。</p>
     *
     * @param chatMessageDO MyBatis查询得到的聊天消息数据库对象
     * @return 恢复完成的聊天消息领域对象
     */
    private AgentChatMessage convertToDomain(AgentChatMessageDO chatMessageDO) {
        if (Objects.isNull(chatMessageDO)) {
            throw new IllegalStateException("聊天消息查询结果包含空DO");
        }

        return AgentChatMessage.restore(
                chatMessageDO.getId(),
                chatMessageDO.getMessageId(),
                chatMessageDO.getConversationId(),
                chatMessageDO.getCurrentUserId(),
                chatMessageDO.getCurrentTenantId(),
                chatMessageDO.getRequestId(),
                ChatMessageRoleEnum.fromCode(chatMessageDO.getMessageRole()),
                chatMessageDO.getMessageContent(),
                chatMessageDO.getSequenceNo(),
                chatMessageDO.getCreateTime());
    }
}
