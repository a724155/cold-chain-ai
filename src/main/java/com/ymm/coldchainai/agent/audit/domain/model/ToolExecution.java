package com.ymm.coldchainai.agent.audit.domain.model;

import com.ymm.coldchainai.agent.audit.domain.enumtype.ToolExecutionStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Agent Tool执行审计领域模型。
 *
 * <p>该模型描述一次独立Tool调用从开始到成功或者失败的完整生命周期。</p>
 *
 * <p>一次Agent请求可能不调用Tool，也可能调用一个或多个Tool，
 * 因此requestId和toolExecutionId之间是一对多关系：</p>
 *
 * <p>requestId用于关联整个Agent执行任务，
 * toolExecutionId用于唯一标识其中某一次具体Tool调用。</p>
 *
 * <p>领域模型只保存安全摘要，不保存完整Tool原始入参和返回值，
 * 避免订单、支付和内部规范数据在审计表中发生不受控复制。</p>
 *
 * <p>在挖矿流程中，该对象相当于一张外协设备作业登记单：
 * 记录哪次项目任务调用了哪台设备、由谁发起、何时开工、
 * 最终是否成功、耗时多少以及失败原因。</p>
 */
@Getter
@AllArgsConstructor
public class ToolExecution {

    /**
     * Tool没有实际业务入参时使用的统一摘要。
     */
    private static final String EMPTY_INPUT_SUMMARY = "无业务入参";

    /**
     * Tool成功但没有需要记录的返回摘要时使用的统一说明。
     */
    private static final String EMPTY_OUTPUT_SUMMARY = "执行成功，无返回摘要";

    /**
     * Tool失败但异常没有提供有效安全信息时使用的统一说明。
     */
    private static final String DEFAULT_ERROR_MESSAGE = "Tool执行失败";

    /**
     * 数据库内部自增主键。
     *
     * <p>新建领域对象时为空，后续查询恢复领域模型时才会存在。</p>
     */
    private final Long id;

    /**
     * 单次Tool执行业务唯一标识。
     */
    private final String toolExecutionId;

    /**
     * 当前Tool调用所属Agent请求唯一标识。
     *
     * <p>同一个requestId下允许存在多条ToolExecution。</p>
     */
    private final String requestId;

    /**
     * 发起当前Tool调用的Agent稳定编码。
     */
    private final String agentCode;

    /**
     * Spring AI Tool稳定名称。
     *
     * <p>该名称应该与@Tool注解中的name保持一致，
     * 例如query_driver_deal_orders。</p>
     */
    private final String toolName;

    /**
     * 发起本次Agent请求的受信任用户ID。
     */
    private final Long currentUserId;

    /**
     * 发起本次Agent请求的受信任租户ID。
     */
    private final Long currentTenantId;

    /**
     * Tool入参安全摘要。
     *
     * <p>这里只记录经过筛选的业务定位字段，禁止直接保存完整敏感请求报文。</p>
     */
    private final String inputSummary;

    /**
     * Tool输出安全摘要。
     *
     * <p>RUNNING和FAILED状态允许为空，SUCCEEDED状态会保存成功结果摘要。</p>
     */
    private String outputSummary;

    /**
     * 当前Tool执行状态。
     */
    private ToolExecutionStatusEnum executionStatus;

    /**
     * Tool失败错误码。
     *
     * <p>只有FAILED状态存在该字段。</p>
     */
    private Integer errorCode;

    /**
     * Tool失败安全错误信息。
     *
     * <p>不能直接保存异常堆栈、SQL或者敏感业务数据。</p>
     */
    private String errorMessage;

    /**
     * Tool开始执行时间。
     */
    private final LocalDateTime startTime;

    /**
     * Tool完成或者失败时间。
     */
    private LocalDateTime finishTime;

    /**
     * Tool执行耗时，单位为毫秒。
     */
    private Long costMillis;

