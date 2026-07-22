package com.ymm.coldchainai.order.interfaces.tool.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 司机成交订单Tool返回条目。
 *
 * <p>该对象只包含模型回答当前订单问题所需的最小字段，不会把数据库DO、租户ID或其他内部字段直接暴露给模型。</p>
 *
 * <p><strong>产品与数据权限提醒：</strong>
 * Tool可以向模型提供哪些订单字段，必须根据PRD、数据权限和安全要求确认。不能为了让回答更丰富，就擅自增加运费、手机号、精确地址等敏感信息。</p>
 *
 * <p>在挖矿流程中，该对象相当于经过筛选的钻石检测报告，只告诉智能设备完成当前回答所需要的信息，而不是交出完整矿场账本。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class DriverOrderToolItem {

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
     * 当前订单状态编码。
     */
    private final Integer orderStatus;

    /**
     * 当前订单状态说明。
     */
    private final String orderStatusDescription;
}
