package com.ymm.coldchainai.agent.core.application.service;

import com.ymm.coldchainai.agent.core.application.command.AgentChatCommand;
import com.ymm.coldchainai.agent.core.application.dto.AgentAnswerDTO;

/**
 * 冷运 Agent 应用服务。矿场项目经理
 *
 * <p>该服务负责完成一次 Agent 问答用例的业务编排，
 * 包括参数校验、requestId生成、执行器调用、耗时统计和异常转换。</p>
 */
public interface IColdChainAgentApplicationService {

    /**
     * 执行一次冷运 Agent 问答。
     *
     * @param command Agent 问答用例命令
     * @return Agent 执行结果
     */
    AgentAnswerDTO chat(AgentChatCommand command);
}
