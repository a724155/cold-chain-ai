package com.ymm.coldchainai.agent.conversation.application.service.impl;

import com.ymm.coldchainai.agent.conversation.application.command.AppendAgentChatMessageCommand;
import com.ymm.coldchainai.agent.conversation.application.command.QueryAgentChatHistoryCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatHistoryDTO;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatMessageDTO;
import com.ymm.coldchainai.agent.conversation.application.enumtype.ConversationErrorCodeEnum;
import com.ymm.coldchainai.agent.conversation.application.service.IAgentChatHistoryApplicationService;
import com.ymm.coldchainai.agent.conversation.domain.model.AgentChatMessage;
import com.ymm.coldchainai.agent.conversation.domain.model.AgentConversation;
import com.ymm.coldchainai.agent.conversation.domain.repository.IAgentChatMessageRepository;
import com.ymm.coldchainai.agent.conversation.domain.repository.IAgentConversationRepository;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent Chat History Application Service实现。
 *
 * <p>该类负责在一个MySQL本地短事务中完成：
 * 锁定Conversation、计算消息顺序、保存ChatMessage、更新Conversation消息统计。</p>
 *
 * <p>事务只覆盖本地数据库读写，不允许在该事务中调用ChatModel、RPC、HTTP或者其他远程服务。
 * 远程调用耗时不可控，如果持有Conversation行锁等待模型返回，
 * 会导致同一会话的后续请求长时间阻塞，并增加数据库连接和锁资源占用。</p>
 *
 * <p>与订单加锁的共同点：</p>
 * <p>1. 都使用SELECT ... FOR UPDATE保护读后写链路；</p>
 * <p>2. 都要求查询、判断、写入位于同一个本地事务；</p>
 * <p>3. 都不能在持锁事务中执行远程模型、RPC或者支付渠道调用。</p>
 *
 * <p>与订单加锁的区别：</p>
 * <p>1. 订单锁通常保护状态流转、防重复处理、金额扣减和资损安全；</p>
 * <p>2. Conversation锁主要保护sequenceNo不重复，以及messageCount和消息明细一致；</p>
 * <p>3. 订单状态冲突可能影响履约或者资金，消息顺序冲突主要影响上下文恢复和会话审计。</p>
 *
 * <p>在挖矿流程中，该类相当于项目档案总调度员：
 * 它先锁住项目总任务单，再分配下一条作业记录编号，
 * 保存记录并更新总记录数，最后提交事务释放任务单。</p>
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentChatHistoryApplicationServiceImpl implements IAgentChatHistoryApplicationService {

    /**
     * 消息业务唯一标识统一前缀。
     */
    private static final String MESSAGE_ID_PREFIX = "msg_";

    /**
     * Agent Conversation Repository。
     *
     * <p>用于加锁读取Conversation，并更新messageCount、lastMessageTime和version。</p>
     */
    private final IAgentConversationRepository agentConversationRepository;

    /**
     * Agent ChatMessage Repository。
     *
     * <p>用于保存实际USER或者ASSISTANT消息明细。</p>
     */
    private final IAgentChatMessageRepository agentChatMessageRepository;

    /**
     * 在一个短事务内向Conversation追加一条聊天消息。
     *
     * <p>事务执行顺序不能随意调整：</p>
     *
     * <p>1. 加锁读取Conversation，确保当前线程获得该会话的消息编号分配权；</p>
     * <p>2. 根据锁定后的最新messageCount计算sequenceNo；</p>
     * <p>3. 保存ChatMessage明细；</p>
     * <p>4. 调用领域行为递增messageCount并更新lastMessageTime；</p>
     * <p>5. 使用version乐观锁更新Conversation统计；</p>
     * <p>6. 事务提交后，ChatMessage和Conversation统计同时生效并释放行锁。</p>
     *
     * <p>任意一步失败都会回滚：
     * 不会出现消息已经插入但messageCount未更新，
     * 也不会出现messageCount已经增加但消息明细不存在。</p>
     *
     * @param command 追加消息Application命令
     * @return 已成功持久化的聊天消息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentChatMessageDTO appendMessage(AppendAgentChatMessageCommand command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("追加Agent聊天消息命令不能为空");
        }

        AgentInvocationContext invocationContext = command.getAgentInvocationContext();

        if (Objects.isNull(invocationContext)) {
            throw new IllegalArgumentException("Agent调用上下文不能为空");
        }

        if (Objects.isNull(invocationContext.getCurrentUserId())) {
            throw new IllegalArgumentException("当前用户ID不能为空");
        }

        if (Objects.isNull(invocationContext.getCurrentTenantId())) {
            throw new IllegalArgumentException("当前租户ID不能为空");
        }

        /*
         * SELECT ... FOR UPDATE锁定的是cold_chain_ai_conversation中的当前会话行，不是锁整张Conversation表，也不是锁ChatMessage表。
         * 锁会一直持有到当前@Transactional方法提交或者回滚，从而保护后面的sequenceNo计算、消息插入和统计更新。
         */
        Optional<AgentConversation> conversationOptional = agentConversationRepository.findByConversationIdAndOwnerForUpdate(
                        command.getConversationId(),
                        invocationContext.getCurrentUserId(),
                        invocationContext.getCurrentTenantId());

        /*
         * 查询不到既可能表示conversationId不存在，也可能表示当前用户或租户没有权限。
         * 对外统一返回“会话不存在或者无权访问”，避免泄露其他用户会话是否真实存在。
         */
        if (conversationOptional.isEmpty()) {
            throw new BusinessException(ConversationErrorCodeEnum.CONVERSATION_NOT_FOUND, "指定Agent会话不存在或者当前用户无权访问");
        }

        AgentConversation conversation = conversationOptional.get();

        // 已关闭Conversation只允许读取历史，不允许继续追加USER或ASSISTANT消息。
        if (!conversation.isActive()) {
            throw new BusinessException(ConversationErrorCodeEnum.CONVERSATION_CLOSED, "当前Agent会话已经关闭，不能继续追加聊天消息");
        }

        /*
         * 此时Conversation行已经加锁，读取到的messageCount是当前事务可见的最新值。
         * 同一个Conversation的其他追加请求必须等待当前事务结束后才能重新计算sequenceNo。
         */
        Integer sequenceNo = conversation.calculateNextMessageSequenceNo();

        // USER和ASSISTANT消息使用统一业务时间，保证消息明细和Conversation最近消息时间一致。
        LocalDateTime messageTime = LocalDateTime.now();

        AgentChatMessage chatMessage = AgentChatMessage.create(
                generateMessageId(),
                conversation.getConversationId(),
                conversation.getCurrentUserId(),
                conversation.getCurrentTenantId(),
                command.getRequestId(),
                command.getMessageRole(),
                command.getMessageContent(),
                sequenceNo,
                messageTime);

        // 先写入消息明细，再更新Conversation统计。如果后续统计更新失败，当前事务会回滚前面的INSERT，不会留下半完成数据。
        agentChatMessageRepository.save(chatMessage);

        /*
         * 领域模型负责执行ACTIVE状态校验、messageCount递增和lastMessageTime更新。
         * Application Service不应该自行setMessageCount，避免会话规则散落。
         */
        conversation.recordNewMessage(messageTime);

        /*
         * UPDATE使用旧version作为WHERE条件，并在数据库中执行version = version + 1。
         * FOR UPDATE是主要并发保护，version是额外防线，用于发现绕过加锁流程的错误更新。
         */
        agentConversationRepository.updateMessageStatistics(conversation);

        return AgentChatMessageDTO.fromDomain(chatMessage);
    }

    /**
     * 查询指定Conversation最近若干条聊天消息。
     *
     * <p>该方法使用只读事务，主要作用是明确当前调用不会执行数据修改，
     * 并让Conversation所有权校验和ChatMessage查询处于同一个数据库读取上下文中。</p>
     *
     * <p>这里不能使用SELECT ... FOR UPDATE：</p>
     *
     * <p>1. 当前流程没有“读取后修改”行为；</p>
     * <p>2. 不需要分配新的sequenceNo；</p>
     * <p>3. 不修改messageCount、lastMessageTime和version；</p>
     * <p>4. 加锁只会阻塞正在向同一Conversation追加消息的请求。</p>
     *
     * <p>与订单场景对比：</p>
     *
     * <p>普通订单详情查询不会加锁，只有读取订单状态后准备执行支付回调、
     * 状态流转或者防重复处理时才使用FOR UPDATE。</p>
     *
     * <p>同理，Chat History查询只是读取档案，不加锁；
     * 只有追加消息并计算sequenceNo时才锁定Conversation。</p>
     *
     * <p>在挖矿流程中，该方法相当于档案管理员复印最近若干条项目记录：
     * 复印不会修改原始任务单，因此不需要阻止其他工作人员继续登记新的作业记录。</p>
     *
     * @param command 查询聊天历史Application命令
     * @return 按sequenceNo升序排列的最近聊天历史
     */
    @Override
    @Transactional(readOnly = true)
    public AgentChatHistoryDTO listRecentMessages(QueryAgentChatHistoryCommand command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("查询Agent聊天历史命令不能为空");
        }

        AgentInvocationContext invocationContext = command.getAgentInvocationContext();

        if (Objects.isNull(invocationContext)) {
            throw new IllegalArgumentException("Agent调用上下文不能为空");
        }

        /*
         * 先使用conversationId、currentUserId和currentTenantId查询Conversation，目的是确认当前调用者确实拥有这个聊天窗口。
         * 不能只查询ChatMessage并在结果为空时直接返回空列表，因为空列表无法区分“Conversation存在但还没有消息”和
         * “Conversation不存在或者属于其他用户”。
         */
        Optional<AgentConversation> conversationOptional = agentConversationRepository.findByConversationIdAndOwner(
                        command.getConversationId(), invocationContext.getCurrentUserId(), invocationContext.getCurrentTenantId());

        // 不存在和无权访问统一返回相同错误。系统不能向调用者泄露某个conversationId真实存在但属于其他用户。
        if (conversationOptional.isEmpty()) {
            throw new BusinessException(ConversationErrorCodeEnum.CONVERSATION_NOT_FOUND, "指定Agent会话不存在或者当前用户无权访问");
        }

        AgentConversation conversation = conversationOptional.get();

        // 已关闭Conversation仍然允许查询历史。CLOSED只禁止追加新消息，不代表历史记录应该被删除或者禁止查看。
        List<AgentChatMessage> chatMessageList = agentChatMessageRepository.listRecentMessages(
                conversation.getConversationId(), conversation.getCurrentUserId(),
                conversation.getCurrentTenantId(), command.getLimit());

        return AgentChatHistoryDTO.fromDomainList(conversation.getConversationId(), chatMessageList);
    }


    /**
     * 生成不可预测的ChatMessage业务唯一标识。
     *
     * <p>数据库自增id只用于内部存储，messageId用于日志、审计和跨模块关联，
     * 因此不能直接向外暴露可预测的数据库主键。</p>
     *
     * @return 新ChatMessage业务唯一标识
     */
    private String generateMessageId() {
        return MESSAGE_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
