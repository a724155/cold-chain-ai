package com.ymm.coldchainai.order.domain.enumtype;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 冷运订单状态枚举。
 *
 * <p>该枚举描述订单当前所处的业务状态。
 * 后续数据库只保存稳定的状态编码，不直接保存容易变化的中文说明。</p>
 *
 * <p>在挖矿流程中，订单状态相当于一颗钻石当前所处的业务阶段：
 * 已发现、待确认、运输中、已交付或者已经取消。没有统一状态定义，
 * Application、数据库和Agent就可能对同一张订单产生不同理解。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * 正式开发前必须根据PRD确认完整状态机、允许的流转方向以及各状态的业务含义。
 * 当前状态仅满足司机成交订单查询示例，不代表真实冷运生产系统的完整状态集合。</p>
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    /**
     * 司机已经抢到订单，等待支付定金。
     */
    WAIT_DEPOSIT(10, "待支付定金"),

    /**
     * 订单已经完成成交确认。
     */
    DEAL_CONFIRMED(20, "已成交"),

    /**
     * 订单已经进入运输履约阶段。
     */
    IN_TRANSIT(30, "运输中"),

    /**
     * 订单已经完成履约。
     */
    COMPLETED(40, "已完成"),

    /**
     * 订单已经取消。
     */
    CANCELLED(50, "已取消");

    /**
     * 订单状态编码。
     */
    private final Integer code;

    /**
     * 订单状态中文说明。
     */
    private final String description;

    /**
     * 根据数据库状态编码获取订单状态。
     *
     * @param code 数据库订单状态编码
     * @return 对应订单状态
     */
    public static OrderStatusEnum getByCode(Integer code) {
        if (Objects.isNull(code)) {
            throw new IllegalArgumentException("订单状态编码不能为空");
        }

        for (OrderStatusEnum orderStatus : values()) {
            if (Objects.equals(orderStatus.getCode(), code)) {
                return orderStatus;
            }
        }

        throw new IllegalArgumentException("无法识别的订单状态编码，code=%s".formatted(code));
    }
}
