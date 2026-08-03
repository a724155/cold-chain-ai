package com.ymm.coldchainai.agent.audit.infrastructure.persistence.repository;

import com.ymm.coldchainai.agent.audit.application.enumtype.ToolAuditErrorCodeEnum;
import com.ymm.coldchainai.agent.audit.domain.enumtype.ToolExecutionStatusEnum;
import com.ymm.coldchainai.agent.audit.domain.model.ToolExecution;
import com.ymm.coldchainai.agent.audit.domain.repository.IToolExecutionRepository;
import com.ymm.coldchainai.agent.audit.infrastructure.persistence.dataobject.ToolExecutionDO;
import com.ymm.coldchainai.agent.audit.infrastructure.persistence.mapper.IToolExecutionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于MyBatis的Agent Tool执行审计Repository实现。
 *
 * <p>该组件负责ToolExecution领域模型与ToolExecutionDO之间的转换，
 * 并检查每条INSERT或者UPDATE是否准确影响一行数据。</p>
 *
 * <p>输入摘要、输出摘要和错误信息在进入数据库前会按照字段长度进行安全截断，
 * 避免审计字段意外过长导致持久化失败，并进一步掩盖原始Tool执行结果。</p>
 *
 * <p>在挖矿流程中，该Repository相当于外协设备档案主管：
 * 它检查项目经理交来的设备任务单状态，转换成数据库表格并要求档案员准确更新一条记录。</p>
 */
