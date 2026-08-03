package com.ymm.coldchainai.agent.audit.application.dto;

import com.ymm.coldchainai.agent.audit.domain.model.ToolExecution;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tool执行审计查询结果DTO。
 *
 * <p>该DTO只返回审计表中的安全摘要，不包含订单列表、
 * 支付单明细、内部规范原文或者异常堆栈。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class ToolExecutionRecordDTO {

    /**
     * 单次Tool执行业务唯一标识。
     */
    private final String toolExecutionId;

    /**
     * 所属Agent请求标识。
     */
    private final String requestId;

    /**
     * 实际发起调用的Agent编码。
     */
    private final String agentCode;

    /**
     * Tool稳定名称。
     */
    private final String toolName;

    /**
     * 当前用户ID。
     */
    private final Long currentUserId;

    /**
     * 当前租户ID。
     */
    private final Long currentTenantId;

    /**
     * Tool输入安全摘要。
     */
    private final String inputSummary;

    /**
     * Tool输出安全摘要。
     */
    private final String outputSummary;

    /**
     * Tool执行状态码。
     */
    private final Integer executionStatusCode;

    /**
     * Tool执行状态说明。
     */
    private final String executionStatusMessage;

    /**
     * Tool执行失败错误码。
     */
    private final Integer errorCode;

    /**
     * Tool执行失败安全错误信息。
     */
    private final String errorMessage;

    /**
     * Tool开始时间。
     */
    private final LocalDateTime startTime;

    /**
     * Tool完成时间。
     */
    private final LocalDateTime finishTime;

    /**
     * Tool执行耗时，单位为毫秒。
     */
    private final Long costMillis;

    /**
     * 将ToolExecution领域对象转换成查询DTO。
     *
     * @param toolExecution Tool执行审计领域对象
     * @return Application查询DTO
     */
    public static ToolExecutionRecordDTO fromDomain(ToolExecution toolExecution) {
        if (Objects.isNull(toolExecution)) {
            throw new IllegalArgumentException("Tool执行审计领域对象不能为空");
        }

        if (Objects.isNull(toolExecution.getExecutionStatus())) {
            throw new IllegalArgumentException("Tool执行状态不能为空");
        }

        // DTO只读取已经经过领域模型校验的安全审计字段。
        return ToolExecutionRecordDTO.of(
                toolExecution.getToolExecutionId(),
                toolExecution.getRequestId(),
                toolExecution.getAgentCode(),
                toolExecution.getToolName(),
                toolExecution.getCurrentUserId(),
                toolExecution.getCurrentTenantId(),
                toolExecution.getInputSummary(),
                toolExecution.getOutputSummary(),
                toolExecution.getExecutionStatus().getCode(),
                toolExecution.getExecutionStatus().getMessage(),
                toolExecution.getErrorCode(),
                toolExecution.getErrorMessage(),
                toolExecution.getStartTime(),
                toolExecution.getFinishTime(),
                toolExecution.getCostMillis());
    }
}
