package com.ymm.coldchainai.agent.audit.domain.repository;

import com.ymm.coldchainai.agent.audit.domain.model.ToolExecution;

/**
 * Agent Tool执行审计Repository领域端口。
 *
 * <p>Application层通过该接口保存Tool执行生命周期，不直接依赖MyBatis Mapper、DO或者数据库表结构。</p>
 *
 * <p>在挖矿流程中，该接口相当于外协设备审计档案室的统一窗口：
 * 上层只提交“登记开工”“登记成功”或者“登记失败”指令，不需要了解档案室内部采用MySQL还是其他存储技术。</p>
 */
public interface IToolExecutionRepository {

    /**
     * 保存一条处于RUNNING状态的Tool执行记录。
     *
     * @param toolExecution 已开始执行的Tool审计领域对象
     */
    void saveRunning(ToolExecution toolExecution);

    /**
     * 将Tool执行记录从RUNNING更新为SUCCEEDED。
     *
     * @param toolExecution 已进入SUCCEEDED状态的Tool审计领域对象
     */
    void updateToSucceeded(ToolExecution toolExecution);

    /**
     * 将Tool执行记录从RUNNING更新为FAILED。
     *
     * @param toolExecution 已进入FAILED状态的Tool审计领域对象
     */
    void updateToFailed(ToolExecution toolExecution);
}
