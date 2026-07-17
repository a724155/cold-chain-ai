package com.ymm.coldchainai.agent.core.application.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 正式 Agent 问答用例命令。
 *
 * <p>Controller接收到AgentChatRequest后，需要转换成该Command，
 * Application Service不直接依赖HTTP请求对象。</p>
 *
 * <p><strong>需求确认提醒：</strong>
 * Command字段应来源于已经确认过的PRD和接口协议。
 * 如果产品没有明确Agent选择方式、默认Agent规则或问题长度限制，
 * 应先补齐需求确认，再决定Command包含哪些字段，避免Application层围绕错误假设开发。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentChatCommand {

    /**
     * 调用方指定的Agent编码，可以为空。
     */
    private final String agentCode;

    /**
     * 用户提交给Agent的问题。
     */
    private final String question;
}