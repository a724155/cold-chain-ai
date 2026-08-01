package com.ymm.coldchainai.agent.audit.application.enumtype;

import com.ymm.coldchainai.shared.exception.code.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent Tool执行审计错误码枚举。
 *
 * <p>该枚举统一管理Tool审计模块的参数、状态和持久化异常，
 * 禁止在Application Service或者Repository中散落魔法错误码。</p>
 */
@Getter
@AllArgsConstructor
public enum ToolAuditErrorCodeEnum implements IErrorCode {

    /**
     * 创建或者更新Tool审计记录时参数不合法。
     */
    TOOL_AUDIT_PARAMETER_ERROR(45000, "Tool审计参数错误"),

    /**
     * Tool审计领域对象发生非法状态流转。
     */
    TOOL_AUDIT_STATUS_ERROR(55000, "Tool审计状态异常"),

    /**
     * Tool审计记录插入、更新或者数据库状态校验失败。
     */
    TOOL_AUDIT_PERSISTENCE_ERROR(55001, "Tool审计记录持久化失败");

    /**
     * Tool审计错误码。
     */
    private final Integer code;

    /**
     * Tool审计默认错误信息。
     */
    private final String message;
}
