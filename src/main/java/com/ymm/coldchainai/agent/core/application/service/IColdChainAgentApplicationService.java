package com.ymm.coldchainai.agent.core.application.service;

import com.ymm.coldchainai.agent.core.application.command.AgentChatCommand;
import com.ymm.coldchainai.agent.core.application.dto.AgentAnswerDTO;

/**
 * 冷运Agent应用服务。
 *
 * <p>该服务相当于矿场项目经理，负责完成一次正式Agent问答用例编排：</p>
 *
 * <p>1. 选择实际执行的Agent；</p>
 * <p>2. 创建或者解析Conversation；</p>
 * <p>3. 建立AgentExecution执行任务；</p>
 * <p>4. 通过短事务保存USER问题；</p>
 * <p>5. 在不持有Conversation数据库锁的情况下调用模型和Tool；</p>
 * <p>6. 通过另一个短事务保存ASSISTANT回答；</p>
 * <p>7. 更新AgentExecution并返回最终结果。</p>
 *
 * <p>该接口不直接暴露MyBatis、ChatClient或者Spring AI实现细节。</p>
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
