package com.ymm.coldchainai.agent.core.infrastructure.springai;

import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.infrastructure.springai.model.SpringAiAgentRuntime;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SpringAiAgentExecutor单元测试。
 *
 * <p>该测试不调用真实模型，主要验证Agent定义与Spring AI运行配置
 * 在系统启动阶段的一致性校验规则。</p>
 */
class SpringAiAgentExecutorTest {

    /**
     * 默认Agent编码。
     */
    private static final String DEFAULT_AGENT_CODE = "cold-chain-general";

    /**
     * 测试已启用Agent存在对应运行配置时执行器可以正常初始化。
     */
    @Test
    void shouldInitializeWhenEnabledAgentHasRuntime() {
        // 模拟Agent注册中心，避免测试依赖真实Spring容器。
        IAgentRegistry agentRegistry = mock(IAgentRegistry.class);

        // 创建已启用的默认Agent定义。
        AgentDefinition agentDefinition = AgentDefinition.of(DEFAULT_AGENT_CODE, "冷运综合业务助手", "测试Agent", true, true);

        // 模拟当前Agent专属ChatClient，本测试不会产生真实模型调用。
        ChatClient chatClient = mock(ChatClient.class);

        // 创建与Agent编码匹配的Spring AI运行配置。
        SpringAiAgentRuntime springAiAgentRuntime = SpringAiAgentRuntime.of(DEFAULT_AGENT_CODE, chatClient);

        // List类型变量统一使用List后缀。
        List<AgentDefinition> enabledAgentDefinitionList = List.of(agentDefinition);
        List<SpringAiAgentRuntime> springAiAgentRuntimeList = List.of(springAiAgentRuntime);

        when(agentRegistry.listEnabledAgents()).thenReturn(enabledAgentDefinitionList);

        // Agent定义和运行配置完整时，构造执行器不应抛出异常。
        assertDoesNotThrow(() -> new SpringAiAgentExecutor(agentRegistry, springAiAgentRuntimeList));
    }

    /**
     * 测试已启用Agent缺少运行配置时执行器初始化失败。
     */
    @Test
    void shouldThrowExceptionWhenEnabledAgentHasNoRuntime() {
        IAgentRegistry agentRegistry = mock(IAgentRegistry.class);

        // 注册中心存在已启用Agent，但运行配置列表中没有对应agentCode。
        AgentDefinition agentDefinition = AgentDefinition.of(DEFAULT_AGENT_CODE, "冷运综合业务助手", "测试Agent", true, true);

        // 创建一个编码不匹配的运行配置，用于模拟配置人员绑定错误。
        ChatClient chatClient = mock(ChatClient.class);
        SpringAiAgentRuntime springAiAgentRuntime = SpringAiAgentRuntime.of("other-agent", chatClient);

        List<AgentDefinition> enabledAgentDefinitionList = List.of(agentDefinition);
        List<SpringAiAgentRuntime> springAiAgentRuntimeList = List.of(springAiAgentRuntime);

        when(agentRegistry.listEnabledAgents()).thenReturn(enabledAgentDefinitionList);

        // 已启用Agent缺少对应运行配置时必须在启动阶段快速失败。
        assertThrows(IllegalStateException.class, () -> new SpringAiAgentExecutor(agentRegistry, springAiAgentRuntimeList));
    }

    /**
     * 测试存在重复Agent运行配置时执行器初始化失败。
     */
    @Test
    void shouldThrowExceptionWhenRuntimeCodeIsDuplicated() {
        IAgentRegistry agentRegistry = mock(IAgentRegistry.class);

        AgentDefinition agentDefinition = AgentDefinition.of(DEFAULT_AGENT_CODE, "冷运综合业务助手", "测试Agent", true, true);

        // 两个运行配置使用大小写不同但标准化后相同的agentCode，应该被识别为重复。
        ChatClient firstChatClient = mock(ChatClient.class);
        ChatClient secondChatClient = mock(ChatClient.class);

        SpringAiAgentRuntime firstSpringAiAgentRuntime = SpringAiAgentRuntime.of(DEFAULT_AGENT_CODE, firstChatClient);
        SpringAiAgentRuntime secondSpringAiAgentRuntime = SpringAiAgentRuntime.of("COLD-CHAIN-GENERAL", secondChatClient);

        List<AgentDefinition> enabledAgentDefinitionList = List.of(agentDefinition);
        List<SpringAiAgentRuntime> springAiAgentRuntimeList = List.of(firstSpringAiAgentRuntime, secondSpringAiAgentRuntime);

        when(agentRegistry.listEnabledAgents()).thenReturn(enabledAgentDefinitionList);

        // 标准化后agentCode重复会导致运行时路由不确定，因此必须拒绝启动。
        assertThrows(IllegalStateException.class, () -> new SpringAiAgentExecutor(agentRegistry, springAiAgentRuntimeList));
    }

    /**
     * 测试运行配置中的ChatClient为空时执行器初始化失败。
     */
    @Test
    void shouldThrowExceptionWhenRuntimeChatClientIsNull() {
        IAgentRegistry agentRegistry = mock(IAgentRegistry.class);

        AgentDefinition agentDefinition = AgentDefinition.of(DEFAULT_AGENT_CODE, "冷运综合业务助手", "测试Agent", true, true);

        // ChatClient为空意味着Agent无法真正执行模型调用。
        SpringAiAgentRuntime springAiAgentRuntime = SpringAiAgentRuntime.of(DEFAULT_AGENT_CODE, null);

        List<AgentDefinition> enabledAgentDefinitionList = List.of(agentDefinition);
        List<SpringAiAgentRuntime> springAiAgentRuntimeList = List.of(springAiAgentRuntime);

        when(agentRegistry.listEnabledAgents()).thenReturn(enabledAgentDefinitionList);

        assertThrows(IllegalStateException.class, () -> new SpringAiAgentExecutor(agentRegistry, springAiAgentRuntimeList));
    }
}
