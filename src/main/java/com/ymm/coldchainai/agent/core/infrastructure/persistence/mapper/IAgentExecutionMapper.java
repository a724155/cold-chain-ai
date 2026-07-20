package com.ymm.coldchainai.agent.core.infrastructure.persistence.mapper;

import com.ymm.coldchainai.agent.core.infrastructure.persistence.dataobject.AgentExecutionDO;

/**
 * Agent执行记录MyBatis Mapper。
 *
 * <p>该接口负责把持久化对象交给Mapper XML执行SQL，不负责Agent状态规则和业务编排。</p>
 *
 * <p>在挖矿流程中，该Mapper相当于真正操作持久化账本的档案员，Repository负责告诉它应该进行哪一种登记。</p>
 */
public interface IAgentExecutionMapper {

    /**
     * 插入处于CREATED状态的新执行记录。
     *
     * @param agentExecutionDO Agent执行数据库对象
     * @return 数据库影响行数
     */
    int insertCreated(AgentExecutionDO agentExecutionDO);

    /**
     * 将执行状态从CREATED更新为RUNNING。
     *
     * @param agentExecutionDO Agent执行数据库对象
     * @return 数据库影响行数
     */
    int updateToRunning(AgentExecutionDO agentExecutionDO);

    /**
     * 将执行状态从RUNNING更新为SUCCEEDED。
     *
     * @param agentExecutionDO Agent执行数据库对象
     * @return 数据库影响行数
     */
    int updateToSucceeded(AgentExecutionDO agentExecutionDO);

    /**
     * 将执行状态从RUNNING更新为FAILED。
     *
     * @param agentExecutionDO Agent执行数据库对象
     * @return 数据库影响行数
     */
    int updateToFailed(AgentExecutionDO agentExecutionDO);
}
