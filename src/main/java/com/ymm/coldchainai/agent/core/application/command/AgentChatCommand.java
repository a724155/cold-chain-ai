package com.ymm.coldchainai.agent.core.application.command;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 正式 Agent 问答用例命令。
 *
 * <p>Controller 接收到 AgentChatRequest 后，需要转换成该 Command，
 * Application Service 不直接依赖 HTTP 请求对象。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentChatCommand {

    /**
     * 用户提交给 Agent 的问题。
     */
    private final String question;
}