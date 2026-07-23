package com.ymm.coldchainai.payment.interfaces.tool.response;

import com.ymm.coldchainai.payment.application.enumtype.PaymentErrorCodeEnum;
import com.ymm.coldchainai.payment.application.query.dto.OrderDepositQueryResultDTO;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单定金支付查询Tool返回结果。
 *
 * <p>该对象是支付Tool与模型之间的结构化协议， 模型根据payOrderCreated、paid、paying和expired等字段生成最终自然语言答案。</p>
 *
 * <p><strong>产品与协议提醒：</strong>
 * 开发前应与产品确认未创建支付单、支付中、支付超时和支付失败的用户展示方式，
 * 并与前端明确金额单位、状态编码、时间格式、失败原因及兼容策略。当前金额统一使用分，字段名称必须保留Cent后缀。</p>
 *
 * <p>在挖矿流程中，该对象相当于财务人员交给智能挖掘机的标准收款查询报告。
 * 如果直接把数据库DO交给模型，内部字段和数据库结构就会泄露到Agent调用边界。</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DepositPaymentQueryToolResponse {

    /**
     * Tool业务执行是否成功。
     */
    private final Boolean success;

    /**
     * 本次查询的冷运业务订单号。
     */
    private final String orderNo;

    /**
     * 当前订单是否已经创建过定金支付单。
     */
    private final Boolean payOrderCreated;

    /**
     * 最新一笔定金支付单号。
     */
    private final String payOrderNo;

    /**
     * 应支付定金金额，单位为分。
     */
    private final Long depositAmountCent;

    /**
     * 当前支付状态编码。
     */
    private final Integer payStatus;

    /**
     * 当前支付状态说明。
     */
    private final String payStatusDescription;

    /**
     * 是否已经确认支付成功。
     */
    private final Boolean paid;

    /**
     * 是否仍处于支付处理中。
     */
    private final Boolean paying;

    /**
     * 非最终状态支付单是否已经超过失效时间。
     */
    private final Boolean expired;

    /**
     * 支付单创建时间。
     */
    private final LocalDateTime createTime;

    /**
     * 支付单失效时间。
     */
    private final LocalDateTime payExpireTime;

    /**
     * 支付渠道确认成功时间。
     */
    private final LocalDateTime paidTime;

    /**
     * 支付失败或关闭时允许向模型提供的安全原因。
     */
    private final String failureReason;

    /**
     * Tool业务失败错误编码。
     */
    private final Integer errorCode;

    /**
     * Tool业务失败提示。
     */
    private final String errorMessage;

    /**
     * 根据Application查询结果创建Tool成功响应。
     *
     * @param resultDTO 订单定金支付查询结果
     * @return Tool成功响应
     */
    public static DepositPaymentQueryToolResponse success(OrderDepositQueryResultDTO resultDTO) {
        if (Objects.isNull(resultDTO)) {
            throw new IllegalArgumentException("订单定金支付查询结果不能为空");
        }

        /*
         * 未创建支付单也是正常成功结果。
         * 在挖矿流程中，相当于财务账本确认没有生成过对应收款单，而不是财务系统查询失败。
         */
        return new DepositPaymentQueryToolResponse(
                true,
                resultDTO.getOrderNo(),
                resultDTO.getPayOrderCreated(),
                resultDTO.getPayOrderNo(),
                resultDTO.getDepositAmountCent(),
                resultDTO.getPayStatus(),
                resultDTO.getPayStatusDescription(),
                resultDTO.getPaid(),
                resultDTO.getPaying(),
                resultDTO.getExpired(),
                resultDTO.getCreateTime(),
                resultDTO.getPayExpireTime(),
                resultDTO.getPaidTime(),
                resultDTO.getFailureReason(),
                null,
                null);
    }

    /**
     * 创建Tool业务失败响应。
     *
     * @param orderNo 原始业务订单号
     * @param errorCode 支付业务错误编码
     * @param errorMessage 支付业务错误提示
     * @return Tool业务失败响应
     */
    public static DepositPaymentQueryToolResponse fail(String orderNo, Integer errorCode, String errorMessage) {
        // 已经定义支付错误码枚举，兜底时禁止重新写42000等魔法数字。
        Integer safeErrorCode = Objects.isNull(errorCode) ? PaymentErrorCodeEnum.ORDER_DEPOSIT_QUERY_PARAMETER_ERROR.getCode() : errorCode;
        String safeErrorMessage = StringUtils.defaultIfBlank(errorMessage, PaymentErrorCodeEnum.ORDER_DEPOSIT_QUERY_PARAMETER_ERROR.getMessage());

        return new DepositPaymentQueryToolResponse(
                false,
                orderNo,
                false,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                null,
                null,
                null,
                null,
                safeErrorCode,
                safeErrorMessage);
    }
}
