package com.ymm.coldchainai.agent.conversation.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Agent Conversation解析验证请求。
 *
 * <p>conversationId允许为空，为空代表开启新会话；
 * 非空代表继续已有Conversation。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 开发前应与产品确认新建/继续会话规则，并与前端明确conversationId的可选性、
 * agentCode必填性、会话关闭后的交互以及兼容策略。</p>
 */
@Getter
@Setter
public class ResolveAgentConversationRequest {

    /**
     * 已有Conversation业务标识，新建会话时允许不传。
     */
    @Size(max = 64, message = "conversationId长度不能超过64个字符")
    private String conversationId;

    /**
     * 当前Conversation绑定的Agent编码。
     */
    @NotBlank(message = "agentCode不能为空")
    @Size(max = 64, message = "agentCode长度不能超过64个字符")
    private String agentCode;
}
