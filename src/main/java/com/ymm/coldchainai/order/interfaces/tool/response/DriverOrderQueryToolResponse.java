package com.ymm.coldchainai.order.interfaces.tool.response;

import com.ymm.coldchainai.order.application.enumtype.OrderErrorCodeEnum;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 司机成交订单查询Tool返回结果。
 *
 * <p>该对象是Tool与模型之间的结构化协议。模型根据success、hasDealOrder、orderCount和orderList生成最终自然语言答案。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * “没有订单”属于正常成功结果，不应与系统异常混为一谈。Tool返回字段和含义调整前，需要确认模型回答策略及后续兼容方式。</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DriverOrderQueryToolResponse {

    /**
     * Tool业务执行是否成功。
     */
    private final Boolean success;

    /**
     * 本次查询的司机ID。
     */
    private final Long driverId;

    /**
     * 本次实际查询日期，格式为yyyy-MM-dd。
     */
    private final String queryDate;

    /**
     * 是否查询到成交订单。
     */
    private final Boolean hasDealOrder;

    /**
     * 查询到的订单数量。
     */
    private final Integer orderCount;

    /**
     * 成交订单列表。
     */
    private final List<DriverOrderToolItem> orderList;

    /**
     * Tool业务失败错误编码。
     */
    private final Integer errorCode;

    /**
     * Tool业务失败提示。
     */
    private final String errorMessage;

    /**
     * 创建Tool成功结果。
     *
     * ListUtils.emptyIfNull(orderList)是为了返回一个空列表[]而不是null，如果返回null，模型不知道是系统错误还是没有订单
     * 只返回一个[]模型会很好理解：司机这段时间没有成交单
     * List.copyOf()返回一个静态列表，后续不允许任何人篡改查询结果，防止数据不一致问题
     *
     * @param driverId 查询司机ID
     * @param queryDate 查询日期
     * @param orderList 订单列表
     * @return Tool成功结果
     */
    public static DriverOrderQueryToolResponse success(Long driverId, String queryDate, List<DriverOrderToolItem> orderList) {
        // emptyIfNull保证Tool结果中的orderList永远不是null，降低模型理解结构的复杂度。
        List<DriverOrderToolItem> safeOrderList = List.copyOf(ListUtils.emptyIfNull(orderList));

        return new DriverOrderQueryToolResponse(true, driverId, queryDate, !safeOrderList.isEmpty(),
                safeOrderList.size(), safeOrderList, null, null);
    }

    /**
     * 创建Tool业务失败结果。
     *
     * @param driverId 查询司机ID
     * @param queryDate 原始查询日期
     * @param errorCode 业务错误编码
     * @param errorMessage 业务错误提示
     * @return Tool失败结果
     */
    public static DriverOrderQueryToolResponse fail(Long driverId, String queryDate, Integer errorCode, String errorMessage) {
        Integer safeErrorCode = Objects.isNull(errorCode) ? OrderErrorCodeEnum.DRIVER_ORDER_QUERY_PARAMETER_ERROR.getCode() : errorCode;
        String safeErrorMessage = StringUtils.defaultIfBlank(errorMessage, "司机成交订单查询参数错误");

        return new DriverOrderQueryToolResponse(false, driverId, queryDate, false,
                0, List.of(), safeErrorCode, safeErrorMessage);
    }
}
