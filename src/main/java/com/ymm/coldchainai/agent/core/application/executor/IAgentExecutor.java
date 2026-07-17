package com.ymm.coldchainai.agent.core.application.executor;

/**
 * Agent 执行器接口。
 *
 * <p>Application Service 只依赖该接口，不直接依赖 Spring AI、ChatClient
 * 或某个具体模型厂商。</p>
 *
 * <p>当前实现类使用 Spring AI。将来即使替换模型框架，
 * Application 层也不需要跟着大规模修改。</p>
 */
public interface IAgentExecutor {

    /**
     * 执行一次正式 Agent 问答。
     *
     * @param requestId 本次 Agent 请求唯一标识
     * @param question 用户问题
     * @return 模型生成的完整答案
     */
    String execute(String requestId, String question);
}
