package com.ymm.coldchainai.agent.core.interfaces.web.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 正式Agent问答响应。
 *
 * <p>该对象负责定义返回给前端的HTTP数据结构，
 * 不能直接使用AgentAnswerDTO代替，避免Application对象与接口协议绑定。</p>
 *
 * <p><strong>前后端协议提醒：</strong>
 * conversationId、requestId、agentCode、agentName、answer和costMillis都是对外接口字段，
 * 上线前必须与前端确认字段用途、空值规则、展示方式和兼容策略。</p>
 *
 * <p>conversationId用于后续继续同一个聊天窗口，
 * requestId只用于标识当前这一轮执行，前端不能混淆二者。</p>
 *
 * <p>在挖矿流程中，conversationId是长期项目编号，
 * requestId是本次具体开采任务编号，二者属于一对多关系。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentChatResponse {

    /**
     * 本次Agent请求唯一标识。
     */
    private final String requestId;

    /**
     * 本次问答所属Conversation业务唯一标识。
     *
     * <p>前端继续多轮聊天时，需要把该字段原样放入下一次请求。</p>
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
     */
    private final Long costMillis;
}