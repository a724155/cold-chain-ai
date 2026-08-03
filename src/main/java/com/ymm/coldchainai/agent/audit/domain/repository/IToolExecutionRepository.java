package com.ymm.coldchainai.agent.audit.domain.repository;

import com.ymm.coldchainai.agent.audit.domain.model.ToolExecution;

import java.util.List;
import java.util.Optional;

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

    /**
     * 根据Agent requestId和数据所有者查询Tool执行审计列表。
     *
     * <p>查询必须同时携带currentUserId和currentTenantId，禁止仅凭requestId读取其他用户或者其他租户的Tool调用记录。</p>
     *
     * <p>一个Agent请求可能没有调用Tool，此时返回空列表。</p>
     *
     * @param requestId Agent请求唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @return 按数据库记录顺序排列的Tool审计列表
     */
    List<ToolExecution> listByRequestIdAndOwner(String requestId, Long currentUserId, Long currentTenantId);

    /**
     * 根据Tool执行标识和数据所有者查询单次Tool执行审计记录。
     *
     * <p>查询条件必须同时包含toolExecutionId、currentUserId和currentTenantId，
     * 禁止仅凭toolExecutionId读取其他用户或者其他租户的数据。</p>
     *
     * <p>记录不存在或者不属于当前用户时统一返回Optional.empty()，
     * Repository不负责区分两种情况，也不向上层泄露记录是否真实存在。</p>
     *
     * <p>在挖矿流程中，该方法相当于根据设备作业单号和客户身份调阅单份档案，
     * 单号正确但身份不匹配时同样不允许取出档案。</p>
     *
     * @param toolExecutionId Tool执行业务唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @return 当前用户和租户有权访问的Tool审计记录
     */
    Optional<ToolExecution> findByToolExecutionIdAndOwner(String toolExecutionId, Long currentUserId, Long currentTenantId);
}
