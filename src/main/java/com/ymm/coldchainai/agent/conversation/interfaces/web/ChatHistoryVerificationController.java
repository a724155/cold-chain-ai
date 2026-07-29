package com.ymm.coldchainai.agent.conversation.interfaces.web;

import com.ymm.coldchainai.agent.conversation.application.command.AppendAgentChatMessageCommand;
import com.ymm.coldchainai.agent.conversation.application.command.QueryAgentChatHistoryCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatHistoryDTO;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatMessageDTO;
import com.ymm.coldchainai.agent.conversation.application.service.IAgentChatHistoryApplicationService;
import com.ymm.coldchainai.agent.conversation.domain.enumtype.ChatMessageRoleEnum;
import com.ymm.coldchainai.agent.conversation.interfaces.web.request.AppendAgentChatMessageRequest;
import com.ymm.coldchainai.agent.conversation.interfaces.web.request.QueryRecentAgentChatMessageRequest;
import com.ymm.coldchainai.agent.conversation.interfaces.web.response.AgentChatHistoryResponse;
import com.ymm.coldchainai.agent.conversation.interfaces.web.response.AgentChatMessageResponse;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.shared.response.YmmResult;
import com.ymm.coldchainai.shared.security.context.ICurrentUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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

    /**
     * 查询指定Conversation最近若干条聊天消息。
     *
     * <p>当前接口只用于local环境验证Chat History查询，
     * 正式聊天历史接口后续可以在此基础上扩展游标分页和更早消息加载。</p>
     *
     * <p>在挖矿流程中，该接口相当于档案调阅窗口：
     * 调用者提交项目编号和记录数量，窗口核验用户和租户身份后返回最近作业记录。</p>
     *
     * @param request 查询最近聊天消息请求
     * @return 按sequenceNo升序排列的聊天历史
     */
    @GetMapping("/messages")
    public YmmResult<AgentChatHistoryResponse> listRecentMessages(@Valid @ModelAttribute QueryRecentAgentChatMessageRequest request) {

        // 受信任用户和租户身份仍然从CurrentUserContext获取，不接受URL参数伪造。
        AgentInvocationContext invocationContext = AgentInvocationContext.create(
                currentUserContext.getCurrentUserId(), currentUserContext.getCurrentTenantId());

        QueryAgentChatHistoryCommand command = QueryAgentChatHistoryCommand.create(
                request.getConversationId(), request.getLimit(), invocationContext);

        // Application Service先验证Conversation所有权，再读取最近若干条ChatMessage。
        AgentChatHistoryDTO chatHistoryDTO = agentChatHistoryApplicationService.listRecentMessages(command);

        // Response转换方法负责DTO、列表和列表元素的空值防御。
        AgentChatHistoryResponse response = AgentChatHistoryResponse.fromDTO(chatHistoryDTO);

        return YmmResult.success(response);
    }
}
