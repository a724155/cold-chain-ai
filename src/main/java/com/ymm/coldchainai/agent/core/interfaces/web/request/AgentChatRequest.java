package com.ymm.coldchainai.agent.core.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 正式 Agent 问答请求。跟前端约好
 *
 * <p>该对象只负责接收 HTTP 请求参数，不允许直接传递到模型执行层、
 * Domain、Repository 或数据库层。</p>
 *
 * <p>当前请求中没有 currentUserId 和 currentTenantId。
 * 后续这两个字段必须从登录认证上下文获取，不能由前端或模型自由传入。</p>
 */
@Getter
@Setter
public class AgentChatRequest {

    /**
     * 用户问题允许的最大字符长度。
     */
    private static final int MAX_QUESTION_LENGTH = 2000;

    /**
     * 用户提交给正式 Agent 的问题。
     */
    @NotBlank(message = "Agent问题不能为空")
    @Size(max = MAX_QUESTION_LENGTH, message = "Agent问题长度不能超过2000个字符")
    private String question;
}
