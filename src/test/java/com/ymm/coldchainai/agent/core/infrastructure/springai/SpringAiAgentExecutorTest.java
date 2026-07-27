package com.ymm.coldchainai.agent.core.infrastructure.springai;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.infrastructure.advisor.AgentAdvisorContextKeys;
import com.ymm.coldchainai.agent.core.infrastructure.springai.model.SpringAiAgentRuntime;
import com.ymm.coldchainai.agent.core.infrastructure.tool.AgentToolContextKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Spring AI Agent执行器单元测试。
 *
 * <p>本测试不调用真实模型、不执行真实Tool，也不启动Spring容器。
 * ChatClient、AgentRegistry和调用上下文全部通过Mockito模拟，
 * 重点验证Agent运行配置初始化、Agent路由、Advisor上下文、
 * ToolContext、同步模型调用和异常防御。</p>
 */
@ExtendWith(MockitoExtension.class)
class SpringAiAgentExecutorTest {

    /**
     * 测试使用的综合Agent编码。
     */
    private static final String AGENT_CODE = "cold-chain-general";

    /**
     * 测试使用的综合Agent名称。
     */
    private static final String AGENT_NAME = "冷运综合Agent";

    /**
     * 测试请求唯一标识。
     */
    private static final String REQUEST_ID = "request-test-001";

    /**
     * 测试用户问题。
     */
    private static final String QUESTION = "我上午9点整打卡算迟到吗？";

    /**
     * 测试模型最终答案。
     */
    private static final String ANSWER = "算迟到。9:00整打卡视为迟到。";

    /**
     * 测试当前用户ID。
     */
    private static final Long CURRENT_USER_ID = 10001L;

    /**
     * 测试当前租户ID。
     */
    private static final Long CURRENT_TENANT_ID = 20001L;

    /**
     * Mock Agent业务注册中心。
     */
    @Mock
    private IAgentRegistry agentRegistry;

    /**
     * Mock主Agent ChatClient。
     */
    @Mock
    private ChatClient primaryChatClient;

    /**
     * Mock第二个Agent ChatClient，用于验证运行环境路由不会串Agent。
     */
    @Mock
    private ChatClient secondaryChatClient;

    /**
     * Mock ChatClient请求构建对象。
     */
    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    /**
     * Mock同步模型响应对象。
     */
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    /**
     * Mock当前需要执行的Agent定义。
     */
    @Mock
    private AgentDefinition agentDefinition;

    /**
     * Mock当前受信任用户和租户上下文。
     */
    @Mock
    private AgentInvocationContext agentInvocationContext;

    /**
     * 验证执行器能够路由到正确ChatClient，并完整传递Advisor上下文和ToolContext。
     */
    @Test
    void shouldExecuteAgentWithCorrectRuntimeAndContext() {
        // 当前执行请求属于cold-chain-general。
        when(agentDefinition.getAgentCode()).thenReturn(AGENT_CODE);
        when(agentDefinition.getAgentName()).thenReturn(AGENT_NAME);
        // 构造第二个已启用Agent，用于验证多个运行环境存在时不会选错ChatClient。
        AgentDefinition secondaryAgentDefinition = mock(AgentDefinition.class);
        when(secondaryAgentDefinition.getAgentCode()).thenReturn("secondary-agent");

        // AgentRegistry声明当前两个Agent均处于启用状态。
        when(agentRegistry.listEnabledAgents()).thenReturn(List.of(agentDefinition, secondaryAgentDefinition));

        /*
         * 第一个Runtime故意使用大写和首尾空格，
         * 验证执行器初始化时确实通过normalizeAgentCode()完成标准化。
         */
        List<SpringAiAgentRuntime> springAiAgentRuntimeList = List.of(
                SpringAiAgentRuntime.of("  COLD-CHAIN-GENERAL  ", primaryChatClient),
                SpringAiAgentRuntime.of("secondary-agent", secondaryChatClient));

        SpringAiAgentExecutor springAiAgentExecutor = new SpringAiAgentExecutor(agentRegistry, springAiAgentRuntimeList);

        // ToolContext需要携带认证层提供的真实用户和租户ID。
        when(agentInvocationContext.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        when(agentInvocationContext.getCurrentTenantId()).thenReturn(CURRENT_TENANT_ID);

        // 模拟ChatClient同步调用链。
        mockSuccessfulChatClientCall(primaryChatClient, QUESTION, ANSWER);

        // 执行真正的Agent执行逻辑。
        String actualAnswer = springAiAgentExecutor.execute(REQUEST_ID, agentDefinition, agentInvocationContext, QUESTION);

        assertEquals(ANSWER, actualAnswer);

        // 捕获真正传给Tool Calling链路的受信任ToolContext。
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> toolContextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(chatClientRequestSpec).toolContext(toolContextCaptor.capture());

        Map<String, Object> toolContextMap = toolContextCaptor.getValue();

        assertAll(
                () -> assertEquals(REQUEST_ID, toolContextMap.get(AgentToolContextKeys.REQUEST_ID)),
                () -> assertEquals(AGENT_CODE, toolContextMap.get(AgentToolContextKeys.AGENT_CODE)),
                () -> assertEquals(CURRENT_USER_ID, toolContextMap.get(AgentToolContextKeys.CURRENT_USER_ID)),
                () -> assertEquals(CURRENT_TENANT_ID, toolContextMap.get(AgentToolContextKeys.CURRENT_TENANT_ID)));

        /*
         * Mockito不会自动执行传入advisors()的Lambda，
         * 因此先捕获Consumer，再主动执行一次，验证Advisor上下文参数实际写入内容。
         */
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<ChatClient.AdvisorSpec>> advisorConsumerCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(chatClientRequestSpec).advisors(advisorConsumerCaptor.capture());

        ChatClient.AdvisorSpec advisorSpec = mock(ChatClient.AdvisorSpec.class);
        when(advisorSpec.param(any(String.class), any())).thenReturn(advisorSpec);

        advisorConsumerCaptor.getValue().accept(advisorSpec);

        assertAll(
                () -> verify(advisorSpec).param(AgentAdvisorContextKeys.REQUEST_ID, REQUEST_ID),
                () -> verify(advisorSpec).param(AgentAdvisorContextKeys.AGENT_CODE, AGENT_CODE),
                () -> verify(advisorSpec).param(AgentAdvisorContextKeys.AGENT_NAME, AGENT_NAME));

        // 本次请求属于cold-chain-general，因此第二个Agent的ChatClient必须完全没有被调用。
        verifyNoInteractions(secondaryChatClient);
    }

    /**
     * 验证requestId为空时立即阻断Agent执行。
     */
    @Test
    void shouldRejectBlankRequestId() {
        SpringAiAgentExecutor springAiAgentExecutor = createSingleRuntimeExecutor();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> springAiAgentExecutor.execute(" ", agentDefinition, agentInvocationContext, QUESTION));

        assertEquals("Agent请求标识不能为空", exception.getMessage());

        // 参数阶段已经失败，不允许继续访问模型。
        verifyNoInteractions(primaryChatClient);
    }

