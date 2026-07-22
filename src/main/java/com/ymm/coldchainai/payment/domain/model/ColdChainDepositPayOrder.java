package com.ymm.coldchainai.payment.domain.model;

import com.ymm.coldchainai.payment.domain.enumtype.DepositPayStatusEnum;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 冷运定金支付单领域对象。
 *
 * <p>该对象表达一笔定金支付单的核心业务事实，包括所属租户、业务订单、司机、支付金额、当前状态和支付时效。</p>
 *
 * <p>在挖矿流程中，该对象相当于经过财务规则校验的矿场收款单：数据库DO只是账本中的原始记录，而领域对象负责保证支付单编号、
 * 金额、状态和时间等核心信息可以被业务安全使用。</p>
 *
 * <p><strong>产品与支付中心确认提醒：</strong>
 * 正式开发前必须确认金额单位、支付状态含义、超时规则、多次支付处理方式和允许向模型展示的失败原因。当前金额统一使用“分”，
 * 避免前后端对元、分理解不一致造成资损。</p>
 */
@Getter
public class ColdChainDepositPayOrder {

    /**
     * 数据库支付单主键。
     */
    private final Long id;

    /**
     * 支付单所属租户ID。
     */
    private final Long tenantId;

    /**
     * 定金支付单号。
     */
    private final String payOrderNo;

    /**
     * 对应的冷运业务订单号。
     */
    private final String orderNo;

    /**
     * 发起本次定金支付的司机ID。
     */
    private final Long driverId;

    /**
     * 应支付定金金额，单位为分。
     *
     * <p>字段名称必须包含Cent后缀，避免调用方误认为单位是元。</p>
     */
    private final Long depositAmountCent;

    /**
     * 当前定金支付状态。
     */
    private final DepositPayStatusEnum payStatus;

    /**
     * 支付单创建时间。
     */
    private final LocalDateTime createTime;

    /**
     * 支付单失效时间。
     */
    private final LocalDateTime payExpireTime;

    /**
     * 支付渠道确认成功的时间。
     *
     * <p>只有支付成功状态必须存在该字段。</p>
     */
    private final LocalDateTime paidTime;

    /**
     * 支付失败或关闭时可安全展示的原因。
     */
    private final String failureReason;

    /**
     * 从数据库记录恢复定金支付单领域对象。
     *
     * <p>构造方法保持私有，外部必须通过restore恢复，
     * 避免Mapper或其他代码绕过领域校验创建不合法支付单。</p>
     *
     * @param id 数据库支付单主键
     * @param tenantId 支付单所属租户ID
     * @param payOrderNo 定金支付单号
     * @param orderNo 业务订单号
     * @param driverId 司机ID
     * @param depositAmountCent 定金金额，单位为分
     * @param payStatus 当前支付状态
     * @param createTime 支付单创建时间
     * @param payExpireTime 支付单失效时间
     * @param paidTime 支付成功时间
     * @param failureReason 支付失败或关闭原因
     */
    private ColdChainDepositPayOrder(Long id, Long tenantId, String payOrderNo, String orderNo, Long driverId, Long depositAmountCent,
                                     DepositPayStatusEnum payStatus, LocalDateTime createTime, LocalDateTime payExpireTime,
                                     LocalDateTime paidTime, String failureReason) {
        this.id = id;
        this.tenantId = tenantId;
        this.payOrderNo = payOrderNo;
        this.orderNo = orderNo;
        this.driverId = driverId;
        this.depositAmountCent = depositAmountCent;
        this.payStatus = payStatus;
        this.createTime = createTime;
        this.payExpireTime = payExpireTime;
        this.paidTime = paidTime;
        this.failureReason = failureReason;
    }

    /**
     * 根据持久化数据恢复合法的定金支付单领域对象。
     *
     * @param id 数据库支付单主键
     * @param tenantId 支付单所属租户ID
     * @param payOrderNo 定金支付单号
     * @param orderNo 业务订单号
     * @param driverId 司机ID
     * @param depositAmountCent 定金金额，单位为分
     * @param payStatus 当前支付状态
     * @param createTime 支付单创建时间
     * @param payExpireTime 支付单失效时间
     * @param paidTime 支付成功时间
     * @param failureReason 支付失败或关闭原因
     * @return 合法的定金支付单领域对象
     */
    public static ColdChainDepositPayOrder restore(Long id, Long tenantId, String payOrderNo, String orderNo, Long driverId,
                                                   Long depositAmountCent, DepositPayStatusEnum payStatus,
                                                   LocalDateTime createTime, LocalDateTime payExpireTime,
                                                   LocalDateTime paidTime, String failureReason) {
        if (Objects.isNull(id) || id <= 0L) {
            throw new IllegalArgumentException("定金支付单主键必须大于0");
        }

        if (Objects.isNull(tenantId) || tenantId <= 0L) {
            throw new IllegalArgumentException("定金支付单租户ID必须大于0");
        }

        if (StringUtils.isBlank(payOrderNo)) {
            throw new IllegalArgumentException("定金支付单号不能为空");
        }

        if (StringUtils.isBlank(orderNo)) {
            throw new IllegalArgumentException("定金支付单对应的业务订单号不能为空");
        }

        if (Objects.isNull(driverId) || driverId <= 0L) {
            throw new IllegalArgumentException("定金支付单司机ID必须大于0");
        }

        if (Objects.isNull(depositAmountCent) || depositAmountCent <= 0L) {
            throw new IllegalArgumentException("定金支付金额必须大于0分");
        }

        if (Objects.isNull(payStatus)) {
            throw new IllegalArgumentException("定金支付状态不能为空");
        }

        if (Objects.isNull(createTime)) {
            throw new IllegalArgumentException("定金支付单创建时间不能为空");
        }

        if (Objects.isNull(payExpireTime)) {
            throw new IllegalArgumentException("定金支付单失效时间不能为空");
        }

        if (!createTime.isBefore(payExpireTime)) {
            throw new IllegalArgumentException("定金支付单创建时间必须早于失效时间");
        }

        if (Boolean.TRUE.equals(payStatus.getPaid()) && Objects.isNull(paidTime)) {
            throw new IllegalArgumentException("支付成功状态必须存在支付成功时间");
        }

        return new ColdChainDepositPayOrder(id, tenantId, payOrderNo, orderNo, driverId, depositAmountCent,
                payStatus, createTime, payExpireTime, paidTime, failureReason);
    }

    /**
     * 判断当前定金是否已经确认支付成功。
     *
     * @return 当前状态为支付成功时返回true
     */
    public boolean isPaid() {
        return Boolean.TRUE.equals(payStatus.getPaid());
    }

    /**
     * 判断当前支付单是否处于支付渠道处理阶段。
     *
     * @return 当前状态为PAYING时返回true
     */
    public boolean isPaying() {
        return payStatus.isPaying();
    }

    /**
     * 判断支付单在指定时间是否已经超时。
     *
     * <p>只有非最终状态支付单才需要继续判断超时。
     * PAYING但已经超过payExpireTime，说明数据库状态可能尚未被补偿任务关闭。</p>
     *
     * @param queryTime 本次查询使用的时间基准
     * @return 非最终状态且已经到达失效时间时返回true
     */
    public boolean isExpiredAt(LocalDateTime queryTime) {
        if (Objects.isNull(queryTime) || Boolean.TRUE.equals(payStatus.getTerminal())) {
            return false;
        }

        return !queryTime.isBefore(payExpireTime);
    }
}
