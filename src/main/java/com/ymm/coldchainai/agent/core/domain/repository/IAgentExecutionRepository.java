package com.ymm.coldchainai.agent.core.domain.repository;

import com.ymm.coldchainai.agent.core.domain.model.AgentExecution;

/**
 * Agent执行记录仓储端口。
 *
 * <p>Domain和Application层通过该接口保存AgentExecution，
 * 不直接依赖MyBatis、Mapper XML或具体数据库表。</p>
 *
 * <p>在挖矿流程中，该接口相当于项目经理与矿场档案仓库之间的交接规范：
 * 项目经理只需要说明保存新任务、登记开工、登记成功或登记失败，
 * 不需要知道档案员具体使用MySQL还是其他存储设备。</p>
 */
public interface IAgentExecutionRepository {

    /**
     * 保存处于CREATED状态的新Agent执行记录。
     *
     * @param agentExecution Agent执行领域对象
     */
    void saveCreated(AgentExecution agentExecution);

    /**
     * 将数据库执行状态从CREATED更新为RUNNING。
     *
     * @param agentExecution 已进入RUNNING状态的Agent执行领域对象
     */
    void updateToRunning(AgentExecution agentExecution);

    /**
     * 将数据库执行状态从RUNNING更新为SUCCEEDED。
     *
     * @param agentExecution 已进入SUCCEEDED状态的Agent执行领域对象
     */
    void updateToSucceeded(AgentExecution agentExecution);

    /**
     * 将数据库执行状态从RUNNING更新为FAILED。
     *
     * @param agentExecution 已进入FAILED状态的Agent执行领域对象
     */
    void updateToFailed(AgentExecution agentExecution);
}
