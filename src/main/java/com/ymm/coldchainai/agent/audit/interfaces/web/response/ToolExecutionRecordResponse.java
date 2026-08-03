package com.ymm.coldchainai.agent.audit.interfaces.web.response;

import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionRecordDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tool执行审计接口响应。
 *
 * <p>该对象定义单次Tool执行记录返回给前端或者研发验证接口的数据结构，
 * 只包含安全摘要和执行元数据，不返回完整订单列表、支付单明细、
 * 公司内部规范原文、SQL、异常堆栈或者其他敏感业务数据。</p>
 *
 * <p><strong>前后端协议提醒：</strong>
 * 开发正式审计管理页面前，需要与产品和前端确认字段展示范围、
 * RUNNING状态的实时刷新方式、时间格式、耗时单位、失败信息展示权限，
 * 以及currentUserId和currentTenantId是否允许直接展示。
 * 当前接口仅用于local环境验证，不能直接假设所有字段都适合正式生产页面。</p>
 *
 * <p>在挖矿流程中，该Response相当于研发人员从设备审计档案室拿到的一张查询回执：
 * 它说明哪台设备执行了什么安全摘要、最终状态和耗时，
 * 但不会把设备内部完整业务数据和敏感资料全部复印出来。</p>
 */
@Getter
@AllArgsConstructor
public class ToolExecutionRecordResponse {

    /**
     * 单次Tool执行业务唯一标识。
     *
     * <p>每调用一次Tool都会生成新的toolExecutionId。
     * 即使同一Agent请求连续调用同一个Tool，每次调用也拥有不同标识。</p>
     */
    private final String toolExecutionId;

    /**
     * 当前Tool调用所属Agent请求唯一标识。
     *
     * <p>同一个requestId可以关联零条、一条或者多条Tool执行记录，
     * 用于还原一次Agent问答中完整的Tool调用链路。</p>
     */
    private final String requestId;

    /**
     * 实际决定调用当前Tool的Agent稳定编码。
     *
     * <p>用于区分本次Tool调用来自哪个Agent运行配置，
     * 不能使用可能发生变化的Agent展示名称代替稳定编码。</p>
     */
    private final String agentCode;

    /**
     * Spring AI Tool稳定名称。
     *
     * <p>该名称应与Tool注册时使用的名称一致，
     * 例如query_driver_deal_orders、query_order_deposit_payment或者query_internal_rules。</p>
     */
    private final String toolName;

    /**
     * 发起本次Agent请求的受信任用户ID。
     *
     * <p>该值来自后端认证上下文，不来自前端请求或者模型参数。
     * 正式生产管理页面是否展示该字段，需要根据审计权限要求确认。</p>
     */
    private final Long currentUserId;

    /**
     * 发起本次Agent请求的受信任租户ID。
     *
     * <p>该字段用于完成租户数据隔离。
     * 查询Tool审计记录时必须同时校验currentUserId和currentTenantId，
     * 不能只依赖requestId。</p>
     */
    private final Long currentTenantId;

    /**
     * Tool输入参数的安全摘要。
     *
     * <p>该字段只保存经过筛选的最小必要信息，
     * 例如司机ID、业务订单号或者问题长度，不保存完整敏感请求报文。</p>
     */
    private final String inputSummary;

    /**
     * Tool成功输出的安全摘要。
     *
     * <p>RUNNING和FAILED状态下该字段通常为空；
     * SUCCEEDED状态下保存订单数量、支付状态或者RAG召回数量等安全统计信息，
     * 不保存完整Tool返回结果。</p>
     */
    private final String outputSummary;

    /**
     * Tool执行状态数据库编码。
     *
     * <p>当前状态约定：</p>
     *
     * <p>10：执行中；</p>
     * <p>20：执行成功；</p>
     * <p>30：执行失败。</p>
     *
     * <p>前端不能只根据数字自行猜测含义，应同时参考executionStatusMessage。</p>
     */
    private final Integer executionStatusCode;

