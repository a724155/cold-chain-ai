package com.ymm.coldchainai.agent.conversation.application.service.impl;

import com.ymm.coldchainai.agent.conversation.application.command.ResolveAgentConversationCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentConversationDTO;
import com.ymm.coldchainai.agent.conversation.application.enumtype.ConversationErrorCodeEnum;
import com.ymm.coldchainai.agent.conversation.application.service.IAgentConversationApplicationService;
import com.ymm.coldchainai.agent.conversation.domain.model.AgentConversation;
import com.ymm.coldchainai.agent.conversation.domain.repository.IAgentConversationRepository;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent会话Application Service实现。
 *
 * <p>该类相当于会话总调度员：
 * 没有conversationId时创建新的聊天项目，已有conversationId时则从档案库恢复原会话并完成权限和状态检查。</p>
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentConversationApplicationServiceImpl implements IAgentConversationApplicationService {

    /**
     * Conversation业务ID统一前缀。
     */
    private static final String CONVERSATION_ID_PREFIX = "conv_";

    /**
     * Agent会话Repository。
     */
    private final IAgentConversationRepository agentConversationRepository;

    /**
     * 获取已有Conversation或者创建新Conversation。
     *
     * @param command 会话解析命令
     * @return 当前请求实际使用的Conversation
     */
    @Override
    public AgentConversationDTO resolveConversation(ResolveAgentConversationCommand command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Agent会话解析命令不能为空");
        }

        AgentInvocationContext invocationContext = command.getAgentInvocationContext();

        if (Objects.isNull(invocationContext)) {
            throw new IllegalArgumentException("Agent调用上下文不能为空");
        }

        // 没有conversationId表示用户正在开启一个全新的聊天窗口。
        if (StringUtils.isBlank(command.getConversationId())) {
            return createNewConversation(command, invocationContext);
        }

        // 已携带conversationId表示用户准备继续之前的聊天窗口。
        return resolveExistingConversation(command, invocationContext);
    }

    /**
     * 创建新的Agent Conversation。
     *
     * @param command 当前会话解析命令
     * @param invocationContext 受信任调用上下文
     * @return 新建Conversation
     */
    private AgentConversationDTO createNewConversation(ResolveAgentConversationCommand command, AgentInvocationContext invocationContext) {

        // UUID只负责生成不可预测业务ID，不使用数据库自增ID暴露给调用方。
        String conversationId = generateConversationId();

        // 创建领域对象时立即绑定用户、租户和Agent，后续整个Conversation生命周期保持稳定。
        AgentConversation conversation = AgentConversation.create(
                conversationId,
                invocationContext.getCurrentUserId(),
                invocationContext.getCurrentTenantId(),
                command.getAgentCode());

        // 持久化新Conversation，此时尚未真正产生ChatMessage，所以messageCount仍然为0。
        agentConversationRepository.save(conversation);

        return AgentConversationDTO.fromDomain(conversation, true);
    }

    /**
     * 获取并验证已有Conversation。
     *
     * @param command 当前会话解析命令
     * @param invocationContext 受信任调用上下文
     * @return 已存在Conversation
     */
    private AgentConversationDTO resolveExistingConversation(ResolveAgentConversationCommand command, AgentInvocationContext invocationContext) {

        /*
         * Repository必须同时携带conversationId、currentUserId和currentTenantId。
         * 找不到时对外统一视为“会话不存在”，不能告诉调用者该conversationId其实属于别人。
         */
        Optional<AgentConversation> conversationOptional = agentConversationRepository.findByConversationIdAndOwner(
                command.getConversationId(),
                invocationContext.getCurrentUserId(),
                invocationContext.getCurrentTenantId());

        if (conversationOptional.isEmpty()) {
            throw new BusinessException(ConversationErrorCodeEnum.CONVERSATION_NOT_FOUND, "指定Agent会话不存在或者当前用户无权访问");
        }

        AgentConversation conversation = conversationOptional.get();

        // 已关闭Conversation只能查看历史，不能继续追加新的Agent问答。
        if (!conversation.isActive()) {
            throw new BusinessException(ConversationErrorCodeEnum.CONVERSATION_CLOSED, "当前Agent会话已经关闭，请创建新的会话");
        }

        /*
         * 一个Conversation生命周期内固定绑定一个Agent。
         * 比较时忽略大小写和首尾空格，避免编码表现形式不同产生误判。
         */
        if (!StringUtils.equalsIgnoreCase(StringUtils.trim(conversation.getAgentCode()), StringUtils.trim(command.getAgentCode()))) {
            throw new BusinessException(ConversationErrorCodeEnum.CONVERSATION_AGENT_MISMATCH, "当前会话绑定Agent与本次请求Agent不一致");
        }

        return AgentConversationDTO.fromDomain(conversation, false);
    }

    /**
     * 生成不可预测的Conversation业务唯一标识。
     *
     * @return 新Conversation业务ID
     */
    private String generateConversationId() {
        return CONVERSATION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