    /**
     * 创建一条处于RUNNING状态的Tool执行审计记录。
     *
     * <p>该方法只代表审计任务已经开始，不能在这里实际调用Tool。</p>
     *
     * <p>正式调用链应当是：</p>
     *
     * <p>1. 创建RUNNING领域对象并持久化；</p>
     * <p>2. 提交审计开始事务；</p>
     * <p>3. 执行真实Tool业务；</p>
     * <p>4. 根据结果将审计记录更新为SUCCEEDED或者FAILED。</p>
     *
     * <p>不能开启数据库事务后一直等待Tool业务完成，
     * 否则RAG查询、RPC或者其他耗时操作会长期占用事务和数据库连接。</p>
     *
     * @param toolExecutionId Tool执行业务唯一标识
     * @param requestId 所属Agent请求标识
     * @param agentCode 发起调用的Agent编码
     * @param toolName Tool稳定名称
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @param inputSummary Tool入参安全摘要
     * @return 处于RUNNING状态的Tool执行领域对象
     */
    public static ToolExecution start(String toolExecutionId, String requestId, String agentCode, String toolName,
            Long currentUserId, Long currentTenantId, String inputSummary) {

        if (StringUtils.isBlank(toolExecutionId)) {
            throw new IllegalArgumentException("Tool执行标识不能为空");
        }

        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("Agent请求标识不能为空");
        }

        if (StringUtils.isBlank(agentCode)) {
            throw new IllegalArgumentException("Agent编码不能为空");
        }

        if (StringUtils.isBlank(toolName)) {
            throw new IllegalArgumentException("Tool名称不能为空");
        }

        if (Objects.isNull(currentUserId) || currentUserId <= 0) {
            throw new IllegalArgumentException("当前用户ID必须大于0");
        }

        if (Objects.isNull(currentTenantId) || currentTenantId <= 0) {
            throw new IllegalArgumentException("当前租户ID必须大于0");
        }

        // Tool允许没有业务参数，但审计表仍然需要保存明确摘要，不能使用null表达不确定状态。
        String resolvedInputSummary = StringUtils.defaultIfBlank(inputSummary, EMPTY_INPUT_SUMMARY);

        // Tool开始时间只生成一次，后续计算耗时必须以该时间为基准。
        LocalDateTime startTime = LocalDateTime.now();

