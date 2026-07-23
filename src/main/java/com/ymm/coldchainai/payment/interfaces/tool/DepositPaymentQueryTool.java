package com.ymm.coldchainai.payment.interfaces.tool;

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
     * 查询一张冷运业务订单最新定金支付状态。
     *
     * <p>用户询问某订单是否支付定金、定金是否到账、当前是否支付中、
     * 支付是否超时或失败时调用本工具。</p>
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

        Long currentTenantId = resolveRequiredLong(toolContextMap, AgentToolContextKeys.CURRENT_TENANT_ID, "当前租户ID");
        Long currentUserId = resolveRequiredLong(toolContextMap, AgentToolContextKeys.CURRENT_USER_ID, "当前用户ID");
        String requestId = resolveRequiredString(toolContextMap, AgentToolContextKeys.REQUEST_ID, "requestId");

        try {
            // queryTime必须使用后端当前业务时间，不能由模型提供或修改。
            LocalDateTime queryTime = LocalDateTime.now(BUSINESS_ZONE_ID);

            // tenantId和queryTime来自后端受信任环境，只有orderNo来自模型参数。OrderDepositQuery会再次完成租户、订单号和查询时间的业务校验。
            OrderDepositQuery orderDepositQuery = OrderDepositQuery.create(currentTenantId, orderNo, queryTime);

            log.info("订单定金支付Tool开始，requestId={}，currentUserId={}，tenantId={}，orderNo={}，queryTime={}",
                    requestId, currentUserId, currentTenantId, orderDepositQuery.getOrderNo(), queryTime);

            OrderDepositQueryResultDTO resultDTO = orderDepositQueryService.queryOrderDeposit(orderDepositQuery);

            log.info("订单定金支付Tool完成，requestId={}，tenantId={}，orderNo={}，payOrderCreated={}，paid={}，paying={}，expired={}",
                    requestId, currentTenantId, orderDepositQuery.getOrderNo(), resultDTO.getPayOrderCreated(),
                    resultDTO.getPaid(), resultDTO.getPaying(), resultDTO.getExpired());

            return DepositPaymentQueryToolResponse.success(resultDTO);
        } catch (BusinessException exception) {
            // 模型生成的订单号不符合业务要求时返回结构化失败结果，让模型可以向用户说明参数问题，而不是编造支付查询结论。
            log.warn("订单定金支付Tool业务失败，requestId={}，tenantId={}，orderNo={}，code={}，message={}",
                    requestId, currentTenantId, orderNo, exception.getCode(), exception.getMessage());

            return DepositPaymentQueryToolResponse.fail(orderNo, exception.getCode(), exception.getMessage());
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
