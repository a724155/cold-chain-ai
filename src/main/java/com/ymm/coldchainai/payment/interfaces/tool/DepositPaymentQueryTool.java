package com.ymm.coldchainai.payment.interfaces.tool;

import com.ymm.coldchainai.agent.audit.application.command.StartToolExecutionAuditCommand;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionAuditDTO;
import com.ymm.coldchainai.agent.audit.application.service.IToolExecutionAuditApplicationService;
import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import com.ymm.coldchainai.agent.core.infrastructure.tool.AgentToolContextKeys;
import com.ymm.coldchainai.payment.application.query.IOrderDepositQueryService;
import com.ymm.coldchainai.payment.application.query.dto.OrderDepositQueryResultDTO;
import com.ymm.coldchainai.payment.application.query.model.OrderDepositQuery;
import com.ymm.coldchainai.payment.interfaces.tool.response.DepositPaymentQueryToolResponse;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;

/**
 * 订单定金支付查询Tool。
 *
 * <p>该Tool是模型进入支付查询业务的适配入口，只负责解析模型参数、
 * 读取受信任ToolContext、创建Application查询对象并转换Tool返回结果。</p>
 *
 * <p>该Tool只能调用IOrderDepositQueryService，禁止直接调用支付Mapper或拼接SQL。
 * 多次支付选择、支付状态和超时判断分别由Application、Repository和Domain负责。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * 开发前必须与产品确认“最新支付单”的选择规则、支付中与超时的展示方式、
 * 未创建支付单的业务含义以及允许模型展示的支付失败原因。</p>
 *
 * <p>在挖矿流程中，该Tool相当于智能挖掘机调用的专业财务查询设备：
 * 模型只提供业务订单号，设备必须使用项目经理下发的真实租户许可证查询财务账本。
 * 如果允许模型传tenantId，就可能查询到其他租户的支付数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DepositPaymentQueryTool {

    /**
     * 订单定金支付查询Tool稳定名称。
     *
     * <p>该常量同时用于Spring AI Tool注册和Tool审计，
     * 避免两个位置分别手写字符串后出现名称不一致。</p>
     */
    private static final String TOOL_NAME = "query_order_deposit_payment";

    /**
     * 冷运支付业务默认时区。
     */
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * ToolContext缺少安全信息时使用的系统异常说明。
     */
    private static final String TOOL_CONTEXT_ERROR_MESSAGE = "订单定金支付Tool缺少受信任调用上下文";

    /**
     * 订单定金支付查询Application Service。
     */
    private final IOrderDepositQueryService orderDepositQueryService;

    /**
     * Tool执行审计Application Service。
     *
     * <p>负责通过独立短事务登记RUNNING、SUCCEEDED和FAILED状态。</p>
     *
     * <p>在挖矿流程中，该服务相当于财务查询设备旁边的审计登记员，负责登记设备何时出发、是否成功以及执行耗时。</p>
     */
    private final IToolExecutionAuditApplicationService toolExecutionAuditApplicationService;

    /**
     * 查询一张冷运业务订单最新定金支付状态。
     *
     * <p>用户询问某订单是否支付定金、定金是否到账、当前是否支付中、
     * 支付是否超时或者失败时调用本工具。</p>
     *
     * <p>执行顺序：</p>
     *
     * <p>1. 从受信任ToolContext读取用户、租户、requestId和agentCode；</p>
     * <p>2. 在独立短事务中保存RUNNING审计记录；</p>
     * <p>3. 执行真实支付查询；</p>
     * <p>4. 根据结果更新SUCCEEDED或者FAILED；</p>
     * <p>5. 审计终态保存完成后返回结构化Tool结果。</p>
     *
     * @param orderNo 待查询冷运业务订单号
     * @param toolContext 后端注入的受信任Tool上下文，不由模型生成
     * @return 结构化定金支付查询结果
     */
    @Tool(
            name = "query_order_deposit_payment",
            description = """
                    查询一张冷运业务订单最新的定金支付状态。
                    当用户询问某订单是否已经支付定金、定金是否到账、是否支付中、
                    是否超时、失败或尚未创建支付单时，必须调用本工具。
                    orderNo是冷运业务订单号，例如CC-AI-DEMO-0001。
                    本工具会自动使用后端当前登录租户和系统时间，禁止猜测或要求用户提供tenantId、currentUserId或queryTime。
                    """
    )
    public DepositPaymentQueryToolResponse queryOrderDepositPayment(
            @ToolParam(description = "待查询冷运业务订单号，不能为空") String orderNo, ToolContext toolContext) {

        // ToolContext相当于项目经理随任务单下发的身份许可证。租户、用户和requestId从这里读取，不会成为模型可以自由填写的Tool参数。
        Map<String, Object> toolContextMap = resolveToolContextMap(toolContext);

        // 从后端受信任上下文读取当前租户，禁止使用模型生成的tenantId查询支付数据。
        Long currentTenantId = resolveRequiredLong(toolContextMap, AgentToolContextKeys.CURRENT_TENANT_ID, "当前租户ID");

        // 当前用户ID用于记录是谁发起了本次支付数据查询。
        Long currentUserId = resolveRequiredLong(toolContextMap, AgentToolContextKeys.CURRENT_USER_ID, "当前用户ID");

        // requestId用于把Tool审计记录与本轮AgentExecution关联起来。
        String requestId = resolveRequiredString(toolContextMap, AgentToolContextKeys.REQUEST_ID, "requestId");

        // agentCode用于记录哪一个Agent决定调用本Tool。
        String agentCode = resolveRequiredString(toolContextMap, AgentToolContextKeys.AGENT_CODE, "agentCode");

        // 支付审计输入摘要只保存业务订单号，不保存完整支付请求或者其他敏感数据。
        String inputSummary = buildInputSummary(orderNo);

        // 严格审计模式下，必须先成功写入RUNNING记录才能执行支付查询。startExecution()使用REQUIRES_NEW独立短事务，方法返回时事务已经提交。
        ToolExecutionAuditDTO auditDTO = toolExecutionAuditApplicationService.startExecution(
                StartToolExecutionAuditCommand.create(requestId, agentCode, TOOL_NAME, currentUserId, currentTenantId, inputSummary));

        try {
            // queryTime必须使用后端当前业务时间，不能由模型提供或修改。
            LocalDateTime queryTime = LocalDateTime.now(BUSINESS_ZONE_ID);

            // tenantId和queryTime来自后端受信任环境，只有orderNo来自模型参数。OrderDepositQuery会再次完成租户、订单号和查询时间的业务校验。
            OrderDepositQuery orderDepositQuery = OrderDepositQuery.create(currentTenantId, orderNo, queryTime);

            log.info("订单定金支付Tool开始，toolExecutionId={}，requestId={}，currentUserId={}，tenantId={}，orderNo={}，queryTime={}",
                    auditDTO.getToolExecutionId(), requestId, currentUserId, currentTenantId, orderDepositQuery.getOrderNo(), queryTime);

            // 调用支付Application Service执行真实查询，此时不存在Tool审计数据库事务。
            OrderDepositQueryResultDTO resultDTO = orderDepositQueryService.queryOrderDeposit(orderDepositQuery);

            // 将支付Application DTO转换成提供给模型的稳定结构化响应。
            DepositPaymentQueryToolResponse response = DepositPaymentQueryToolResponse.success(resultDTO);

            // 支付查询成功后先登记SUCCEEDED。只有终态审计成功写入数据库，支付查询结果才允许返回给模型。
            toolExecutionAuditApplicationService.markSucceeded(auditDTO, buildSuccessOutputSummary(response));

            log.info("订单定金支付Tool完成，toolExecutionId={}，requestId={}，tenantId={}，orderNo={}，payOrderCreated={}，paid={}，paying={}，expired={}",
                    auditDTO.getToolExecutionId(),requestId, currentTenantId, orderDepositQuery.getOrderNo(), resultDTO.getPayOrderCreated(),
                    resultDTO.getPaid(), resultDTO.getPaying(), resultDTO.getExpired());

            return DepositPaymentQueryToolResponse.success(resultDTO);
        } catch (BusinessException exception) {
            // 业务订单号为空或者支付查询参数不合法属于可预期业务失败。先登记FAILED，再返回结构化失败结果供模型向用户说明。
            toolExecutionAuditApplicationService.markFailed(auditDTO, exception.getCode(), exception.getMessage());
            // 模型生成的订单号不符合业务要求时返回结构化失败结果，让模型可以向用户说明参数问题，而不是编造支付查询结论。
            log.warn("订单定金支付Tool业务失败，toolExecutionId={}，requestId={}，tenantId={}，orderNo={}，code={}，message={}",
                    auditDTO.getToolExecutionId(), requestId, currentTenantId, orderNo, exception.getCode(), exception.getMessage());

            return DepositPaymentQueryToolResponse.fail(orderNo, exception.getCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            // Repository、数据库、框架转换或者审计终态更新异常属于系统故障。尝试把审计记录更新为FAILED，但不能让审计补偿异常覆盖最初异常。
            markUnexpectedExecutionFailed(auditDTO, exception);
            throw exception;
        }
    }

    /**
     * 构建支付查询Tool入参安全摘要。
     *
     * <p>当前只保存业务订单号，不保存tenantId、用户ID、查询时间或者完整支付请求。
     * 用户和租户已经存在独立审计字段中，不需要在摘要中重复保存。</p>
     *
     * @param orderNo 模型传入的业务订单号
     * @return 可安全写入Tool审计表的输入摘要
     */
    private String buildInputSummary(String orderNo) {
        // 空白订单号使用明确文字表示，保证参数校验失败的调用也能够被审计。
        String safeOrderNo = StringUtils.defaultIfBlank(orderNo, "空");

        // String.formatted()是JDK 15新增的字符串实例方法。它等价于String.format("orderNo=%s", safeOrderNo)，但模板和参数关系更紧凑。
        return "orderNo=%s".formatted(safeOrderNo);
    }

    /**
     * 构建支付查询Tool成功输出安全摘要。
     *
     * <p>摘要只保存支付状态布尔结论和状态码，
     * 不保存支付单号、支付金额、失败详情或者完整支付结果。</p>
     *
     * @param response 支付查询Tool成功响应
     * @return 可安全写入Tool审计表的输出摘要
     */
    private String buildSuccessOutputSummary(DepositPaymentQueryToolResponse response) {
        if (Objects.isNull(response)) {
            throw new IllegalArgumentException("订单定金支付Tool响应不能为空");
        }
        // 只抽取状态结论，避免审计表再次复制完整支付业务数据。
        return "success=%s，payOrderCreated=%s，payStatus=%s，paid=%s，paying=%s，expired=%s".formatted(response.getSuccess(),
                response.getPayOrderCreated(), response.getPayStatus(), response.getPaid(), response.getPaying(), response.getExpired());
    }

    /**
     * 处理支付Tool执行过程中的非预期系统异常。
     *
     * <p>如果FAILED审计更新本身也失败，
     * 使用suppressed异常同时保留原始Tool异常和审计异常。</p>
     *
     * @param auditDTO 当前Tool审计凭证
     * @param originalException 最初导致Tool失败的系统异常
     */
    private void markUnexpectedExecutionFailed(ToolExecutionAuditDTO auditDTO, RuntimeException originalException) {
        try {
            // 系统异常只保存统一安全提示，禁止把SQL、连接地址或者堆栈信息写入审计表。
            toolExecutionAuditApplicationService.markFailed(auditDTO, AgentErrorCodeEnum.AGENT_EXECUTION_ERROR.getCode(),
                    AgentErrorCodeEnum.AGENT_EXECUTION_ERROR.getMessage());
        } catch (RuntimeException auditException) {
            // 原始异常仍然作为主要异常，审计失败作为附加异常供日志和排查工具读取。
            originalException.addSuppressed(auditException);
            log.error("订单定金支付Tool失败审计更新异常，toolExecutionId={}，requestId={}", auditDTO.getToolExecutionId(), auditDTO.getRequestId(), auditException);
        }
    }

    /**
     * 安全获取ToolContext中的上下文Map。
     *
     * @param toolContext Spring AI Tool执行上下文
     * @return 非空Tool上下文Map
     */
    private Map<String, Object> resolveToolContextMap(ToolContext toolContext) {
        Map<String, Object> toolContextMap = Objects.isNull(toolContext) ? null : toolContext.getContext();

        if (MapUtils.isEmpty(toolContextMap)) {
            throw createToolContextException("ToolContext为空");
        }

        return toolContextMap;
    }

    /**
     * 从ToolContext中读取必填Long字段。
     *
     * @param toolContextMap Tool上下文Map
     * @param contextKey 上下文字段名称
     * @param fieldName 异常信息使用的字段说明
     * @return 大于0的Long值
     */
    private Long resolveRequiredLong(Map<String, Object> toolContextMap, String contextKey, String fieldName) {
        Long contextValue = MapUtils.getLong(toolContextMap, contextKey);

        if (Objects.isNull(contextValue) || contextValue <= 0L) {
            throw createToolContextException("%s不能为空且必须大于0".formatted(fieldName));
        }

        return contextValue;
    }

    /**
     * 从ToolContext中读取必填字符串字段。
     *
     * @param toolContextMap Tool上下文Map
     * @param contextKey 上下文字段名称
     * @param fieldName 异常信息使用的字段说明
     * @return 非空字符串值
     */
    private String resolveRequiredString(Map<String, Object> toolContextMap, String contextKey, String fieldName) {
        String contextValue = MapUtils.getString(toolContextMap, contextKey);

        if (StringUtils.isBlank(contextValue)) {
            throw createToolContextException("%s不能为空".formatted(fieldName));
        }

        return contextValue;
    }

    /**
     * 创建ToolContext系统异常。
     *
     * @param detailMessage 具体上下文错误说明
     * @return ToolContext系统异常
     */
    private IllegalStateException createToolContextException(String detailMessage) {
        String errorMessage = "%s：%s，%s".formatted(AgentErrorCodeEnum.AGENT_EXECUTION_ERROR.getMessage(), TOOL_CONTEXT_ERROR_MESSAGE, detailMessage);
        return new IllegalStateException(errorMessage);
    }
}
