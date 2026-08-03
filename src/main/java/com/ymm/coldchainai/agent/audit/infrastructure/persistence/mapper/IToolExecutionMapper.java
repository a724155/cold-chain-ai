package com.ymm.coldchainai.agent.audit.infrastructure.persistence.mapper;

import com.ymm.coldchainai.agent.audit.infrastructure.persistence.dataobject.ToolExecutionDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent Tool执行审计MyBatis Mapper。
 *
 * <p>该Mapper负责执行Tool审计记录的INSERT和状态更新SQL，
 * 不负责创建toolExecutionId，也不负责计算执行耗时。</p>
 *
 * <p>在挖矿流程中，该Mapper相当于真正操作设备审计账本的档案员，
 * Repository负责告诉它本次应该登记开工、成功还是失败。</p>
 */
public interface IToolExecutionMapper {

    /**
     * 插入一条处于RUNNING状态的Tool执行记录。
     *
     * @param toolExecutionDO Tool执行审计数据库对象
     * @return 实际插入记录数，正常情况下必须为1
     */
    int insertRunning(ToolExecutionDO toolExecutionDO);

    /**
     * 将Tool执行记录从RUNNING更新为SUCCEEDED。
     *
     * @param toolExecutionDO 包含成功结果和RUNNING预期状态的数据库对象
     * @return 实际更新记录数，正常情况下必须为1
     */
    int updateToSucceeded(ToolExecutionDO toolExecutionDO);

    /**
     * 将Tool执行记录从RUNNING更新为FAILED。
     *
     * @param toolExecutionDO 包含失败结果和RUNNING预期状态的数据库对象
     * @return 实际更新记录数，正常情况下必须为1
     */
    int updateToFailed(ToolExecutionDO toolExecutionDO);

    /**
     * 根据Agent requestId和用户租户所有权查询Tool执行记录。
     *
     * @param requestId Agent请求唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @return 按数据库主键升序排列的Tool执行DO列表
     */
    List<ToolExecutionDO> selectByRequestIdAndOwner(@Param("requestId") String requestId,
                                                    @Param("currentUserId") Long currentUserId,
                                                    @Param("currentTenantId") Long currentTenantId);

    /**
     * 根据Tool执行标识和用户租户所有权查询单条审计记录。
     *
     * <p>tool_execution_id存在数据库唯一索引，正常情况下最多返回一条记录。</p>
     *
     * @param toolExecutionId Tool执行业务唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @return 匹配的Tool执行DO，记录不存在或无权访问时返回null
     */
    ToolExecutionDO selectByToolExecutionIdAndOwner(@Param("toolExecutionId") String toolExecutionId,
                                                    @Param("currentUserId") Long currentUserId,
                                                    @Param("currentTenantId") Long currentTenantId);
}
