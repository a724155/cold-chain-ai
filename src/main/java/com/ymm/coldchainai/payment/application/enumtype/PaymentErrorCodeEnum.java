package com.ymm.coldchainai.payment.application.enumtype;

import com.ymm.coldchainai.shared.exception.code.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 冷运定金支付模块错误码枚举。
 *
 * <p>该枚举统一管理定金支付查询参数错误和支付单数据错误，
 * 避免错误编码散落在Application Service、Repository和后续Tool中。</p>
 */
@Getter
@AllArgsConstructor
public enum PaymentErrorCodeEnum implements IErrorCode {

    /**
     * 订单定金支付查询参数不符合业务要求。
     */
    ORDER_DEPOSIT_QUERY_PARAMETER_ERROR(42000, "订单定金支付查询参数错误"),

    /**
     * 数据库中的定金支付单缺少必要字段或状态编码不合法。
     */
    DEPOSIT_PAY_ORDER_DATA_ERROR(52000, "定金支付单数据异常");

    /**
     * 支付模块错误编码。
     */
    private final Integer code;

    /**
     * 支付模块默认错误提示。
     */
    private final String message;
}
