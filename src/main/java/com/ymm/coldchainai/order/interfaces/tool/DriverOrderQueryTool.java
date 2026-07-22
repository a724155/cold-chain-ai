package com.ymm.coldchainai.order.interfaces.tool;

import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import com.ymm.coldchainai.agent.core.infrastructure.tool.AgentToolContextKeys;
import com.ymm.coldchainai.order.application.enumtype.OrderErrorCodeEnum;
import com.ymm.coldchainai.order.application.query.IDriverOrderQueryService;
import com.ymm.coldchainai.order.application.query.dto.DriverOrderSummaryDTO;
import com.ymm.coldchainai.order.application.query.model.DriverOrderQuery;
import com.ymm.coldchainai.order.interfaces.tool.response.DriverOrderQueryToolResponse;
import com.ymm.coldchainai.order.interfaces.tool.response.DriverOrderToolItem;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 司机成交订单查询Tool。
 *
 * <p>该Tool是模型进入订单业务系统的适配入口，只负责解析模型参数、读取受信任ToolContext、构建Application查询对象并转换Tool返回结果。</p>
 *
 * <p>该Tool只能调用IDriverOrderQueryService，禁止直接调用Mapper或拼接SQL。
 * 订单业务规则、租户隔离和数据恢复分别由Application、Repository和Domain负责。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * “成交订单”的定义、取消订单是否返回、默认查询日期和最大返回数量，
 * 必须根据PRD确认。Tool描述只是帮助模型正确调用，不能替代正式业务需求。</p>
 *
 * <p>在挖矿流程中，该Tool相当于智能挖掘机调用的一辆专业档案查询车：
 * 挖掘机只提交司机和日期，查询车必须使用项目经理下发的真实租户许可证访问账本。
 * 如果Tool直接相信模型提供的tenantId，就可能开进其他公司的矿区查询数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DriverOrderQueryTool {

    /**
     * 冷运业务默认时区。
     *
     * <p>用户表达“今天”时，当前暂定按照中国标准时间计算。
     * 正式上线前需要结合产品覆盖地区确认时区规则。</p>
     */
    private static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * ToolContext缺少安全信息时使用的系统异常说明。
     */
    private static final String TOOL_CONTEXT_ERROR_MESSAGE = "司机订单Tool缺少受信任调用上下文";

    /**
     * 司机成交订单查询Application Service。
     */
    private final IDriverOrderQueryService driverOrderQueryService;

    /**
     * 查询指定司机在某一天发生过成交的冷运订单。
     *
     * <p>用户询问“司机今天有没有成交单”“某司机某天成交了哪些订单”时调用。
     * queryDate为空表示查询中国标准时间下的今天。</p>
     *
     * @param driverId 待查询司机ID
     * @param queryDate 查询日期，格式yyyy-MM-dd，可以为空
     * @param maxResultCount 最大返回数量，可以为空
     * @param toolContext 后端注入的受信任Tool上下文，不由模型生成
     * @return 结构化订单查询结果
     */
    @Tool(
            name = "query_driver_deal_orders",
            description = """
                    查询指定司机在某一天发生过成交的冷运订单。
                    当用户询问某个司机今天、某天是否有成交单，或者要求列出成交订单时必须调用本工具，禁止自行猜测司机的成交单。
                    driverId是司机ID。
                    queryDate格式为yyyy-MM-dd；用户说“今天”时可以不传queryDate。
                    maxResultCount范围为1到100，可以不传，不传时默认20。
                    本工具会自动使用后端当前登录租户，禁止猜测或要求用户提供tenantId。
                    """
    )
    public DriverOrderQueryToolResponse queryDriverDealOrders(
            @ToolParam(description = "待查询司机ID，必须是大于0的整数") Long driverId,
            @ToolParam(description = "查询日期，格式为yyyy-MM-dd；用户说今天时可以省略", required = false) String queryDate,
            @ToolParam(description = "最大返回订单数量，范围1到100；省略时默认20", required = false) Integer maxResultCount,
            ToolContext toolContext) {

        // ToolContext相当于项目经理随任务单下发的安全许可证。tenantId和currentUserId从这里读取，不会出现在模型可填写的Tool JSON参数中。
        Map<String, Object> toolContextMap = resolveToolContextMap(toolContext);

        Long currentTenantId = resolveRequiredLong(toolContextMap, AgentToolContextKeys.CURRENT_TENANT_ID, "当前租户ID");
        Long currentUserId = resolveRequiredLong(toolContextMap, AgentToolContextKeys.CURRENT_USER_ID, "当前用户ID");
        String requestId = resolveRequiredString(toolContextMap, AgentToolContextKeys.REQUEST_ID, "requestId");

        try {
            // queryDate为空时按照冷运业务时区计算今天，避免依赖模型自行猜测当前日期。
            LocalDate resolvedQueryDate = resolveQueryDate(queryDate);

            // tenantId来自受信任ToolContext，driverId和日期来自模型Tool参数。DriverOrderQuery会再次校验所有业务参数和最大返回数量。
            DriverOrderQuery driverOrderQuery = DriverOrderQuery.create(currentTenantId, driverId, resolvedQueryDate, maxResultCount);

            log.info("司机成交订单Tool开始，requestId={}，currentUserId={}，tenantId={}，driverId={}，queryDate={}",
                    requestId, currentUserId, currentTenantId, driverId, resolvedQueryDate);

            List<DriverOrderSummaryDTO> driverOrderSummaryDTOList = driverOrderQueryService.queryDriverDealOrderList(driverOrderQuery);

            List<DriverOrderToolItem> driverOrderToolItemList = convertToToolItemList(driverOrderSummaryDTOList);

            log.info("司机成交订单Tool完成，requestId={}，tenantId={}，driverId={}，queryDate={}，orderCount={}",
                    requestId, currentTenantId, driverId, resolvedQueryDate, driverOrderToolItemList.size());

            return DriverOrderQueryToolResponse.success(driverId, resolvedQueryDate.format(DateTimeFormatter.ISO_LOCAL_DATE), driverOrderToolItemList);
        } catch (BusinessException exception) {

            // 模型生成的driverId、日期或数量不符合业务要求时，返回结构化失败结果，让模型可以向用户解释参数问题，而不是直接中断整个Agent请求。
            log.warn("司机成交订单Tool业务失败，requestId={}，driverId={}，queryDate={}，code={}，message={}",
                    requestId, driverId, queryDate, exception.getCode(), exception.getMessage());

            return DriverOrderQueryToolResponse.fail(driverId, queryDate, exception.getCode(), exception.getMessage());
        } catch (DateTimeException exception) {
            // 日期格式错误属于模型Tool参数错误，返回安全提示供模型重新组织答案。
            String errorMessage = "查询日期格式错误，请使用yyyy-MM-dd";

            log.warn("司机成交订单Tool日期解析失败，requestId={}，driverId={}，queryDate={}", requestId, driverId, queryDate);

            return DriverOrderQueryToolResponse.fail(driverId, queryDate, OrderErrorCodeEnum.DRIVER_ORDER_QUERY_PARAMETER_ERROR.getCode(), errorMessage);
        }
    }

    /**
     * 安全获取ToolContext中的上下文Map。
     *
     * @param toolContext Spring AI Tool执行上下文
     * @return 非空上下文Map
     */
    private Map<String, Object> resolveToolContextMap(ToolContext toolContext) {
        Map<String, Object> toolContextMap = Objects.isNull(toolContext) ? null : toolContext.getContext();

        // MapUtils.isEmpty同时处理null和空Map，避免重复编写Objects.isNull和isEmpty。
        if (MapUtils.isEmpty(toolContextMap)) {
            throw createToolContextException("ToolContext为空");
        }

        return toolContextMap;
    }

    /**
     * 从ToolContext中读取必填Long类型字段。
     *
     * @param toolContextMap Tool上下文Map
     * @param contextKey 上下文字段名称
     * @param fieldName 日志与异常使用的字段说明
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
     * @param fieldName 日志与异常使用的字段说明
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
     * 解析Tool查询日期。
     *
     * @param queryDate 模型传入的日期字符串，可以为空
     * @return 实际查询日期
     */
    private LocalDate resolveQueryDate(String queryDate) {
        if (StringUtils.isBlank(queryDate)) {
            return LocalDate.now(BUSINESS_ZONE_ID);
        }

        return LocalDate.parse(queryDate, DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /**
     * 将Application订单摘要转换成Tool返回条目。
     *
     * @param driverOrderSummaryDTOList Application订单摘要列表
     * @return Tool订单条目列表
     */
    private List<DriverOrderToolItem> convertToToolItemList(List<DriverOrderSummaryDTO> driverOrderSummaryDTOList) {
        if (CollectionUtils.isEmpty(driverOrderSummaryDTOList)) {
            return List.of();
        }

        return driverOrderSummaryDTOList.stream()
                .filter(Objects::nonNull)
                .map(driverOrderSummaryDTO -> DriverOrderToolItem.of(
                        driverOrderSummaryDTO.getOrderNo(),
                        driverOrderSummaryDTO.getPickupCity(),
                        driverOrderSummaryDTO.getDeliveryCity(),
                        driverOrderSummaryDTO.getDealTime(),
                        driverOrderSummaryDTO.getOrderStatus(),
                        driverOrderSummaryDTO.getOrderStatusDescription()))
                .toList();
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
