package com.ymm.coldchainai.payment.application.query.model;

import com.ymm.coldchainai.payment.application.enumtype.PaymentErrorCodeEnum;
import com.ymm.coldchainai.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OrderDepositQuery单元测试。
 *
 * <p>该测试不启动Spring容器，
 * 重点验证受信任租户、业务订单号和后端查询时间的参数规则。</p>
 */
class OrderDepositQueryTest {

    /**
     * 测试合法参数可以创建查询对象，并自动去除订单号首尾空格。
     */
    @Test
    void shouldCreateOrderDepositQuery() {
        LocalDateTime queryTime = LocalDateTime.of(2026, 7, 22, 10, 30);

        OrderDepositQuery query = OrderDepositQuery.create(1001L, " CC-AI-DEMO-0001 ", queryTime);

        assertEquals(1001L, query.getTenantId());
        assertEquals("CC-AI-DEMO-0001", query.getOrderNo());
        assertEquals(queryTime, query.getQueryTime());
    }

    /**
     * 测试租户ID不合法时抛出支付业务异常。
     */
    @Test
    void shouldThrowBusinessExceptionWhenTenantIdIsInvalid() {
        BusinessException businessException = assertThrows(BusinessException.class,
                () -> OrderDepositQuery.create(0L, "CC-AI-DEMO-0001", LocalDateTime.of(2026, 7, 22, 10, 30)));

        assertEquals(PaymentErrorCodeEnum.ORDER_DEPOSIT_QUERY_PARAMETER_ERROR.getCode(), businessException.getCode());
        assertEquals("租户ID必须大于0", businessException.getMessage());
    }

    /**
     * 测试订单号为空时抛出支付业务异常。
     */
    @Test
    void shouldThrowBusinessExceptionWhenOrderNoIsBlank() {
        BusinessException businessException = assertThrows(BusinessException.class,
                () -> OrderDepositQuery.create(1001L, " ", LocalDateTime.of(2026, 7, 22, 10, 30)));

        assertEquals(PaymentErrorCodeEnum.ORDER_DEPOSIT_QUERY_PARAMETER_ERROR.getCode(), businessException.getCode());
        assertEquals("业务订单号不能为空", businessException.getMessage());
    }
}
