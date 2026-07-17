package com.ymm.coldchainai.agent.core.interfaces.web.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 正式 Agent 问答响应。
 *
 * <p>该对象负责定义返回给前端的HTTP数据结构，
 * 不能直接使用AgentAnswerDTO代替，避免Application对象与接口协议绑定。</p>
 *
 * <p><strong>前后端协议提醒：</strong>
 * requestId、agentCode、agentName、answer和costMillis都是对外接口字段，
 * 上线前必须与前端确认字段用途、空值规则和展示方式。其中costMillis单位固定为毫秒，
 * agentName是否直接展示给用户也需要与产品和前端确认。字段一旦被前端使用，
 * 后续删除、改名或改变含义都必须考虑兼容性。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentChatResponse {

    /**
     * 本次Agent请求唯一标识。
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
     * Agent返回的完整答案。
     */
    private final String answer;

    /**
     * 本次Agent调用总耗时，单位为毫秒。
     */
    private final Long costMillis;
}