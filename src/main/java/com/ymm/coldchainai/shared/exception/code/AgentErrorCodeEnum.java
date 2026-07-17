package com.ymm.coldchainai.shared.exception.code;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent Core 错误码枚举。
 *
 * <p>该枚举只管理 Agent Core 模块的业务错误和系统错误，
 * 避免 Agent 错误码散落在 Application Service、执行器和全局异常处理器中。</p>
 */
@Getter
@AllArgsConstructor
public enum AgentErrorCodeEnum implements IErrorCode {

    /**
     * Agent Application Service 接收到无效请求参数。
     */
    AGENT_PARAMETER_ERROR(40010, "Agent请求参数错误"),

    /**
     * Agent模型、Advisor、Tool或其他执行链路发生系统异常。
     */
    AGENT_EXECUTION_ERROR(50001, "Agent执行失败，请稍后重试");

    /**
     * Agent错误编码。
     */
    private final Integer code;

    /**
     * Agent错误默认提示信息。
     */
    private final String message;
}
