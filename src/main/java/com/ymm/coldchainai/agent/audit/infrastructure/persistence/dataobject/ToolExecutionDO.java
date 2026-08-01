package com.ymm.coldchainai.agent.audit.infrastructure.persistence.dataobject;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent Tool执行审计数据库持久化对象。
 *
 * <p>该对象与cold_chain_ai_tool_execution表字段对应，
 * 只负责在Java对象与MySQL记录之间搬运数据，不承担状态流转业务规则。</p>
 *
 * <p>ToolExecution是理解执行生命周期的领域任务单，
 * ToolExecutionDO则是MyBatis能够识别的数据库档案格式。</p>
 *
 * <p>在挖矿流程中，该DO相当于档案仓库使用的固定纸质表格，
 * Repository负责把外协设备作业任务单填写到这张表格中。</p>
 */
@Getter
@Setter
public class ToolExecutionDO {

    /**
     * 数据库内部自增主键。
     */
    private Long id;

    /**
     * 单次Tool执行业务唯一标识。
     */
    private String toolExecutionId;

    /**
     * 当前Tool调用所属Agent请求标识。
     */
    private String requestId;

    /**
     * 发起Tool调用的Agent稳定编码。
     */
    private String agentCode;

    /**
     * Spring AI Tool稳定名称。
     */
    private String toolName;

    /**
     * 发起Agent请求的受信任用户ID。
     */
    private Long currentUserId;

    /**
     * 发起Agent请求的受信任租户ID。
     */
    private Long currentTenantId;

    /**
     * Tool入参安全摘要。
     */
    private String inputSummary;

    /**
     * Tool输出安全摘要。
     */
    private String outputSummary;

    /**
     * Tool当前执行状态数据库编码。
     */
    private Integer executionStatus;

    /**
     * 状态更新SQL要求数据库记录当前必须处于的原状态。
     *
     * <p>该字段不对应数据库列，只作为Mapper UPDATE语句的WHERE条件，
     * 防止SUCCEEDED或者FAILED记录被重复推进。</p>
     */
    private Integer expectedStatus;

    /**
     * Tool失败错误码。
     */
    private Integer errorCode;

    /**
     * Tool失败安全错误信息。
     */
    private String errorMessage;

    /**
     * Tool开始执行时间。
     */
    private LocalDateTime startTime;

    /**
     * Tool完成或者失败时间。
     */
    private LocalDateTime finishTime;

    /**
     * Tool执行耗时，单位为毫秒。
     */
    private Long costMillis;
}
