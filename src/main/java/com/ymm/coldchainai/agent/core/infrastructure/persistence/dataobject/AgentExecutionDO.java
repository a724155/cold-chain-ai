package com.ymm.coldchainai.agent.core.infrastructure.persistence.dataobject;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent执行记录数据库对象。
 *
 * <p>该对象负责承载MyBatis写入数据库需要的字段，
 * 不作为Controller响应，也不直接代替领域对象AgentExecution。</p>
 *
 * <p>在挖矿流程中，该对象相当于档案员最终填写到持久化账本中的表格格式；
 * AgentExecution是业务任务单，而DO是数据库能够识别的落库格式。</p>
 */
@Getter
@Setter
public class AgentExecutionDO {

    /**
     * 数据库自增主键。
     */
    private Long id;

    /**
     * Agent请求唯一标识。
     */
    private String requestId;

    /**
     * 实际执行的Agent稳定编码。
     */
    private String agentCode;

    /**
     * 实际执行的Agent名称。
     */
    private String agentName;

    /**
     * 用户问题字符长度。
     */
    private Integer questionLength;

    /**
     * 当前目标执行状态编码。
     */
    private Integer executionStatus;

    /**
     * SQL状态更新要求的原状态编码。
     *
     * <p>该字段不是数据库列，只用于WHERE条件中的状态校验。
     * 例如更新成功时，要求数据库当前状态必须仍然是RUNNING。</p>
     */
    private Integer expectedStatus;

    /**
     * 模型最终答案字符长度。
     */
    private Integer answerLength;

    /**
     * 执行失败错误编码。
     */
    private Integer errorCode;

    /**
     * 执行失败安全提示。
     */
    private String errorMessage;

    /**
     * 执行记录创建时间。
     */
    private LocalDateTime createTime;

    /**
     * Agent开始执行时间。
     */
    private LocalDateTime startTime;

    /**
     * Agent执行结束时间。
     */
    private LocalDateTime finishTime;

    /**
     * Agent实际执行耗时，单位为毫秒。
     */
    private Long costMillis;
}
