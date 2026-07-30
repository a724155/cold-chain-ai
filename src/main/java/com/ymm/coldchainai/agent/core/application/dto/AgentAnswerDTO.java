package com.ymm.coldchainai.agent.core.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 正式Agent应用服务返回DTO。
 *
 * <p>该对象用于在Application层向Interfaces层传递Agent执行结果，
 * 不属于HTTP Response，也不属于数据库对象。</p>
 *
 * <p>在挖矿流程中，该DTO相当于矿场项目总调度员交给接待窗口的交付凭证：
 * conversationId标识长期项目，requestId标识本轮作业，
 * answer是最终交付结果，costMillis是本轮作业耗时。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentAnswerDTO {

    /**
     * 本次Agent请求的唯一标识。
     *
     * <p>用于关联接口响应、AgentExecution、USER消息、ASSISTANT消息、
     * 模型日志和后续Tool执行审计信息。</p>
     */
    private final String requestId;

    /**
     * 本次问答所属Conversation业务唯一标识。
     *
     * <p>同一Conversation可以包含多个不同requestId，
     * 每个requestId代表其中一轮独立Agent执行。</p>
     */
    private final String conversationId;

    /**
     * 本次实际执行的Agent编码。
     */
    private final String agentCode;

    /**
     * 本次实际执行的Agent名称。
     */
    private final String agentName;

    /**
     * Agent返回的完整答案。
     */
    private final String answer;

    /**
     * 本次Agent调用总耗时，单位为毫秒。
     *
     * <p>当前耗时来源于AgentExecution，
     * 主要覆盖模型和Tool Calling执行链路。</p>
     */
    private final Long costMillis;
}