package com.ymm.coldchainai.agent.core.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 正式 Agent 应用服务返回 DTO。
 *
 * <p>该对象用于在 Application 层向 Interfaces 层传递 Agent 执行结果，
 * 不属于 HTTP Response，也不属于数据库对象。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentAnswerDTO {

    /**
     * 本次 Agent 请求的唯一标识。
     *
     * <p>该标识用于关联接口响应、Agent执行日志和异常排查信息。</p>
     */
    private final String requestId;

    /**
     * 本次实际执行的Agent编码。
     */
    private final String agentCode;

    /**
     * 本次实际执行的Agent名称。
     */
    private final String agentName;

    /**
     * Agent 返回的完整答案。
     */
    private final String answer;

    /**
     * 本次 Agent 调用总耗时，单位为毫秒。
     *
     * <p>当前耗时包含 Application Service 调用执行器和模型等待时间。</p>
     */
    private final Long costMillis;
}
