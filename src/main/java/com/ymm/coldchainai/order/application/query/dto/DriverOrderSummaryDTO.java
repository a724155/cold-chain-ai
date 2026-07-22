package com.ymm.coldchainai.order.application.query.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 司机成交订单摘要DTO。
 *
 * <p>该对象由订单Application层返回，后续DriverOrderQueryTool会将其转换成专门提供给模型使用的Tool Response，不能把数据库DO直接交给模型。</p>
 *
 * <p><strong>产品与协议提醒：</strong>
 * 当前暂定返回订单号、装卸城市、成交时间和当前状态。正式接入Agent前必须与产品确认模型回答需要哪些字段，并确认是否允许把城市、
 * 货物、价格或其他订单信息提供给当前用户；不能为了让模型回答更丰富而无边界返回数据。</p>
 *
 * <p>在挖矿流程中，该DTO相当于项目经理整理后的钻石交付摘要，只交付本次任务需要的信息，而不是把矿场完整内部账本直接交给客户。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class DriverOrderSummaryDTO {

    /**
     * 对外订单号。
     */
    private final String orderNo;

    /**
     * 装货城市。
     */
    private final String pickupCity;

    /**
     * 卸货城市。
     */
    private final String deliveryCity;

    /**
     * 订单成交时间。
     */
    private final LocalDateTime dealTime;

    /**
     * 当前订单状态编码。com.ymm.coldchainai.order.domain.enumtype.OrderStatusEnum
     */
    private final Integer orderStatus;

    /**
     * 当前订单状态中文说明。
     */
    private final String orderStatusDescription;
}