    /**
     * 验证AgentDefinition为空时立即阻断Agent执行。
     */
    @Test
    void shouldRejectNullAgentDefinition() {
        SpringAiAgentExecutor springAiAgentExecutor = createSingleRuntimeExecutor();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> springAiAgentExecutor.execute(REQUEST_ID, null, agentInvocationContext, QUESTION));

        assertEquals("Agent定义不能为空", exception.getMessage());
        verifyNoInteractions(primaryChatClient);
    }

    /**
     * 验证Agent调用上下文为空时立即失败，而不是在创建ToolContext时产生裸NPE。
     */
    @Test
    void shouldRejectNullAgentInvocationContext() {
        SpringAiAgentExecutor springAiAgentExecutor = createSingleRuntimeExecutor();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> springAiAgentExecutor.execute(REQUEST_ID, agentDefinition, null, QUESTION));

        assertEquals("Agent调用上下文不能为空", exception.getMessage());
        verifyNoInteractions(primaryChatClient);
    }

    /**
     * 验证用户问题为空时立即阻断模型调用。
     */
    @Test
    void shouldRejectBlankQuestion() {
        SpringAiAgentExecutor springAiAgentExecutor = createSingleRuntimeExecutor();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> springAiAgentExecutor.execute(REQUEST_ID, agentDefinition, agentInvocationContext, "   "));

        assertEquals("Agent执行问题不能为空", exception.getMessage());
        verifyNoInteractions(primaryChatClient);
    }

    /**
     * 验证执行阶段传入未注册Agent时明确报运行配置异常。
     */
    @Test
    void shouldRejectAgentWithoutRuntimeDuringExecution() {
        SpringAiAgentExecutor springAiAgentExecutor = createSingleRuntimeExecutor();

        AgentDefinition unknownAgentDefinition = mock(AgentDefinition.class);
        when(unknownAgentDefinition.getAgentCode()).thenReturn("unknown-agent");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> springAiAgentExecutor.execute(REQUEST_ID, unknownAgentDefinition, agentInvocationContext, QUESTION));

        assertTrue(exception.getMessage().contains("执行时未找到Agent运行配置"));
        verifyNoInteractions(primaryChatClient);
    }

    /**
     * 验证模型返回空白答案时不能作为成功结果返回。
     */
    @Test
    void shouldRejectBlankAgentAnswer() {
        SpringAiAgentExecutor springAiAgentExecutor = createSingleRuntimeExecutor();

        when(agentInvocationContext.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        when(agentInvocationContext.getCurrentTenantId()).thenReturn(CURRENT_TENANT_ID);

        // 模拟模型请求执行成功，但最终没有返回有效正文。
        mockSuccessfulChatClientCall(primaryChatClient, QUESTION, "   ");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> springAiAgentExecutor.execute(REQUEST_ID, agentDefinition, agentInvocationContext, QUESTION));

        assertEquals("Agent模型未返回有效回答", exception.getMessage());
    }

    /**
     * 验证ChatClient调用异常时不会被执行器错误吞掉或者伪装成空答案。
     */
    @Test
    void shouldPropagateChatClientException() {
        SpringAiAgentExecutor springAiAgentExecutor = createSingleRuntimeExecutor();

        when(agentInvocationContext.getCurrentUserId()).thenReturn(CURRENT_USER_ID);
        when(agentInvocationContext.getCurrentTenantId()).thenReturn(CURRENT_TENANT_ID);

        RuntimeException chatClientException = new RuntimeException("模拟模型调用失败");

        when(primaryChatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.advisors(ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.toolContext(anyMap())).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(QUESTION)).thenReturn(chatClientRequestSpec);

        // 模拟同步ChatClient真正发起模型调用时失败。
        when(chatClientRequestSpec.call()).thenThrow(chatClientException);

        RuntimeException actualException = assertThrows(
                RuntimeException.class,
                () -> springAiAgentExecutor.execute(REQUEST_ID, agentDefinition, agentInvocationContext, QUESTION));

        assertSame(chatClientException, actualException);
    }

    /**
     * 验证系统没有配置任何Agent Runtime时在启动阶段直接失败。
     */
    @Test
    void shouldRejectEmptyRuntimeListDuringConstruction() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new SpringAiAgentExecutor(agentRegistry, List.of()));

        assertTrue(exception.getMessage().contains("系统中没有配置任何Agent运行环境"));
    }

    /**
     * 验证标准化以后出现重复Agent编码时启动失败，防止后注册配置覆盖前一个Runtime。
     */
    @Test
    void shouldRejectDuplicateRuntimeCodeDuringConstruction() {
        List<SpringAiAgentRuntime> springAiAgentRuntimeList = List.of(
                SpringAiAgentRuntime.of("cold-chain-general", primaryChatClient),
                SpringAiAgentRuntime.of(" COLD-CHAIN-GENERAL ", secondaryChatClient));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new SpringAiAgentExecutor(agentRegistry, springAiAgentRuntimeList));

        assertTrue(exception.getMessage().contains("存在重复Agent运行配置"));
    }

    /**
     * 验证业务层已经启用Agent但没有对应Spring AI Runtime时启动失败。
     */
    @Test
    void shouldRejectEnabledAgentWithoutRuntimeDuringConstruction() {
        AgentDefinition enabledAgentDefinition = mock(AgentDefinition.class);
        when(enabledAgentDefinition.getAgentCode()).thenReturn("missing-agent");

        when(agentRegistry.listEnabledAgents()).thenReturn(List.of(enabledAgentDefinition));

        List<SpringAiAgentRuntime> springAiAgentRuntimeList = List.of(
                SpringAiAgentRuntime.of(AGENT_CODE, primaryChatClient));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new SpringAiAgentExecutor(agentRegistry, springAiAgentRuntimeList));

        assertTrue(exception.getMessage().contains("已启用Agent缺少运行配置"));
    }

    /**
     * 创建只有cold-chain-general一个Runtime的被测执行器。
     *
     * <p>大部分参数和异常测试只需要一个运行环境，
     * 通过该方法统一准备基础配置，避免每个测试重复构造AgentRegistry和Runtime。</p>
     *
     * @return 已完成运行配置初始化的Spring AI Agent执行器
     */
    private SpringAiAgentExecutor createSingleRuntimeExecutor() {
        // 构造阶段需要AgentRegistry声明cold-chain-general当前处于启用状态。
        when(agentDefinition.getAgentCode()).thenReturn(AGENT_CODE);
        when(agentRegistry.listEnabledAgents()).thenReturn(List.of(agentDefinition));

        return new SpringAiAgentExecutor(
                agentRegistry,
                List.of(SpringAiAgentRuntime.of(AGENT_CODE, primaryChatClient)));
    }

    /**
     * 模拟完整的同步ChatClient调用链。
     *
     * <p>对应正式代码：
     * prompt → advisors → toolContext → user → call → content。</p>
     *
     * @param chatClient 当前Agent绑定的ChatClient
     * @param question 用户问题
     * @param answer 模型最终返回内容
     */
    private void mockSuccessfulChatClientCall(ChatClient chatClient, String question, String answer) {
        when(chatClient.prompt()).thenReturn(chatClientRequestSpec);

        /*
         * advisors存在多个重载，因此显式指定Consumer<ChatClient.AdvisorSpec>，
         * 避免Mockito any()产生重载方法匹配歧义。
         */
        when(chatClientRequestSpec.advisors(ArgumentMatchers.<Consumer<ChatClient.AdvisorSpec>>any())).thenReturn(chatClientRequestSpec);

        // ToolContext继续返回当前RequestSpec，使Fluent API能够继续执行user()。
        when(chatClientRequestSpec.toolContext(anyMap())).thenReturn(chatClientRequestSpec);

        // user()把真实问题写入当前请求，并继续返回RequestSpec。
        when(chatClientRequestSpec.user(question)).thenReturn(chatClientRequestSpec);

        // call()代表同步等待整个模型及Tool Calling流程执行完成。
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);

        // content()返回本次Agent最终生成的完整自然语言答案。
        when(callResponseSpec.content()).thenReturn(answer);
    }
}
