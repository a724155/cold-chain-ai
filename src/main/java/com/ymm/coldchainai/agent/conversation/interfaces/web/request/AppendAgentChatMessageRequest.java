package com.ymm.coldchainai.agent.conversation.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Chat History追加消息local验证请求。
 *
 * <p>该接口仅用于local环境验证ChatMessage事务、sequenceNo和Conversation统计。
 * 正式聊天接口接入后，USER和ASSISTANT角色应由后端执行流程确定，
 * 不能允许普通前端任意伪造ASSISTANT消息。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 正式开发前应与产品和前端确认conversationId、消息最大长度、
 * 重试策略和失败消息展示方式；messageRole不应作为正式用户接口开放字段。</p>
 */
@Getter
@Setter
public class AppendAgentChatMessageRequest {

    /**
     * 消息所属Conversation业务唯一标识。
     */
    @NotBlank(message = "conversationId不能为空")
    @Size(max = 64, message = "conversationId长度不能超过64个字符")
    private String conversationId;

    /**
     * 产生当前消息的Agent请求唯一标识。
     *
     * <p>local验证阶段由Postman传入；
     * 正式接口中应由Application Service统一生成。</p>
     */
    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId长度不能超过64个字符")
    private String requestId;

    /**
     * 消息角色码：1-USER，2-ASSISTANT。
     *
     * <p>该字段仅为local验证开放。</p>
     */
    @NotNull(message = "messageRole不能为空")
    private Integer messageRole;

    /**
     * 聊天消息完整正文。
     */
    @NotBlank(message = "messageContent不能为空")
    @Size(max = 20000, message = "messageContent长度不能超过20000个字符")
    private String messageContent;
}
