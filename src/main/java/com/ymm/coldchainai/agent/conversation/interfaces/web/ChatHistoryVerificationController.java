package com.ymm.coldchainai.agent.conversation.interfaces.web;

import com.ymm.coldchainai.agent.conversation.application.command.AppendAgentChatMessageCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatMessageDTO;
import com.ymm.coldchainai.agent.conversation.application.service.IAgentChatHistoryApplicationService;
import com.ymm.coldchainai.agent.conversation.domain.enumtype.ChatMessageRoleEnum;
import com.ymm.coldchainai.agent.conversation.interfaces.web.request.AppendAgentChatMessageRequest;
import com.ymm.coldchainai.agent.conversation.interfaces.web.response.AgentChatMessageResponse;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.shared.response.YmmResult;
import com.ymm.coldchainai.shared.security.context.ICurrentUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat History本地验证接口。
 *
 * <p>该Controller只用于验证Conversation加锁、ChatMessage顺序分配和消息统计更新，
 * 不代表正式聊天接口设计。</p>
 *
 * <p>在挖矿流程中，该接口相当于测试环境中的档案登记窗口，
 * 允许研发人员手动提交客户记录或者矿场报告，检查归档编号是否连续。</p>
 */
@RestController
@Profile("local")
@RequestMapping("/api/verification/agent/chat-history")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ChatHistoryVerificationController {

    /**
     * Agent Chat History Application Service。
     */
    private final IAgentChatHistoryApplicationService agentChatHistoryApplicationService;

    /**
     * 当前认证用户上下文。
     *
     * <p>Postman只能提交消息内容，不能伪造currentUserId和currentTenantId。</p>
     */
    private final ICurrentUserContext currentUserContext;

    /**
     * 向指定Conversation追加一条local验证消息。
     *
     * @param request 追加消息验证请求
     * @return 已成功持久化的消息信息
     */
    @PostMapping("/messages")
    public YmmResult<AgentChatMessageResponse> appendMessage(@Valid @RequestBody AppendAgentChatMessageRequest request) {
        // 用户和租户身份从受信任本地认证上下文获取，不能接受Postman直接指定。
        AgentInvocationContext invocationContext = AgentInvocationContext.create(
                currentUserContext.getCurrentUserId(), currentUserContext.getCurrentTenantId());

        // 将数据库角色码转换为领域枚举，未知角色码会被明确拒绝。
        ChatMessageRoleEnum messageRole = ChatMessageRoleEnum.fromCode(request.getMessageRole());

        AppendAgentChatMessageCommand command = AppendAgentChatMessageCommand.create(
                request.getConversationId(),
                request.getRequestId(),
                messageRole,
                request.getMessageContent(),
                invocationContext);

        // Application Service在一个短事务内完成加锁、消息保存和Conversation统计更新。
        AgentChatMessageDTO chatMessageDTO = agentChatHistoryApplicationService.appendMessage(command);

        // Response转换方法自身负责DTO空值防御。
        AgentChatMessageResponse response = AgentChatMessageResponse.fromDTO(chatMessageDTO);

        return YmmResult.success(response);
    }
}
