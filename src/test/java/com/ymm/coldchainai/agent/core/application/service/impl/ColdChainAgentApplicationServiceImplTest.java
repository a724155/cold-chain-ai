package com.ymm.coldchainai.agent.core.application.service.impl;

import com.ymm.coldchainai.agent.core.application.command.AgentChatCommand;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.dto.AgentAnswerDTO;
import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import com.ymm.coldchainai.agent.core.application.executor.IAgentExecutor;
import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.domain.model.AgentExecution;
import com.ymm.coldchainai.agent.core.domain.repository.IAgentExecutionRepository;
import com.ymm.coldchainai.shared.exception.AgentExecutionException;
import com.ymm.coldchainai.shared.security.context.ICurrentUserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 冷运Agent应用服务单元测试。
 *
 * <p>该测试模拟注册中心、执行器和Repository，
 * 验证任务创建、状态持久化、模型调用和结果返回的编排顺序。</p>
 */
@ExtendWith(MockitoExtension.class)
class ColdChainAgentApplicationServiceImplTest {

    /**
     * 测试Agent编码。
     */
    private static final String AGENT_CODE = "cold-chain-general";

    /**
     * 测试Agent名称。
     */
    private static final String AGENT_NAME = "冷运综合业务助手";

    /**
     * 测试问题。
     */
    private static final String QUESTION = "测试Agent执行记录持久化";

    /**
     * 测试模型答案。
     */
    private static final String AGENT_ANSWER = "Agent执行记录持久化成功";

    /**
     * 模拟Agent注册中心。
     */
    @Mock
    private IAgentRegistry agentRegistry;

    /**
     * 模拟Agent执行器。
     */
    @Mock
    private IAgentExecutor agentExecutor;

    /**
     * 模拟Agent执行记录Repository。
     */
    @Mock
    private IAgentExecutionRepository agentExecutionRepository;

    /**
     * 模拟当前登录用户上下文。
     */
    @Mock
    private ICurrentUserContext currentUserContext;

    /**
     * 将模拟依赖注入被测试Application Service。
     */
    @InjectMocks
    private ColdChainAgentApplicationServiceImpl coldChainAgentApplicationService;

    /**
     * 测试成功问答会按照CREATED、RUNNING、SUCCEEDED顺序持久化。
     */
    @Test
    void shouldPersistSuccessfulExecutionLifecycle() {
        AgentDefinition agentDefinition = AgentDefinition.of(AGENT_CODE, AGENT_NAME, "测试Agent", true, true);
        AgentChatCommand command = AgentChatCommand.of(AGENT_CODE, QUESTION);

        when(agentRegistry.getRequiredAgent(AGENT_CODE)).thenReturn(agentDefinition);
        when(agentExecutor.execute(anyString(), same(agentDefinition), any(AgentInvocationContext.class), eq(QUESTION))).thenReturn(AGENT_ANSWER);
        when(currentUserContext.getCurrentUserId()).thenReturn(90001L);
        when(currentUserContext.getCurrentTenantId()).thenReturn(1001L);

        AgentAnswerDTO agentAnswerDTO = coldChainAgentApplicationService.chat(command);

        assertNotNull(agentAnswerDTO.getRequestId());
        assertEquals(AGENT_CODE, agentAnswerDTO.getAgentCode());
        assertEquals(AGENT_ANSWER, agentAnswerDTO.getAnswer());

        /*
         * InOrder验证项目经理的调度顺序：
         * 先登记任务，再登记开工，然后启动设备，最后登记成功。
         */
        InOrder inOrder = inOrder(agentExecutionRepository, agentExecutor);
        inOrder.verify(agentExecutionRepository).saveCreated(any(AgentExecution.class));
        inOrder.verify(agentExecutionRepository).updateToRunning(any(AgentExecution.class));
        inOrder.verify(agentExecutor).execute(anyString(), same(agentDefinition), any(AgentInvocationContext.class), eq(QUESTION));
        inOrder.verify(agentExecutionRepository).updateToSucceeded(any(AgentExecution.class));
    }

    /**
     * 测试执行器异常时会把任务记录更新为FAILED。
     */
    @Test
    void shouldPersistFailedExecutionWhenExecutorThrowsException() {
        AgentDefinition agentDefinition = AgentDefinition.of(AGENT_CODE, AGENT_NAME, "测试Agent", true, true);
        AgentChatCommand command = AgentChatCommand.of(AGENT_CODE, QUESTION);

        when(currentUserContext.getCurrentUserId()).thenReturn(90001L);
        when(currentUserContext.getCurrentTenantId()).thenReturn(1001L);

        when(agentRegistry.getRequiredAgent(AGENT_CODE)).thenReturn(agentDefinition);
        when(agentExecutor.execute(anyString(), same(agentDefinition), any(AgentInvocationContext.class), eq(QUESTION))).thenThrow(new IllegalStateException("模拟模型调用失败"));

        AgentExecutionException exception = assertThrows(AgentExecutionException.class, () -> coldChainAgentApplicationService.chat(command));

        assertEquals(AgentErrorCodeEnum.AGENT_EXECUTION_ERROR.getCode(), exception.getErrorCode().getCode());

        verify(agentExecutionRepository).saveCreated(any(AgentExecution.class));
        verify(agentExecutionRepository).updateToRunning(any(AgentExecution.class));
        verify(agentExecutionRepository).updateToFailed(any(AgentExecution.class));
        verify(agentExecutionRepository, never()).updateToSucceeded(any(AgentExecution.class));
    }
}