    /**
     * Tool执行状态中文说明。
     *
     * <p>该值由ToolExecutionStatusEnum统一生成，
     * 例如“执行中”“执行成功”或者“执行失败”。</p>
     */
    private final String executionStatusMessage;

    /**
     * Tool执行失败错误码。
     *
     * <p>只有FAILED状态通常存在该字段；
     * RUNNING和SUCCEEDED状态应为空。</p>
     */
    private final Integer errorCode;

    /**
     * Tool执行失败安全错误信息。
     *
     * <p>只有FAILED状态通常存在该字段。
     * 该信息只能包含经过治理的安全提示，不能包含SQL、数据库连接信息、
     * 内部异常堆栈或者完整敏感业务数据。</p>
     */
    private final String errorMessage;

    /**
     * Tool开始执行时间。
     *
     * <p>该时间表示RUNNING审计记录创建时的业务开始时间，
     * 不是数据库create_time字段。</p>
     */
    private final LocalDateTime startTime;

    /**
     * Tool执行成功或者失败的完成时间。
     *
     * <p>RUNNING状态下该字段为空；
     * SUCCEEDED和FAILED终态下该字段必须存在。</p>
     */
    private final LocalDateTime finishTime;

    /**
     * Tool完整执行耗时，单位为毫秒。
     *
     * <p>该耗时从startTime计算到finishTime，
     * 包含真实Tool业务执行过程，但不包含Tool完成后外层模型继续生成答案的时间。</p>
     *
     * <p>RUNNING状态下该字段为空，终态下应大于等于0。</p>
     */
    private final Long costMillis;

    /**
     * 将Application层Tool执行审计DTO转换成HTTP响应。
     *
     * <p>该方法只执行接口字段映射，不重新计算耗时，
     * 也不重新解释或者修改Tool执行状态。</p>
     *
     * @param recordDTO Tool执行审计Application DTO
     * @return 可返回给前端或者Postman的Tool执行审计响应
     */
    public static ToolExecutionRecordResponse fromDTO(ToolExecutionRecordDTO recordDTO) {
        if (Objects.isNull(recordDTO)) {
            throw new IllegalArgumentException("Tool执行审计DTO不能为空");
        }

        // 映射Tool、Agent和本次Agent请求的业务关联标识。
        String toolExecutionId = recordDTO.getToolExecutionId();
        String requestId = recordDTO.getRequestId();
        String agentCode = recordDTO.getAgentCode();
        String toolName = recordDTO.getToolName();

        // 映射来自后端认证上下文的用户和租户审计身份。
        Long currentUserId = recordDTO.getCurrentUserId();
        Long currentTenantId = recordDTO.getCurrentTenantId();

        // 映射已经完成安全治理的Tool输入和输出摘要。
        String inputSummary = recordDTO.getInputSummary();
        String outputSummary = recordDTO.getOutputSummary();

        // 映射执行状态编码、状态说明以及失败信息。
        Integer executionStatusCode = recordDTO.getExecutionStatusCode();
        String executionStatusMessage = recordDTO.getExecutionStatusMessage();
        Integer errorCode = recordDTO.getErrorCode();
        String errorMessage = recordDTO.getErrorMessage();

        // 映射Tool开始时间、完成时间和完整执行耗时。
        LocalDateTime startTime = recordDTO.getStartTime();
        LocalDateTime finishTime = recordDTO.getFinishTime();
        Long costMillis = recordDTO.getCostMillis();

        // 创建只负责HTTP协议输出的Response，不让Application DTO直接暴露到Interfaces层之外。
        return new ToolExecutionRecordResponse(
                toolExecutionId,
                requestId,
                agentCode,
                toolName,
                currentUserId,
                currentTenantId,
                inputSummary,
                outputSummary,
                executionStatusCode,
                executionStatusMessage,
                errorCode,
                errorMessage,
                startTime,
                finishTime,
                costMillis);
    }
}