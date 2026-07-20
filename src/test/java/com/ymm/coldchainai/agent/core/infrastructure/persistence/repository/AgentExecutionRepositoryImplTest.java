package com.ymm.coldchainai.agent.core.infrastructure.persistence.repository;

import com.ymm.coldchainai.agent.core.domain.enumtype.AgentExecutionStatusEnum;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.domain.model.AgentExecution;
import com.ymm.coldchainai.agent.core.infrastructure.persistence.dataobject.AgentExecutionDO;
import com.ymm.coldchainai.agent.core.infrastructure.persistence.mapper.IAgentExecutionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentExecutionRepositoryImpl单元测试。
 *
 * <p>该测试使用Mockito模拟Mapper，不连接真实数据库，
 * 重点验证领域对象到DO的转换、状态条件和影响行数校验。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentExecutionRepositoryImplTest {

    /**
     * 测试Agent编码。
     */
    private static final String AGENT_CODE = "cold-chain-general";

    /**
     * 测试requestId。
     */
    private static final String REQUEST_ID = "request-001";

    /**
     * 模拟Agent执行记录Mapper。
     */
    @Mock
    private IAgentExecutionMapper agentExecutionMapper;

    /**
     * 将模拟Mapper注入被测试Repository。
     */
    @InjectMocks
    private AgentExecutionRepositoryImpl agentExecutionRepository;

    /**
     * 测试CREATED执行记录可以正常插入。
     */
    @Test
    void shouldInsertCreatedExecution() {
        AgentExecution agentExecution = createAgentExecution();

        when(agentExecutionMapper.insertCreated(any(AgentExecutionDO.class))).thenReturn(1);

        agentExecutionRepository.saveCreated(agentExecution);

        ArgumentCaptor<AgentExecutionDO> agentExecutionDOCaptor = ArgumentCaptor.forClass(AgentExecutionDO.class);
        verify(agentExecutionMapper).insertCreated(agentExecutionDOCaptor.capture());

        AgentExecutionDO agentExecutionDO = agentExecutionDOCaptor.getValue();

        assertEquals(REQUEST_ID, agentExecutionDO.getRequestId());
        assertEquals(AGENT_CODE, agentExecutionDO.getAgentCode());
        assertEquals(AgentExecutionStatusEnum.CREATED.getCode(), agentExecutionDO.getExecutionStatus());
    }

    /**
     * 测试RUNNING更新会携带CREATED原状态条件。
     */
    @Test
    void shouldUpdateCreatedExecutionToRunning() {
        AgentExecution agentExecution = createAgentExecution();
        agentExecution.start();

        when(agentExecutionMapper.updateToRunning(any(AgentExecutionDO.class))).thenReturn(1);

        agentExecutionRepository.updateToRunning(agentExecution);

        ArgumentCaptor<AgentExecutionDO> agentExecutionDOCaptor = ArgumentCaptor.forClass(AgentExecutionDO.class);
        verify(agentExecutionMapper).updateToRunning(agentExecutionDOCaptor.capture());

        AgentExecutionDO agentExecutionDO = agentExecutionDOCaptor.getValue();

        assertEquals(AgentExecutionStatusEnum.CREATED.getCode(), agentExecutionDO.getExpectedStatus());
        assertEquals(AgentExecutionStatusEnum.RUNNING.getCode(), agentExecutionDO.getExecutionStatus());
    }

    /**
     * 测试SUCCEEDED更新会携带RUNNING原状态条件。
     */
    @Test
    void shouldUpdateRunningExecutionToSucceeded() {
        AgentExecution agentExecution = createAgentExecution();
        agentExecution.start();
        agentExecution.succeed("Agent执行成功");

        when(agentExecutionMapper.updateToSucceeded(any(AgentExecutionDO.class))).thenReturn(1);

        agentExecutionRepository.updateToSucceeded(agentExecution);

        ArgumentCaptor<AgentExecutionDO> agentExecutionDOCaptor = ArgumentCaptor.forClass(AgentExecutionDO.class);
        verify(agentExecutionMapper).updateToSucceeded(agentExecutionDOCaptor.capture());

        AgentExecutionDO agentExecutionDO = agentExecutionDOCaptor.getValue();

        assertEquals(AgentExecutionStatusEnum.RUNNING.getCode(), agentExecutionDO.getExpectedStatus());
        assertEquals(AgentExecutionStatusEnum.SUCCEEDED.getCode(), agentExecutionDO.getExecutionStatus());
        assertEquals("Agent执行成功".length(), agentExecutionDO.getAnswerLength());
    }

    /**
     * 测试数据库影响零行时Repository拒绝假装更新成功。
     */
    @Test
    void shouldThrowExceptionWhenAffectedRowsIsZero() {
        AgentExecution agentExecution = createAgentExecution();
        agentExecution.start();

        // 返回0表示requestId不存在或数据库当前状态不是CREATED。
        when(agentExecutionMapper.updateToRunning(any(AgentExecutionDO.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> agentExecutionRepository.updateToRunning(agentExecution));
    }

    /**
     * 创建测试使用的CREATED状态Agent执行记录。
     *
     * @return Agent执行领域对象
     */
    private AgentExecution createAgentExecution() {
        AgentDefinition agentDefinition = AgentDefinition.of(AGENT_CODE, "冷运综合业务助手", "测试Agent", true, true);
        return AgentExecution.create(REQUEST_ID, agentDefinition, "测试Agent执行记录持久化");
    }
}