@Repository
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ToolExecutionRepositoryImpl implements IToolExecutionRepository {

    /**
     * 单条Tool审计写操作的预期影响行数。
     */
    private static final int EXPECTED_AFFECTED_ROWS = 1;

    /**
     * Tool入参摘要数据库最大长度。
     */
    private static final int MAX_INPUT_SUMMARY_LENGTH = 1024;

    /**
     * Tool输出摘要数据库最大长度。
     */
    private static final int MAX_OUTPUT_SUMMARY_LENGTH = 1024;

    /**
     * Tool失败错误信息数据库最大长度。
     */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

    /**
     * Tool执行审计MyBatis Mapper。
     */
    private final IToolExecutionMapper toolExecutionMapper;

    /**
     * 保存一条处于RUNNING状态的Tool执行记录。
     *
     * @param toolExecution 已开始执行的Tool审计领域对象
     */
    @Override
    public void saveRunning(ToolExecution toolExecution) {
        // Repository边界必须校验领域对象和目标状态，防止错误状态写入INSERT。
        validateExecutionStatus(toolExecution, ToolExecutionStatusEnum.RUNNING);

        // 将领域任务单转换成MyBatis能够写入数据库的DO。
        ToolExecutionDO toolExecutionDO = convertToDO(toolExecution, null);

        // Mapper执行单条INSERT并返回数据库实际影响行数。
        int affectedRows = toolExecutionMapper.insertRunning(toolExecutionDO);

        // Tool执行记录必须准确插入一行，零行或者多行都属于持久化异常。
        validateAffectedRows("插入RUNNING Tool审计记录", toolExecution.getToolExecutionId(), affectedRows);
    }

    /**
     * 将Tool执行记录从RUNNING更新为SUCCEEDED。
     *
     * @param toolExecution 已进入SUCCEEDED状态的Tool审计领域对象
     */
    @Override
    public void updateToSucceeded(ToolExecution toolExecution) {
        // 只有已经执行succeed()的领域对象才能进入成功持久化链路。
        validateExecutionStatus(toolExecution, ToolExecutionStatusEnum.SUCCEEDED);

        /*
         * expectedStatus用于SQL WHERE条件。
         * 数据库只有仍处于RUNNING时才允许更新为SUCCEEDED。
         */
        ToolExecutionDO toolExecutionDO = convertToDO(toolExecution, ToolExecutionStatusEnum.RUNNING);

        // 使用一条条件UPDATE原子推进状态，不需要额外SELECT FOR UPDATE。
        int affectedRows = toolExecutionMapper.updateToSucceeded(toolExecutionDO);

        // 更新零行通常表示记录不存在、已经完成或者发生重复状态推进。
        validateAffectedRows("更新Tool审计状态为SUCCEEDED", toolExecution.getToolExecutionId(), affectedRows);
    }

    /**
     * 将Tool执行记录从RUNNING更新为FAILED。
     *
     * @param toolExecution 已进入FAILED状态的Tool审计领域对象
     */
    @Override
    public void updateToFailed(ToolExecution toolExecution) {
        // 只有已经执行fail()的领域对象才能进入失败持久化链路。
        validateExecutionStatus(toolExecution, ToolExecutionStatusEnum.FAILED);

        // FAILED同样只能从数据库RUNNING状态推进。
        ToolExecutionDO toolExecutionDO = convertToDO(toolExecution, ToolExecutionStatusEnum.RUNNING);

        // 使用单条条件UPDATE保存错误码、错误信息、完成时间和耗时。
        int affectedRows = toolExecutionMapper.updateToFailed(toolExecutionDO);

        // 严格校验影响行数，避免失败审计没有真正落库却继续运行。
        validateAffectedRows("更新Tool审计状态为FAILED", toolExecution.getToolExecutionId(), affectedRows);
    }

    /**
     * 根据requestId和数据所有者查询Tool执行审计列表。
     *
     * <p>Repository同时校验用户、租户和requestId，防止错误内部调用绕过Application层的数据权限约束。</p>
     *
     * @param requestId Agent请求唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @return 按审计记录插入顺序排列的Tool执行领域对象
     */
    @Override
    public List<ToolExecution> listByRequestIdAndOwner(String requestId, Long currentUserId, Long currentTenantId) {

        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("查询Tool审计时requestId不能为空");
        }

        if (Objects.isNull(currentUserId) || currentUserId <= 0L) {
            throw new IllegalArgumentException("查询Tool审计时当前用户ID必须大于0");
        }

        if (Objects.isNull(currentTenantId) || currentTenantId <= 0L) {
            throw new IllegalArgumentException("查询Tool审计时当前租户ID必须大于0");
        }

        // Mapper异常返回null时按空列表处理，避免Repository直接对null执行Stream操作。
        List<ToolExecutionDO> toolExecutionDOList = ListUtils.emptyIfNull(
                toolExecutionMapper.selectByRequestIdAndOwner(StringUtils.trim(requestId), currentUserId, currentTenantId));

        /*
         * 在一次Stream遍历中完成DO元素判空和领域对象恢复。
         * Stream.toList()是JDK 16新增API，返回不可修改List；
         * 审计查询结果不应被Application层随意增删，因此这里使用不可修改结果更安全。
         */
        return toolExecutionDOList.stream()
                .map(toolExecutionDO -> {
                    // 查询结果包含空DO说明Mapper或框架转换异常，不能通过filter静默丢弃。
                    if (Objects.isNull(toolExecutionDO)) {
                        throw new IllegalStateException("Tool执行审计查询结果不能包含空DO");
                    }
                    // 将数据库状态码和字段组合恢复为经过领域规则校验的ToolExecution。
                    return convertToDomain(toolExecutionDO);
                })
                .toList();
    }

    /**
     * 根据toolExecutionId和数据所有者查询单次Tool执行审计记录。
     *
     * <p>Repository再次校验toolExecutionId、用户ID和租户ID，
     * 防止未来其他Application Service绕过当前查询Command直接传入非法参数。</p>
     *
     * @param toolExecutionId Tool执行业务唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @return 当前用户和租户有权访问的Tool执行领域对象
     */
    @Override
    public Optional<ToolExecution> findByToolExecutionIdAndOwner(String toolExecutionId, Long currentUserId, Long currentTenantId) {

        if (StringUtils.isBlank(toolExecutionId)) {
            throw new IllegalArgumentException("查询Tool审计时toolExecutionId不能为空");
        }

        if (Objects.isNull(currentUserId) || currentUserId <= 0L) {
            throw new IllegalArgumentException("查询Tool审计时当前用户ID必须大于0");
        }

        if (Objects.isNull(currentTenantId) || currentTenantId <= 0L) {
            throw new IllegalArgumentException("查询Tool审计时当前租户ID必须大于0");
        }

        // 同时携带Tool执行标识、用户和租户完成数据所有权查询。
        ToolExecutionDO toolExecutionDO = toolExecutionMapper.selectByToolExecutionIdAndOwner(
                StringUtils.trim(toolExecutionId), currentUserId, currentTenantId);

        // DO为空时返回Optional.empty()，非空时返回包含DO的Optional，避免Repository把null领域对象直接暴露给Application层。
        return Optional.ofNullable(toolExecutionDO)
                // 将数据库状态码和终态字段恢复为经过领域规则校验的ToolExecution。
                .map(this::convertToDomain);
    }


    /**
     * 将MyBatis查询结果恢复成Tool执行审计领域对象。
     *
     * <p>恢复时会把executionStatus整数编码转换成领域枚举，并校验RUNNING、SUCCEEDED和FAILED对应字段组合是否一致。</p>
     *
     * @param toolExecutionDO Tool执行审计数据库对象
     * @return 恢复完成的Tool执行领域对象
     */
    private ToolExecution convertToDomain(ToolExecutionDO toolExecutionDO) {
        if (Objects.isNull(toolExecutionDO)) {
            throw new IllegalStateException("Tool执行审计DO不能为空");
        }
        // 数据库状态码必须转换为领域枚举，未知状态码会由fromCode()明确阻断。
        ToolExecutionStatusEnum executionStatus = ToolExecutionStatusEnum.fromCode(toolExecutionDO.getExecutionStatus());

        return ToolExecution.restore(
                toolExecutionDO.getId(),
                toolExecutionDO.getToolExecutionId(),
                toolExecutionDO.getRequestId(),
                toolExecutionDO.getAgentCode(),
                toolExecutionDO.getToolName(),
                toolExecutionDO.getCurrentUserId(),
                toolExecutionDO.getCurrentTenantId(),
                toolExecutionDO.getInputSummary(),
                toolExecutionDO.getOutputSummary(),
                executionStatus,
                toolExecutionDO.getErrorCode(),
                toolExecutionDO.getErrorMessage(),
                toolExecutionDO.getStartTime(),
                toolExecutionDO.getFinishTime(),
                toolExecutionDO.getCostMillis());
    }

    /**
     * 将ToolExecution领域模型转换成MyBatis持久化对象。
     *
     * <p>该转换不会重新计算耗时或者改变执行状态，
     * 领域行为必须在进入Repository前完成。</p>
     *
     * @param toolExecution Tool执行审计领域对象
     * @param expectedStatus SQL更新要求的数据库原状态，INSERT时为空
     * @return 可交给MyBatis Mapper的数据库对象
     */
    private ToolExecutionDO convertToDO(ToolExecution toolExecution, ToolExecutionStatusEnum expectedStatus) {

        // 创建只负责数据库字段映射的持久化对象。
        ToolExecutionDO toolExecutionDO = new ToolExecutionDO();

        // 映射数据库主键和Tool执行链路标识。
        toolExecutionDO.setId(toolExecution.getId());
        toolExecutionDO.setToolExecutionId(toolExecution.getToolExecutionId());
        toolExecutionDO.setRequestId(toolExecution.getRequestId());

        // 映射Agent、Tool和受信任用户租户身份。
        toolExecutionDO.setAgentCode(toolExecution.getAgentCode());
        toolExecutionDO.setToolName(toolExecution.getToolName());
        toolExecutionDO.setCurrentUserId(toolExecution.getCurrentUserId());
        toolExecutionDO.setCurrentTenantId(toolExecution.getCurrentTenantId());

        // 摘要字段按照数据库最大长度进行防御性截断。StringUtils.left在字符串为null时会安全返回null，不会产生空指针。
        toolExecutionDO.setInputSummary(StringUtils.left(toolExecution.getInputSummary(), MAX_INPUT_SUMMARY_LENGTH));

        toolExecutionDO.setOutputSummary(StringUtils.left(toolExecution.getOutputSummary(), MAX_OUTPUT_SUMMARY_LENGTH));

        // 映射当前领域状态和状态更新SQL要求的数据库原状态。
        toolExecutionDO.setExecutionStatus(toolExecution.getExecutionStatus().getCode());
        toolExecutionDO.setExpectedStatus(Objects.isNull(expectedStatus) ? null : expectedStatus.getCode());

        // 错误信息同样限制最大长度，避免异常信息过长造成二次数据库异常。
        toolExecutionDO.setErrorCode(toolExecution.getErrorCode());
        toolExecutionDO.setErrorMessage(StringUtils.left(toolExecution.getErrorMessage(), MAX_ERROR_MESSAGE_LENGTH));

        // 映射Tool开始、完成时间和最终耗时。
        toolExecutionDO.setStartTime(toolExecution.getStartTime());
        toolExecutionDO.setFinishTime(toolExecution.getFinishTime());
        toolExecutionDO.setCostMillis(toolExecution.getCostMillis());

        return toolExecutionDO;
    }

    /**
     * 校验待持久化领域对象及其目标状态。
     *
     * @param toolExecution Tool执行审计领域对象
     * @param expectedStatus Repository方法要求的当前领域状态
     */
    private void validateExecutionStatus(ToolExecution toolExecution, ToolExecutionStatusEnum expectedStatus) {

        if (Objects.isNull(toolExecution)) {
            throw createPersistenceException("Tool执行审计领域对象不能为空");
        }

        // Repository方法和领域对象状态必须完全一致，禁止调用错误持久化方法。
        if (!Objects.equals(expectedStatus, toolExecution.getExecutionStatus())) {
            String detailMessage = "Tool执行状态不符合持久化要求，toolExecutionId=%s，expectedStatus=%s，actualStatus=%s"
                    .formatted(toolExecution.getToolExecutionId(), expectedStatus, toolExecution.getExecutionStatus());

            throw createPersistenceException(detailMessage);
        }
    }

    /**
     * 校验数据库写操作影响行数。
     *
     * @param action 当前数据库操作说明
     * @param toolExecutionId Tool执行业务唯一标识
     * @param affectedRows 数据库实际影响行数
     */
    private void validateAffectedRows(String action, String toolExecutionId, int affectedRows) {

        // 准确影响一行表示当前写操作符合预期，可以正常结束。
        if (affectedRows == EXPECTED_AFFECTED_ROWS) {
            return;
        }
        // 影响零行通常表示记录不存在或者数据库状态不符合WHERE条件；影响多行则说明唯一约束或者SQL条件发生严重错误。
        String detailMessage = "%s失败，toolExecutionId=%s，affectedRows=%s".formatted(action, toolExecutionId, affectedRows);

        throw createPersistenceException(detailMessage);
    }

    /**
     * 创建Tool执行审计持久化异常。
     *
     * @param detailMessage 具体持久化错误信息
     * @return 系统内部状态异常
     */
    private IllegalStateException createPersistenceException(String detailMessage) {
        // 错误提示统一从Tool审计错误码枚举读取，禁止在Repository中使用魔法错误码。
        String errorMessage = "%s：%s".formatted(ToolAuditErrorCodeEnum.TOOL_AUDIT_PERSISTENCE_ERROR.getMessage(), detailMessage);

        return new IllegalStateException(errorMessage);
    }
}