        return new ToolExecution(
                null,
                StringUtils.trim(toolExecutionId),
                StringUtils.trim(requestId),
                StringUtils.trim(agentCode),
                StringUtils.trim(toolName),
                currentUserId,
                currentTenantId,
                resolvedInputSummary,
                null,
                ToolExecutionStatusEnum.RUNNING,
                null,
                null,
                startTime,
                null,
                null);
    }

    /**
     * 根据已经持久化的RUNNING审计信息恢复Tool执行领域对象。
     *
     * <p>Tool开始、成功和失败分别由三个独立短事务完成。
     * startExecution()事务提交后，Application层只保留ToolExecutionAuditDTO；
     * Tool执行结束时，需要根据DTO重新恢复RUNNING领域对象，
     * 再调用succeed()或者fail()完成领域状态推进。</p>
     *
     * <p>这里不能直接创建SUCCEEDED或者FAILED对象，
     * 因为完成时间、执行耗时以及终态字段仍然必须经过领域行为统一计算。</p>
     *
     * <p>在挖矿流程中，该方法相当于设备作业结束后，
     * 档案员根据之前开具的RUNNING作业回执重新找到原任务单，
     * 然后再登记成功结果或者失败原因。</p>
     *
     * @param toolExecutionId Tool执行业务唯一标识
     * @param requestId 所属Agent请求唯一标识
     * @param agentCode 发起调用的Agent稳定编码
     * @param toolName Tool稳定名称
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @param inputSummary Tool入参安全摘要
     * @param startTime Tool原始开始执行时间
     * @return 已恢复的RUNNING Tool执行领域对象
     */
    public static ToolExecution restoreRunning(
            String toolExecutionId,
            String requestId,
            String agentCode,
            String toolName,
            Long currentUserId,
            Long currentTenantId,
            String inputSummary,
            LocalDateTime startTime) {

        if (StringUtils.isBlank(toolExecutionId)) {
            throw new IllegalArgumentException("Tool执行标识不能为空");
        }

        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("Agent请求标识不能为空");
        }

        if (StringUtils.isBlank(agentCode)) {
            throw new IllegalArgumentException("Agent编码不能为空");
        }

        if (StringUtils.isBlank(toolName)) {
            throw new IllegalArgumentException("Tool名称不能为空");
        }

        if (Objects.isNull(currentUserId) || currentUserId <= 0L) {
            throw new IllegalArgumentException("当前用户ID必须大于0");
        }

        if (Objects.isNull(currentTenantId) || currentTenantId <= 0L) {
            throw new IllegalArgumentException("当前租户ID必须大于0");
        }

        if (Objects.isNull(startTime)) {
            throw new IllegalArgumentException("Tool开始时间不能为空");
        }

        // 空白入参摘要恢复为统一说明，保证领域对象和数据库RUNNING记录语义一致。
        String resolvedInputSummary = StringUtils.defaultIfBlank(inputSummary, EMPTY_INPUT_SUMMARY);

        /*
         * 恢复时保持RUNNING状态，不生成新的startTime。
         * 如果错误地使用LocalDateTime.now()，最终costMillis只会计算终态更新阶段，
         * 无法代表真实Tool业务执行耗时。
         */
        return new ToolExecution(
                null,
                StringUtils.trim(toolExecutionId),
                StringUtils.trim(requestId),
                StringUtils.trim(agentCode),
                StringUtils.trim(toolName),
                currentUserId,
                currentTenantId,
                resolvedInputSummary,
                null,
                ToolExecutionStatusEnum.RUNNING,
                null,
                null,
                startTime,
                null,
                null);
    }

    /**
     * 将Tool执行状态推进为SUCCEEDED。
     *
     * <p>只有RUNNING状态允许成功完成。
     * 重复调用succeed()或者已经FAILED后再次成功都属于非法状态流转。</p>
     *
     * @param outputSummary Tool成功输出安全摘要
     */
    public void succeed(String outputSummary) {
        // 状态推进前先确认当前审计任务仍然处于RUNNING。
        validateRunningStatus();

        // 空白成功摘要统一替换为固定说明，避免SUCCEEDED记录缺少结果语义。
        this.outputSummary = StringUtils.defaultIfBlank(outputSummary, EMPTY_OUTPUT_SUMMARY);

        // 成功状态不应残留失败错误码和错误信息。
        this.errorCode = null;
        this.errorMessage = null;

        // 记录Tool真实完成时间，并以开始时间和完成时间计算执行耗时。
        this.finishTime = LocalDateTime.now();
        this.costMillis = calculateCostMillis(this.finishTime);

        // 所有成功结果字段准备完成后，最后推进领域状态。
        this.executionStatus = ToolExecutionStatusEnum.SUCCEEDED;
    }

    /**
     * 将Tool执行状态推进为FAILED。
     *
     * <p>只有RUNNING状态允许失败完成。
     * errorMessage必须是可以安全落库和展示给研发人员的错误摘要，
     * 禁止直接传入完整异常堆栈。</p>
     *
     * @param errorCode Tool执行失败错误码
     * @param errorMessage Tool执行失败安全错误信息
     */
    public void fail(Integer errorCode, String errorMessage) {
        // 状态推进前先确认当前审计任务仍然处于RUNNING。
        validateRunningStatus();

        if (Objects.isNull(errorCode) || errorCode <= 0) {
            throw new IllegalArgumentException("Tool执行失败错误码必须大于0");
        }

        // 失败状态不应保留成功输出摘要，避免一条记录同时表达成功和失败。
        this.outputSummary = null;

        // 保存明确错误码；空白错误信息使用统一安全提示兜底。
        this.errorCode = errorCode;
        this.errorMessage = StringUtils.defaultIfBlank(errorMessage, DEFAULT_ERROR_MESSAGE);

        // 记录Tool失败时间并计算从开始到失败的真实耗时。
        this.finishTime = LocalDateTime.now();
        this.costMillis = calculateCostMillis(this.finishTime);

        // 所有失败字段准备完成后，最后推进领域状态。
        this.executionStatus = ToolExecutionStatusEnum.FAILED;
    }

    /**
     * 校验Tool执行记录是否仍然允许进入终态。
     */
    private void validateRunningStatus() {
        // 只有RUNNING状态可以进入SUCCEEDED或者FAILED，终态不能重复变更。
        if (!Objects.equals(ToolExecutionStatusEnum.RUNNING, executionStatus)) {
            throw new IllegalStateException("Tool执行状态不允许继续变更，toolExecutionId=%s，currentStatus=%s".formatted(toolExecutionId, executionStatus));
        }
    }

    /**
     * 计算Tool执行耗时。
     *
     * @param resolvedFinishTime Tool完成或者失败时间
     * @return 非负执行耗时，单位为毫秒
     */
    private Long calculateCostMillis(LocalDateTime resolvedFinishTime) {
        /*
         * Duration属于JDK 8 java.time时间体系，用于计算两个时间点之间的时长。
         * 相比手动转换时间戳相减，Duration能够更明确地表达“开始到结束之间的持续时间”。
         */
        long resolvedCostMillis = Duration.between(startTime, resolvedFinishTime).toMillis();

        // 系统时钟发生极端回拨时不能把负数耗时写入数据库，因此最小按0毫秒记录。
        return Math.max(resolvedCostMillis, 0L);
    }
}
