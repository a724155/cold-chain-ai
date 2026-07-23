package com.ymm.coldchainai.payment.domain.model;

import com.ymm.coldchainai.payment.domain.enumtype.DepositPayStatusEnum;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ColdChainDepositPayOrder领域对象单元测试。
 *
 * <p>该测试不启动Spring容器和数据库，
 * 只验证支付成功、支付中和支付超时等核心领域规则。</p>
 */
class ColdChainDepositPayOrderTest {

    /**
     * 测试支付中的支付单超过失效时间后会被识别为超时。
     */
    @Test
    void shouldIdentifyExpiredPayingOrder() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 22, 10, 0);
        LocalDateTime payExpireTime = createTime.plusMinutes(10);

        ColdChainDepositPayOrder depositPayOrder = ColdChainDepositPayOrder.restore(
                1L, 1001L, "PAY-AI-DEMO-0001", "CC-AI-DEMO-0001", 12369L,
                1300L, DepositPayStatusEnum.PAYING, createTime, payExpireTime, null, null);

        assertTrue(depositPayOrder.isPaying());
        assertTrue(depositPayOrder.isExpiredAt(createTime.plusMinutes(11)));
        assertFalse(depositPayOrder.isPaid());
    }

    /**
     * 测试支付中的支付单未到失效时间时不会被识别为超时。
     */
    @Test
    void shouldNotIdentifyPayingOrderAsExpiredBeforeExpireTime() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 22, 10, 0);
        LocalDateTime payExpireTime = createTime.plusMinutes(10);

        ColdChainDepositPayOrder depositPayOrder = ColdChainDepositPayOrder.restore(
                1L, 1001L, "PAY-AI-DEMO-0001", "CC-AI-DEMO-0001", 12369L,
                1300L, DepositPayStatusEnum.PAYING, createTime, payExpireTime, null, null);

        assertFalse(depositPayOrder.isExpiredAt(createTime.plusMinutes(5)));
    }

    /**
     * 测试支付成功状态必须存在支付成功时间。
     */
    @Test
    void shouldRejectPaidOrderWithoutPaidTime() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 22, 10, 0);

        assertThrows(IllegalArgumentException.class, () -> ColdChainDepositPayOrder.restore(
                1L, 1001L, "PAY-AI-DEMO-0001", "CC-AI-DEMO-0001", 12369L,
                1300L, DepositPayStatusEnum.PAID, createTime, createTime.plusMinutes(10), null, null));
    }

    /**
     * 测试支付成功属于最终状态，不再根据失效时间判定超时。
     */
    @Test
    void shouldNotMarkPaidOrderAsExpired() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 22, 10, 0);
        LocalDateTime paidTime = createTime.plusMinutes(2);

        ColdChainDepositPayOrder depositPayOrder = ColdChainDepositPayOrder.restore(
                1L, 1001L, "PAY-AI-DEMO-0001", "CC-AI-DEMO-0001", 12369L,
                1300L, DepositPayStatusEnum.PAID, createTime, createTime.plusMinutes(10), paidTime, null);

        assertTrue(depositPayOrder.isPaid());
        assertFalse(depositPayOrder.isExpiredAt(createTime.plusHours(1)));
    }
}
