package com.ymm.coldchainai.agent.core.application.executor;

import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;

/**
 * Agent 执行器接口。矿场操作员
 *
 * <p>Application Service 只依赖该接口，不直接依赖 Spring AI、ChatClient
 * 或某个具体模型厂商。</p>
 *
 * <p>当前实现类使用 Spring AI。将来即使替换模型框架，
 * Application 层也不需要跟着大规模修改。</p>
 */
public interface IAgentExecutor {

    /**
     * 执行一次正式Agent问答。
     *
     * @param requestId 本次Agent请求唯一标识
     * @param agentDefinition 本次需要执行的Agent定义
     * @param question 用户问题
     * @return 模型生成的完整答案
     */
    String execute(String requestId, AgentDefinition agentDefinition, String question);
}
