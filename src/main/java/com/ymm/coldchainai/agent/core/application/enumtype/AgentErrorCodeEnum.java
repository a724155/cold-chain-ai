package com.ymm.coldchainai.agent.core.application.enumtype;

import com.ymm.coldchainai.shared.exception.code.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent Core 错误码枚举。
 *
 * <p>该枚举只管理Agent Core模块的业务错误和系统错误，
 * 避免错误编码和提示信息散落在Application Service、注册中心和执行器中。</p>
 */
@Getter
@AllArgsConstructor
public enum AgentErrorCodeEnum implements IErrorCode {

    /**
     * Agent Application Service 接收到无效请求参数。
     */
    AGENT_PARAMETER_ERROR(40010, "Agent请求参数错误"),

    /**
     * 调用方指定的 Agent 不存在。
     */
    AGENT_NOT_FOUND(40011, "指定Agent不存在"),

    /**
     * 调用方指定的 Agent 已被停用。
     */
    AGENT_DISABLED(40012, "指定Agent已停用"),

    /**
     * Agent模型、Advisor、Tool或其他执行链路发生系统异常。
     */
    AGENT_EXECUTION_ERROR(50001, "Agent执行失败，请稍后重试"),

    /**
     * Agent定义为空、编码重复或默认Agent配置错误。
     */
    AGENT_REGISTRY_CONFIGURATION_ERROR(50002, "Agent注册配置错误"),

    /**
     * Agent已经完成业务注册，但没有配置对应的Spring AI运行环境。
     */
    AGENT_RUNTIME_CONFIGURATION_ERROR(50003, "Agent运行时配置错误");

    /**
     * Agent错误编码。
     */
    private final Integer code;

    /**
     * Agent错误默认提示信息。
     */
    private final String message;
}
