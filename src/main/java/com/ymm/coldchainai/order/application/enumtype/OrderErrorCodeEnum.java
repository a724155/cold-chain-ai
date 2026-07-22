package com.ymm.coldchainai.order.application.enumtype;

import com.ymm.coldchainai.shared.exception.code.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 冷运订单模块错误码枚举。
 *
 * <p>该枚举只管理订单模块的业务错误和系统错误，
 * 避免订单错误编码散落在查询服务、Repository和后续Tool中。</p>
 */
@Getter
@AllArgsConstructor
public enum OrderErrorCodeEnum implements IErrorCode {

    /**
     * 司机成交订单查询参数不符合业务要求。
     */
    DRIVER_ORDER_QUERY_PARAMETER_ERROR(41000, "司机成交订单查询参数错误"),

    /**
     * 数据库中的订单数据缺少必要字段或状态编码不合法。
     */
    ORDER_DATA_ERROR(51000, "订单数据异常");

    /**
     * 订单模块错误编码。
     */
    private final Integer code;

    /**
     * 订单模块默认错误提示。
     */
    private final String message;
}
