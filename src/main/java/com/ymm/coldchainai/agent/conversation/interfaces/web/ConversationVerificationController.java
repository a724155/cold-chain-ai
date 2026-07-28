package com.ymm.coldchainai.agent.conversation.interfaces.web;

import com.ymm.coldchainai.agent.conversation.application.command.ResolveAgentConversationCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentConversationDTO;
import com.ymm.coldchainai.agent.conversation.application.service.IAgentConversationApplicationService;
import com.ymm.coldchainai.agent.conversation.interfaces.web.request.ResolveAgentConversationRequest;
import com.ymm.coldchainai.agent.conversation.interfaces.web.response.AgentConversationResponse;
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
 * Agent Conversation本地验证接口。
 *
 * <p>当前接口只用于local环境验证Conversation创建、复用和数据权限逻辑。
 * 正式聊天接口接入Conversation后将不再依赖该验证入口。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 正式接入前需要与前端明确conversationId为空表示新会话，
 * 非空表示继续原会话，并确认会话关闭、切换Agent等交互行为。</p>
 */
@RestController
@Profile("local")
@RequestMapping("/api/verification/agent/conversation")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ConversationVerificationController {

    /**
     * Agent Conversation应用服务。
     */
    private final IAgentConversationApplicationService agentConversationApplicationService;

    /**
     * 当前认证用户上下文。
     */
    private final ICurrentUserContext currentUserContext;

    /**
     * 创建新Conversation或者解析已有Conversation。
     *
     * @param request Conversation验证请求
     * @return 当前实际使用的Conversation
     */
    @PostMapping("/resolve")
    public YmmResult<AgentConversationResponse> resolveConversation(@Valid @RequestBody ResolveAgentConversationRequest request) {
        // 用户和租户身份继续从受信任认证上下文获取，绝不接受Postman直接伪造userId和tenantId。
        AgentInvocationContext invocationContext = AgentInvocationContext.create(
                currentUserContext.getCurrentUserId(),
                currentUserContext.getCurrentTenantId());

        ResolveAgentConversationCommand command = ResolveAgentConversationCommand.create(
                request.getConversationId(),
                request.getAgentCode(),
                invocationContext);

        AgentConversationDTO conversationDTO = agentConversationApplicationService.resolveConversation(command);

        AgentConversationResponse response = AgentConversationResponse.fromDTO(conversationDTO);

        return YmmResult.success(response);
    }
}
