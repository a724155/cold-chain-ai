package com.ymm.coldchainai.agent.core.domain.enumtype;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent执行状态枚举。
 *
 * <p>该枚举描述一次Agent任务从创建到结束所经历的稳定状态，
 * 后续写入数据库时也应保存状态编码，而不是直接保存容易变化的中文说明。</p>
 *
 * <p>在挖矿流程中，该枚举相当于矿场任务单上的作业状态：
 * 已创建表示任务单刚生成，执行中表示设备已经开工，
 * 执行成功或执行失败表示本次任务已经结束。</p>
 */
@Getter
@AllArgsConstructor
public enum AgentExecutionStatusEnum {

    /**
     * Agent执行记录已经创建，但尚未开始调用执行器。
     */
    CREATED(0, "已创建", false),

    /**
     * Agent执行器已经开始工作，正在等待模型或Tool完成。
     */
    RUNNING(10, "执行中", false),

    /**
     * Agent已经成功生成最终答案。
     */
    SUCCEEDED(20, "执行成功", true),

    /**
     * Agent执行过程中发生业务失败或系统异常。
     */
    FAILED(30, "执行失败", true);

    /**
     * 状态编码。
     *
     * <p>后续数据库建议保存该字段，避免直接依赖枚举名称或中文说明。</p>
     */
    private final Integer code;

    /**
     * 状态中文说明。
     */
    private final String description;

    /**
     * 当前状态是否属于最终状态。
     *
     * <p>成功和失败都属于最终状态，进入最终状态后不允许再次流转。</p>
     */
    private final Boolean terminal;
}
