package com.ymm.coldchainai.agent.audit.application.dto;

import com.ymm.coldchainai.agent.audit.domain.enumtype.ToolExecutionStatusEnum;
import com.ymm.coldchainai.agent.audit.domain.model.ToolExecution;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tool执行审计Application凭证DTO。
 *
 * <p>startExecution()完成RUNNING记录持久化后返回该DTO。
 * Tool完成时，成功或失败方法根据该凭证恢复原RUNNING领域对象，从而继续推进同一条Tool审计记录。</p>
 *
 * <p>该DTO不包含完整Tool业务结果，只携带恢复审计任务所需的安全字段。</p>
 *
 * <p>在挖矿流程中，该DTO相当于设备开工后交给操作人员的作业回执：
 * 作业完成时需要凭回执上的任务编号和开工时间登记最终结果。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class ToolExecutionAuditDTO {

    /**
     * 单次Tool执行业务唯一标识。
     */
    private final String toolExecutionId;

    /**
     * 所属Agent请求唯一标识。
     */
    private final String requestId;

    /**
     * 发起调用的Agent稳定编码。
     */
    private final String agentCode;

    /**
     * Tool稳定名称。
     */
    private final String toolName;

    /**
     * 当前受信任用户ID。
     */
    private final Long currentUserId;

    /**
     * 当前受信任租户ID。
     */
    private final Long currentTenantId;

    /**
     * Tool入参安全摘要。
     */
    private final String inputSummary;

    /**
     * Tool原始开始时间。
     *
     * <p>成功或者失败时必须继续使用该时间计算完整执行耗时。</p>
     */
    private final LocalDateTime startTime;

    /**
     * 将RUNNING Tool执行领域对象转换成审计凭证DTO。
     *
     * @param toolExecution 已成功持久化的RUNNING Tool执行对象
     * @return Tool执行审计凭证
     */
    public static ToolExecutionAuditDTO fromDomain(ToolExecution toolExecution) {
        if (Objects.isNull(toolExecution)) {
            throw new IllegalArgumentException("Tool执行领域对象不能为空");
        }

        // 该DTO只代表已经开始的审计任务，终态对象不能再次生成RUNNING执行凭证。
        if (!Objects.equals(ToolExecutionStatusEnum.RUNNING, toolExecution.getExecutionStatus())) {
            throw new IllegalArgumentException("只有RUNNING Tool执行对象才能生成审计凭证，currentStatus=%s".formatted(toolExecution.getExecutionStatus()));
        }

        return ToolExecutionAuditDTO.of(
                toolExecution.getToolExecutionId(),
                toolExecution.getRequestId(),
                toolExecution.getAgentCode(),
                toolExecution.getToolName(),
                toolExecution.getCurrentUserId(),
                toolExecution.getCurrentTenantId(),
                toolExecution.getInputSummary(),
                toolExecution.getStartTime());
    }
}
