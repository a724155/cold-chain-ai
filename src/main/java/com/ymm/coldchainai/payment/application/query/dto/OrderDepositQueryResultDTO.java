package com.ymm.coldchainai.payment.application.query.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 订单定金支付查询结果DTO。
 *
 * <p>该对象由支付Application层返回，后续DepositPaymentQueryTool会将其转换成专门提供给模型的Tool Response，不能把支付单DO直接交给模型。</p>
 *
 * <p><strong>产品与协议提醒：</strong>
 * payOrderCreated、paid、paying和expired的含义必须与产品明确约定。金额字段单位固定为分，字段名必须保留Cent后缀，避免前端或模型误认为是元。</p>
 *
 * <p>在挖矿流程中，该DTO相当于项目经理整理后的财务查询摘要：只交付本次任务需要的支付结论，不把支付渠道完整账本交给客户。</p>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderDepositQueryResultDTO {

    /**
     * 当前业务订单是否已经创建过定金支付单。
     */
    private final Boolean payOrderCreated;

    /**
     * 冷运业务订单号。
     */
    private final String orderNo;

    /**
     * 最新一笔定金支付单号。
     */
    private final String payOrderNo;

    /**
     * 定金金额，单位为分。
     */
    private final Long depositAmountCent;

    /**
     * 支付状态编码。
     */
    private final Integer payStatus;

    /**
     * 支付状态中文说明。
     */
    private final String payStatusDescription;

    /**
     * 定金是否已经确认支付成功。
     */
    private final Boolean paid;

    /**
     * 是否处于支付渠道处理中。
     */
    private final Boolean paying;

    /**
     * 支付单是否已经超过失效时间。
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
     * 支付成功时间。
     */
    private final LocalDateTime paidTime;

    /**
     * 支付失败或关闭时可安全展示的原因。
     */
    private final String failureReason;

    /**
     * 创建“尚未创建定金支付单”的正常查询结果。
     *
     * @param orderNo 冷运业务订单号
     * @return 未创建支付单结果
     */
    public static OrderDepositQueryResultDTO notCreated(String orderNo) {
        return new OrderDepositQueryResultDTO(false, orderNo, null, null, null, null, false, false, false, null, null, null, null);
    }

    /**
     * 创建已经查询到定金支付单的结果。
     *
     * @param orderNo 冷运业务订单号
     * @param payOrderNo 定金支付单号
     * @param depositAmountCent 定金金额，单位为分
     * @param payStatus 支付状态编码
     * @param payStatusDescription 支付状态说明
     * @param paid 是否支付成功
     * @param paying 是否支付中
     * @param expired 是否已经超时
     * @param createTime 支付单创建时间
     * @param payExpireTime 支付单失效时间
     * @param paidTime 支付成功时间
     * @param failureReason 失败或关闭原因
     * @return 支付单查询结果
     */
    public static OrderDepositQueryResultDTO created(String orderNo, String payOrderNo, Long depositAmountCent,
                                                     Integer payStatus, String payStatusDescription,
                                                     Boolean paid, Boolean paying, Boolean expired,
                                                     LocalDateTime createTime, LocalDateTime payExpireTime,
                                                     LocalDateTime paidTime, String failureReason) {
        return new OrderDepositQueryResultDTO(true, orderNo, payOrderNo, depositAmountCent, payStatus,
                payStatusDescription, paid, paying, expired, createTime, payExpireTime, paidTime, failureReason);
    }
}
