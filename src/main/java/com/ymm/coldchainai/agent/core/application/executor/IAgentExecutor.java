package com.ymm.coldchainai.agent.core.application.executor;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;

/**
 * Agent 执行器接口。矿场操作员
 *
 * <p>Application Service 只依赖该接口，不直接依赖 Spring AI、ChatClient或某个具体模型厂商。</p>
 *
 * <p>在挖矿流程中，该接口相当于项目经理向设备操作员下达任务的标准格式，除了任务编号和目标矿区，还必须携带经过认证的客户与租户上下文。</p>
 */
public interface IAgentExecutor {

    /**
     * 执行一次正式Agent问答。
     *
     * @param requestId 本次Agent请求唯一标识
     * @param agentDefinition 本次需要执行的Agent定义
     * @param agentInvocationContext 本次调用使用的受信任用户和租户上下文
     * @param question 用户问题
     * @return 模型生成的完整答案
     */
    String execute(String requestId, AgentDefinition agentDefinition, AgentInvocationContext agentInvocationContext, String question);
}
