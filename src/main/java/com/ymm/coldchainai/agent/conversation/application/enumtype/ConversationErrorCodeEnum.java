package com.ymm.coldchainai.agent.conversation.application.enumtype;

import com.ymm.coldchainai.shared.exception.code.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent会话错误码。
 */
@Getter
@AllArgsConstructor
public enum ConversationErrorCodeEnum implements IErrorCode {

    /**
     * 会话请求参数不合法。
     */
    CONVERSATION_PARAMETER_ERROR(44000, "Agent会话参数错误"),

    /**
     * 指定会话不存在或者当前用户无权访问。
     */
    CONVERSATION_NOT_FOUND(44001, "Agent会话不存在"),

    /**
     * 已关闭会话不能继续进行问答。
     */
    CONVERSATION_CLOSED(44002, "Agent会话已关闭"),

    /**
     * 请求Agent与原会话绑定Agent不一致。
     */
    CONVERSATION_AGENT_MISMATCH(44003, "Agent会话绑定关系不一致"),

    /**
     * 创建或者查询会话过程中发生系统异常。
     */
    CONVERSATION_SYSTEM_ERROR(54000, "Agent会话处理失败");

    /**
     * 错误码。
     */
    private final Integer code;

    /**
     * 错误信息。
     */
    private final String message;
}
