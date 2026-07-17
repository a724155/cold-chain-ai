package com.ymm.coldchainai.agent.core.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 正式 Agent 问答请求。
 *
 * <p>该对象只负责接收HTTP请求参数，不允许直接传递到模型执行层、
 * Domain、Repository或数据库层。</p>
 *
 * <p><strong>前后端协议提醒：</strong>
 * 开发正式接口前必须和前端明确agentCode和question的字段名称、必填性、最大长度、
 * 默认值以及错误返回结构。当前约定agentCode可以不传，不传时由后端选择默认Agent；
 * question必须传入。以后修改这些规则时需要评估旧版本前端兼容性，不能只修改后端。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * PRD如果只写“开发一个Agent问答接口”但没有说明是否允许用户切换Agent，
 * 后端必须主动向产品确认，不应自行假设页面需要或不需要Agent选择功能。</p>
 *
 * <p>当前请求中没有currentUserId和currentTenantId。
 * 后续这两个字段必须从登录认证上下文获取，不能由前端或模型自由传入。</p>
 */
@Getter
@Setter
public class AgentChatRequest {

    /**
     * Agent编码允许的最大字符长度。
     */
    private static final int MAX_AGENT_CODE_LENGTH = 64;

    /**
     * 用户问题允许的最大字符长度。
     */
    private static final int MAX_QUESTION_LENGTH = 2000;

    /**
     * 调用方指定的Agent编码。
     *
     * <p>该字段可以为空，为空时由后端使用默认Agent。</p>
     */
    @Size(max = MAX_AGENT_CODE_LENGTH, message = "Agent编码长度不能超过64个字符")
    private String agentCode;

    /**
     * 用户提交给正式Agent的问题。
     */
    @NotBlank(message = "Agent问题不能为空")
    @Size(max = MAX_QUESTION_LENGTH, message = "Agent问题长度不能超过2000个字符")
    private String question;
}
