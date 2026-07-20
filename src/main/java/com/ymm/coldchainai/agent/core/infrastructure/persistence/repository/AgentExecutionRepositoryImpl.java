package com.ymm.coldchainai.agent.core.infrastructure.persistence.repository;

import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import com.ymm.coldchainai.agent.core.domain.enumtype.AgentExecutionStatusEnum;
import com.ymm.coldchainai.agent.core.domain.model.AgentExecution;
import com.ymm.coldchainai.agent.core.domain.repository.IAgentExecutionRepository;
import com.ymm.coldchainai.agent.core.infrastructure.persistence.dataobject.AgentExecutionDO;
import com.ymm.coldchainai.agent.core.infrastructure.persistence.mapper.IAgentExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * 基于MyBatis的Agent执行记录仓储实现。
 *
 * <p>该类负责把领域对象转换成数据库对象，并检查每条INSERT或UPDATE是否准确影响一行数据。</p>
 *
 * <p>在挖矿流程中，该类相当于矿场档案主管：
 * 它把项目经理提交的业务任务单翻译成数据库表格，再要求档案员准确登记；如果影响零行或多行，就说明账本状态异常，不能假装成功。</p>
 */
@Repository
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentExecutionRepositoryImpl implements IAgentExecutionRepository {

    /**
     * 单条执行记录写操作的预期影响行数。
     */
    private static final int EXPECTED_AFFECTED_ROWS = 1;

    /**
     * 数据库错误信息允许保存的最大字符长度。
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    /**
     * Agent执行记录MyBatis Mapper。
     */
    private final IAgentExecutionMapper agentExecutionMapper;

    /**
     * 保存处于CREATED状态的新Agent执行记录。
     *
     * @param agentExecution Agent执行领域对象
     */
    @Override
    public void saveCreated(AgentExecution agentExecution) {
        validateExecutionStatus(agentExecution, AgentExecutionStatusEnum.CREATED);

        // 将领域任务单转换成数据库能够直接写入的DO。
        AgentExecutionDO agentExecutionDO = convertToDO(agentExecution, null);

        int affectedRows = agentExecutionMapper.insertCreated(agentExecutionDO);

        validateAffectedRows("插入CREATED执行记录", agentExecution.getRequestId(), affectedRows);
    }

    /**
     * 将数据库执行状态从CREATED更新为RUNNING。
     *
     * @param agentExecution 已进入RUNNING状态的Agent执行领域对象
     */
    @Override
    public void updateToRunning(AgentExecution agentExecution) {
        validateExecutionStatus(agentExecution, AgentExecutionStatusEnum.RUNNING);

        // expectedStatus表示SQL只允许把数据库中的CREATED状态更新为RUNNING。
        AgentExecutionDO agentExecutionDO = convertToDO(agentExecution, AgentExecutionStatusEnum.CREATED);

        int affectedRows = agentExecutionMapper.updateToRunning(agentExecutionDO);

        validateAffectedRows("更新执行状态为RUNNING", agentExecution.getRequestId(), affectedRows);
    }

    /**
     * 将数据库执行状态从RUNNING更新为SUCCEEDED。
     *
     * @param agentExecution 已进入SUCCEEDED状态的Agent执行领域对象
     */
    @Override
    public void updateToSucceeded(AgentExecution agentExecution) {
        validateExecutionStatus(agentExecution, AgentExecutionStatusEnum.SUCCEEDED);

        // 成功状态只能从数据库中的RUNNING状态推进。
        AgentExecutionDO agentExecutionDO = convertToDO(agentExecution, AgentExecutionStatusEnum.RUNNING);

        int affectedRows = agentExecutionMapper.updateToSucceeded(agentExecutionDO);

        validateAffectedRows("更新执行状态为SUCCEEDED", agentExecution.getRequestId(), affectedRows);
    }

    /**
     * 将数据库执行状态从RUNNING更新为FAILED。
     *
     * @param agentExecution 已进入FAILED状态的Agent执行领域对象
     */
    @Override
    public void updateToFailed(AgentExecution agentExecution) {
        validateExecutionStatus(agentExecution, AgentExecutionStatusEnum.FAILED);

        // 失败状态也只能从数据库中的RUNNING状态推进。
        AgentExecutionDO agentExecutionDO = convertToDO(agentExecution, AgentExecutionStatusEnum.RUNNING);

        int affectedRows = agentExecutionMapper.updateToFailed(agentExecutionDO);

        validateAffectedRows("更新执行状态为FAILED", agentExecution.getRequestId(), affectedRows);
    }

    /**
     * 将Agent执行领域对象转换成MyBatis持久化对象。
     *
     * @param agentExecution Agent执行领域对象
     * @param expectedStatus SQL更新要求的数据库原状态，插入时为空
     * @return Agent执行数据库对象
     */
    private AgentExecutionDO convertToDO(AgentExecution agentExecution, AgentExecutionStatusEnum expectedStatus) {
        AgentExecutionDO agentExecutionDO = new AgentExecutionDO();

        agentExecutionDO.setRequestId(agentExecution.getRequestId());
        agentExecutionDO.setAgentCode(agentExecution.getAgentCode());
        agentExecutionDO.setAgentName(agentExecution.getAgentName());
        agentExecutionDO.setQuestionLength(agentExecution.getQuestionLength());
        agentExecutionDO.setExecutionStatus(agentExecution.getStatus().getCode());
        agentExecutionDO.setExpectedStatus(Objects.isNull(expectedStatus) ? null : expectedStatus.getCode());
        agentExecutionDO.setAnswerLength(agentExecution.getAnswerLength());
        agentExecutionDO.setErrorCode(agentExecution.getErrorCode());
        // 即使上层错误信息意外过长，也只保存前512个字符，避免数据库字段溢出进一步掩盖原始Agent异常。
        agentExecutionDO.setErrorMessage(StringUtils.left(agentExecution.getErrorMessage(), MAX_ERROR_MESSAGE_LENGTH));
        agentExecutionDO.setCreateTime(agentExecution.getCreateTime());
        agentExecutionDO.setStartTime(agentExecution.getStartTime());
        agentExecutionDO.setFinishTime(agentExecution.getFinishTime());
        agentExecutionDO.setCostMillis(agentExecution.getCostMillis());

        return agentExecutionDO;
    }

    /**
     * 校验待持久化领域对象及其目标状态。
     *
     * @param agentExecution Agent执行领域对象
     * @param expectedStatus Repository方法要求的目标状态
     */
    private void validateExecutionStatus(AgentExecution agentExecution, AgentExecutionStatusEnum expectedStatus) {
        if (Objects.isNull(agentExecution)) {
            throw createPersistenceException("Agent执行领域对象不能为空");
        }

        if (!Objects.equals(expectedStatus, agentExecution.getStatus())) {
            String detailMessage = "执行状态不符合持久化要求，requestId=%s，expectedStatus=%s，actualStatus=%s"
                    .formatted(agentExecution.getRequestId(), expectedStatus, agentExecution.getStatus());

            throw createPersistenceException(detailMessage);
        }
    }

    /**
     * 校验数据库写操作影响行数。
     *
     * @param action 当前数据库操作说明
     * @param requestId Agent请求唯一标识
     * @param affectedRows 数据库实际影响行数
     */
    private void validateAffectedRows(String action, String requestId, int affectedRows) {
        if (affectedRows == EXPECTED_AFFECTED_ROWS) {
            return;
        }

        // 影响零行通常表示requestId不存在或数据库状态不符合WHERE条件；影响多行则说明唯一约束或SQL条件出现严重问题。
        String detailMessage = "%s失败，requestId=%s，affectedRows=%s".formatted(action, requestId, affectedRows);

        throw createPersistenceException(detailMessage);
    }

    /**
     * 创建Agent执行记录持久化异常。
     *
     * @param detailMessage 具体持久化错误信息
     * @return 系统内部状态异常
     */
    private IllegalStateException createPersistenceException(String detailMessage) {
        String errorMessage = "%s：%s".formatted(AgentErrorCodeEnum.AGENT_EXECUTION_PERSISTENCE_ERROR.getMessage(), detailMessage);
        return new IllegalStateException(errorMessage);
    }
}
