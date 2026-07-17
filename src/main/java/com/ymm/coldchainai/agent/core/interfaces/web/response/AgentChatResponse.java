package com.ymm.coldchainai.agent.core.interfaces.web.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 正式 Agent 问答响应。跟前端约好
 *
 * <p>该对象负责定义返回给前端的 HTTP 数据结构，
 * 不能直接使用 AgentAnswerDTO 代替，避免 Application 对象与接口协议绑定。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentChatResponse {

    /**
     * 本次 Agent 请求的唯一标识。
     */
    private final String requestId;

    /**
     * Agent 返回的完整答案。
     */
    private final String answer;

    /**
     * 本次 Agent 调用总耗时，单位为毫秒。
     */
    private final Long costMillis;
}