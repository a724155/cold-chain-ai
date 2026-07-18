package com.ymm.coldchainai.agent.core.infrastructure.registry;

import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.shared.exception.BusinessException;
import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AgentRegistryImpl 单元测试。
 *
 * <p>该测试不启动Spring容器，直接构造Agent定义列表，
 * 验证默认Agent、指定Agent、不存在Agent和停用Agent处理规则。</p>
 */
class AgentRegistryImplTest {

    /**
     * 默认Agent编码。
     */
    private static final String DEFAULT_AGENT_CODE = "cold-chain-general";

    /**
     * 测试未指定agentCode时返回默认Agent。
     */
    @Test
    void shouldReturnDefaultAgentWhenAgentCodeIsBlank() {
        // 创建启用且标记为默认的Agent定义。
        AgentDefinition defaultAgentDefinition = AgentDefinition.of(DEFAULT_AGENT_CODE, "冷运综合业务助手", "默认助手", true, true);

        // List变量使用List后缀，符合项目集合命名规范。
        List<AgentDefinition> agentDefinitionList = List.of(defaultAgentDefinition);

        AgentRegistryImpl agentRegistry = new AgentRegistryImpl(agentDefinitionList);

        AgentDefinition result = agentRegistry.getRequiredAgent(" ");

        assertSame(defaultAgentDefinition, result);
    }

    /**
     * 测试Agent编码查找不区分大小写。
     */
    @Test
    void shouldFindAgentIgnoringCodeCase() {
        AgentDefinition defaultAgentDefinition = AgentDefinition.of(DEFAULT_AGENT_CODE, "冷运综合业务助手", "默认助手", true, true);
        List<AgentDefinition> agentDefinitionList = List.of(defaultAgentDefinition);

        AgentRegistryImpl agentRegistry = new AgentRegistryImpl(agentDefinitionList);

        AgentDefinition result = agentRegistry.getRequiredAgent("COLD-CHAIN-GENERAL");

        assertSame(defaultAgentDefinition, result);
    }

    /**
     * 测试指定Agent不存在时抛出业务异常。
     */
    @Test
    void shouldThrowBusinessExceptionWhenAgentDoesNotExist() {
        AgentDefinition defaultAgentDefinition = AgentDefinition.of(DEFAULT_AGENT_CODE, "冷运综合业务助手", "默认助手", true, true);
        List<AgentDefinition> agentDefinitionList = List.of(defaultAgentDefinition);

        AgentRegistryImpl agentRegistry = new AgentRegistryImpl(agentDefinitionList);

        BusinessException businessException = assertThrows(BusinessException.class, () -> agentRegistry.getRequiredAgent("unknown-agent"));

        assertEquals(AgentErrorCodeEnum.AGENT_NOT_FOUND.getCode(), businessException.getCode());
    }

    /**
     * 测试指定Agent已停用时抛出业务异常。
     */
    @Test
    void shouldThrowBusinessExceptionWhenAgentIsDisabled() {
        AgentDefinition defaultAgentDefinition = AgentDefinition.of(DEFAULT_AGENT_CODE, "冷运综合业务助手", "默认助手", true, true);
        AgentDefinition disabledAgentDefinition = AgentDefinition.of("disabled-agent", "停用助手", "测试停用状态", false, false);
        List<AgentDefinition> agentDefinitionList = List.of(defaultAgentDefinition, disabledAgentDefinition);

        AgentRegistryImpl agentRegistry = new AgentRegistryImpl(agentDefinitionList);

        BusinessException businessException = assertThrows(BusinessException.class, () -> agentRegistry.getRequiredAgent("disabled-agent"));

        assertEquals(AgentErrorCodeEnum.AGENT_DISABLED.getCode(), businessException.getCode());
    }
}
