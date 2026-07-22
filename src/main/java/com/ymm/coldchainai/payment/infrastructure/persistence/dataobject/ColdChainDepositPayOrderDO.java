package com.ymm.coldchainai.payment.infrastructure.persistence.dataobject;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 冷运定金支付单数据库对象。
 *
 * <p>该对象只负责承载MyBatis从cold_chain_deposit_pay_order表中查询出的字段，
 * 不作为Controller响应，也不能直接交给Agent或Application层使用。</p>
 *
 * <p>在挖矿流程中，该对象相当于财务仓库中的原始收款登记表。
 * 原始登记表需要经过转换器检查后，才能成为业务层可信任的正式收款单。</p>
 */
@Getter
@Setter
public class ColdChainDepositPayOrderDO {

    /**
     * 数据库支付单主键。
     */
    private Long id;

    /**
     * 支付单所属租户ID。
     */
    private Long tenantId;

    /**
     * 冷运定金支付单号。
     */
    private String payOrderNo;

    /**
     * 对应的冷运业务订单号。
     */
    private String orderNo;

    /**
     * 发起本次定金支付的司机ID。
     */
    private Long driverId;

    /**
     * 应支付定金金额，单位为分。
     */
    private Long depositAmountCent;

    /**
     * 数据库支付状态编码。
     */
    private Integer payStatus;

    /**
     * 支付单失效时间。
     */
    private LocalDateTime payExpireTime;

    /**
     * 支付渠道确认成功时间。
     */
    private LocalDateTime paidTime;

    /**
     * 支付失败或关闭原因。
     */
    private String failureReason;

    /**
     * 支付单创建时间。
     */
    private LocalDateTime createTime;
}
