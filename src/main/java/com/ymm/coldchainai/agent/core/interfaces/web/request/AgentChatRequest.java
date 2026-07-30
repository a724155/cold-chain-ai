package com.ymm.coldchainai.agent.core.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 正式Agent问答请求。
 *
 * <p>该对象只负责接收HTTP请求参数，不允许直接传递到模型执行层、
 * Domain、Repository或者数据库层。</p>
 *
 * <p>conversationId允许为空：</p>
 *
 * <p>1. 为空表示用户开启一个新的聊天窗口；</p>
 * <p>2. 非空表示用户继续已有Conversation；</p>
 * <p>3. 后端会同时校验conversationId、currentUserId和currentTenantId，
 * 防止调用者读取或者操作其他用户的聊天窗口。</p>
 *
 * <p><strong>前后端协议提醒：</strong>
 * 开发正式接口前必须和产品、前端明确conversationId、agentCode和question的字段名称、
 * 必填性、最大长度、默认值、空值语义、兼容策略和错误返回结构。</p>
 *
 * <p>当前约定agentCode可以不传，不传时由后端选择默认Agent；
 * question必须传入。继续非默认Agent的Conversation时，
 * 前端应继续传递原Conversation绑定的agentCode，禁止中途切换Agent。</p>
 *
 * <p>当前请求中禁止出现currentUserId和currentTenantId。
 * 这两个字段必须来自后端登录认证上下文，不能由前端、Postman或者模型自由指定。</p>
 *
 * <p>在挖矿流程中，该请求相当于客户提交给矿场接待窗口的作业申请：
 * conversationId表示是否继续原项目，agentCode表示指定矿区，
 * question表示本轮需要完成的实际开采任务。</p>
 */
@Getter
@Setter
public class AgentChatRequest {

    /**
     * Conversation业务唯一标识允许的最大字符长度。
     */
    private static final int MAX_CONVERSATION_ID_LENGTH = 64;

    /**
     * Agent编码允许的最大字符长度。
     */
    private static final int MAX_AGENT_CODE_LENGTH = 64;

    /**
     * 用户问题允许的最大字符长度。
     */
    private static final int MAX_QUESTION_LENGTH = 2000;

    /**
     * 调用方准备继续使用的Conversation业务唯一标识。
     *
     * <p>该字段为空时创建新Conversation；
     * 非空时继续已有Conversation并校验用户、租户和Agent绑定关系。</p>
     */
    @Size(max = MAX_CONVERSATION_ID_LENGTH, message = "Conversation标识长度不能超过64个字符")
    private String conversationId;

    /**
     * 调用方指定的Agent编码。
     *
     * <p>该字段可以为空，为空时由后端选择默认Agent。
     * 已有Conversation不能在后续请求中切换到其他Agent。</p>
     */
    @Size(max = MAX_AGENT_CODE_LENGTH, message = "Agent编码长度不能超过64个字符")
    private String agentCode;

    /**
     * 用户本轮提交给正式Agent的问题。
     */
    @NotBlank(message = "Agent问题不能为空")
    @Size(max = MAX_QUESTION_LENGTH, message = "Agent问题长度不能超过2000个字符")
    private String question;
}
