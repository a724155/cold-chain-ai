package com.ymm.coldchainai.agent.core.application.registry;

import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;

import java.util.List;

/**
 * Agent 注册中心接口。查找哪个矿区
 *
 * <p>Application Service通过该接口查找Agent定义，
 * 不直接依赖Spring Bean容器、配置文件或数据库等具体注册方式。</p>
 */
public interface IAgentRegistry {

    /**
     * 根据Agent编码获取可以执行的Agent。
     *
     * <p>agentCode为空时返回默认Agent；指定Agent不存在或已停用时抛出业务异常。</p>
     *
     * @param agentCode Agent编码，可以为空
     * @return 可以执行的Agent定义
     */
    AgentDefinition getRequiredAgent(String agentCode);

    /**
     * 查询当前所有已启用的Agent。
     *
     * @return 已启用Agent列表
     */
    List<AgentDefinition> listEnabledAgents();
}
