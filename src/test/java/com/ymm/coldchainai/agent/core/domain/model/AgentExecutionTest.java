package com.ymm.coldchainai.agent.core.domain.model;

import com.ymm.coldchainai.agent.core.domain.enumtype.AgentExecutionStatusEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AgentExecution领域对象单元测试。
 *
 * <p>该测试不启动Spring容器、不调用模型和数据库，
 * 只验证Agent任务单自身的状态流转和字段维护规则。</p>
 */
class AgentExecutionTest {

    /**
     * 测试Agent编码。
     */
    private static final String AGENT_CODE = "cold-chain-general";

    /**
     * 测试Agent名称。
     */
    private static final String AGENT_NAME = "冷运综合业务助手";

    /**
     * 测试requestId。
     */
    private static final String REQUEST_ID = "request-001";

    /**
     * 测试新建执行记录初始状态为CREATED。
     */
    @Test
    void shouldCreateAgentExecutionWithCreatedStatus() {
        AgentDefinition agentDefinition = createAgentDefinition();

        // 创建任务单时只记录任务基础信息，尚未正式启动设备。
        AgentExecution agentExecution = AgentExecution.create(REQUEST_ID, agentDefinition, "测试Agent执行记录");

        assertEquals(REQUEST_ID, agentExecution.getRequestId());
        assertEquals(AGENT_CODE, agentExecution.getAgentCode());
        assertEquals(AGENT_NAME, agentExecution.getAgentName());
        assertEquals(AgentExecutionStatusEnum.CREATED, agentExecution.getStatus());
        assertEquals("测试Agent执行记录".length(), agentExecution.getQuestionLength());
        assertNotNull(agentExecution.getCreateTime());
        assertNull(agentExecution.getStartTime());
        assertNull(agentExecution.getFinishTime());
        assertNull(agentExecution.getCostMillis());
    }

    /**
     * 测试Agent任务能够从CREATED流转到RUNNING，再流转到SUCCEEDED。
     */
    @Test
    void shouldMarkAgentExecutionAsSucceeded() {
        AgentExecution agentExecution = AgentExecution.create(REQUEST_ID, createAgentDefinition(), "测试成功状态");

        // start相当于任务单正式开工，状态必须从CREATED推进到RUNNING。
        agentExecution.start();

        assertEquals(AgentExecutionStatusEnum.RUNNING, agentExecution.getStatus());
        assertNotNull(agentExecution.getStartTime());

        // succeed相当于作业完成并验收通过，任务单进入不可逆的成功最终状态。
        agentExecution.succeed("Agent执行成功");

        assertEquals(AgentExecutionStatusEnum.SUCCEEDED, agentExecution.getStatus());
        assertEquals("Agent执行成功".length(), agentExecution.getAnswerLength());
        assertNotNull(agentExecution.getFinishTime());
        assertNotNull(agentExecution.getCostMillis());
        assertTrue(agentExecution.getCostMillis() >= 0L);
        assertNull(agentExecution.getErrorCode());
    }

    /**
     * 测试Agent任务能够从RUNNING流转到FAILED。
     */
    @Test
    void shouldMarkAgentExecutionAsFailed() {
        AgentExecution agentExecution = AgentExecution.create(REQUEST_ID, createAgentDefinition(), "测试失败状态");

        agentExecution.start();

        // 失败时记录安全错误码和提示，但不保存完整异常堆栈。
        agentExecution.fail(50001, "Agent执行失败，请稍后重试");

        assertEquals(AgentExecutionStatusEnum.FAILED, agentExecution.getStatus());
        assertEquals(50001, agentExecution.getErrorCode());
        assertEquals("Agent执行失败，请稍后重试", agentExecution.getErrorMessage());
        assertNotNull(agentExecution.getFinishTime());
        assertNotNull(agentExecution.getCostMillis());
    }

    /**
     * 测试任务尚未开始时不能直接标记为成功。
     */
    @Test
    void shouldRejectSuccessBeforeExecutionStarts() {
        AgentExecution agentExecution = AgentExecution.create(REQUEST_ID, createAgentDefinition(), "测试非法状态流转");

        // 没有开工就直接宣布任务成功会破坏执行记录可信度，因此必须拒绝。
        assertThrows(IllegalStateException.class, () -> agentExecution.succeed("非法成功答案"));

        assertEquals(AgentExecutionStatusEnum.CREATED, agentExecution.getStatus());
    }

    /**
     * 创建单元测试统一使用的Agent定义。
     *
     * @return 启用的默认Agent定义
     */
    private AgentDefinition createAgentDefinition() {
        return AgentDefinition.of(AGENT_CODE, AGENT_NAME, "测试Agent执行状态", true, true);
    }
}
