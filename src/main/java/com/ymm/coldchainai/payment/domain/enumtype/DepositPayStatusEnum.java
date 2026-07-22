package com.ymm.coldchainai.payment.domain.enumtype;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 冷运定金支付状态枚举。
 *
 * <p>该枚举定义一笔定金支付单当前所处的业务状态。数据库后续保存稳定状态编码，不直接保存容易变化的中文说明。</p>
 *
 * <p>在挖矿流程中，该枚举相当于矿场收款单上的处理状态：待支付表示客户尚未付款，支付中表示支付渠道正在处理，
 * 支付成功表示资金确认到账，失败或关闭表示本次支付尝试已经结束。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * 当前状态只满足定金支付查询示例，不代表生产支付中心的完整状态机。
 * 正式开发前必须与产品、支付中心确认支付中、失败、关闭、退款等状态的准确含义和流转规则。</p>
 */
@Getter
@AllArgsConstructor
public enum DepositPayStatusEnum {

    /**
     * 支付单已经创建，等待用户发起支付。
     */
    WAIT_PAY(10, "待支付", false, false),

    /**
     * 已经向支付渠道发起支付，正在等待同步结果、异步回调或主动查询结果。
     */
    PAYING(20, "支付中", false, false),

    /**
     * 支付渠道已经确认定金支付成功。
     */
    PAID(30, "支付成功", true, true),

    /**
     * 支付渠道明确返回本次支付失败。
     */
    FAILED(40, "支付失败", false, true),

    /**
     * 支付单由于超时、用户取消或业务关闭而终止。
     */
    CLOSED(50, "已关闭", false, true);

    /**
     * 支付状态编码。
     */
    private final Integer code;

    /**
     * 支付状态中文说明。
     */
    private final String description;

    /**
     * 当前状态是否代表资金已经支付成功。
     */
    private final Boolean paid;

    /**
     * 当前状态是否为不可继续流转的最终状态。
     */
    private final Boolean terminal;

    /**
     * 根据数据库状态编码获取定金支付状态。
     *
     * @param code 数据库状态编码
     * @return 对应定金支付状态
     */
    public static DepositPayStatusEnum getByCode(Integer code) {
        if (Objects.isNull(code)) {
            throw new IllegalArgumentException("定金支付状态编码不能为空");
        }

        for (DepositPayStatusEnum payStatus : values()) {
            if (Objects.equals(payStatus.getCode(), code)) {
                return payStatus;
            }
        }

        throw new IllegalArgumentException("无法识别的定金支付状态编码，code=%s".formatted(code));
    }

    /**
     * 判断当前支付单是否仍处于支付渠道处理阶段。
     *
     * @return 当前状态为PAYING时返回true
     */
    public boolean isPaying() {
        return Objects.equals(PAYING, this);
    }
}
