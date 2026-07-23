package com.ymm.coldchainai.payment.application.query.impl;

import com.ymm.coldchainai.payment.application.query.dto.OrderDepositQueryResultDTO;
import com.ymm.coldchainai.payment.application.query.model.OrderDepositQuery;
import com.ymm.coldchainai.payment.domain.enumtype.DepositPayStatusEnum;
import com.ymm.coldchainai.payment.domain.model.ColdChainDepositPayOrder;
import com.ymm.coldchainai.payment.domain.repository.IColdChainPayOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * OrderDepositQueryServiceImpl单元测试。
 *
 * <p>该测试使用Mockito模拟支付Repository，
 * 验证未创建支付单、支付成功和支付中超时等结果组装规则。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderDepositQueryServiceImplTest {

    /**
     * 模拟冷运定金支付单Repository。
     */
    @Mock
    private IColdChainPayOrderRepository coldChainPayOrderRepository;

    /**
     * 将模拟Repository注入被测试Application Service。
     */
    @InjectMocks
    private OrderDepositQueryServiceImpl orderDepositQueryService;

    /**
     * 测试未创建支付单时返回正常结果。
     */
    @Test
    void shouldReturnNotCreatedResultWhenPayOrderDoesNotExist() {
        LocalDateTime queryTime = LocalDateTime.of(2026, 7, 22, 10, 30);
        OrderDepositQuery query = OrderDepositQuery.create(1001L, "CC-AI-DEMO-0004", queryTime);

        when(coldChainPayOrderRepository.findLatestDepositPayOrder(1001L, "CC-AI-DEMO-0004")).thenReturn(Optional.empty());

        OrderDepositQueryResultDTO resultDTO = orderDepositQueryService.queryOrderDeposit(query);

        assertFalse(resultDTO.getPayOrderCreated());
        assertFalse(resultDTO.getPaid());
        assertFalse(resultDTO.getPaying());
        assertFalse(resultDTO.getExpired());
        assertNull(resultDTO.getPayOrderNo());
    }

    /**
     * 测试支付中的支付单超过失效时间后返回expired=true。
     */
    @Test
    void shouldReturnExpiredResultWhenPayingOrderExceedsExpireTime() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 22, 10, 0);
        LocalDateTime payExpireTime = createTime.plusMinutes(10);
        OrderDepositQuery query = OrderDepositQuery.create(1001L, "CC-AI-DEMO-0003", createTime.plusMinutes(11));

        ColdChainDepositPayOrder depositPayOrder = ColdChainDepositPayOrder.restore(
                1L, 1001L, "PAY-AI-DEMO-0003", "CC-AI-DEMO-0003", 12369L,
                1800L, DepositPayStatusEnum.PAYING, createTime, payExpireTime, null, null);

        when(coldChainPayOrderRepository.findLatestDepositPayOrder(1001L, "CC-AI-DEMO-0003")).thenReturn(Optional.of(depositPayOrder));

        OrderDepositQueryResultDTO resultDTO = orderDepositQueryService.queryOrderDeposit(query);

        assertTrue(resultDTO.getPayOrderCreated());
        assertTrue(resultDTO.getPaying());
        assertTrue(resultDTO.getExpired());
        assertFalse(resultDTO.getPaid());
    }

    /**
     * 测试支付成功结果包含支付单号、金额和支付成功时间。
     */
    @Test
    void shouldReturnPaidResultWhenLatestPayOrderIsPaid() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 22, 10, 0);
        LocalDateTime paidTime = createTime.plusMinutes(2);
        OrderDepositQuery query = OrderDepositQuery.create(1001L, "CC-AI-DEMO-0001", createTime.plusMinutes(30));

        ColdChainDepositPayOrder depositPayOrder = ColdChainDepositPayOrder.restore(
                1L, 1001L, "PAY-AI-DEMO-0001-NEW", "CC-AI-DEMO-0001", 12369L,
                1300L, DepositPayStatusEnum.PAID, createTime, createTime.plusMinutes(10), paidTime, null);

        when(coldChainPayOrderRepository.findLatestDepositPayOrder(1001L, "CC-AI-DEMO-0001")).thenReturn(Optional.of(depositPayOrder));

        OrderDepositQueryResultDTO resultDTO = orderDepositQueryService.queryOrderDeposit(query);

        assertTrue(resultDTO.getPayOrderCreated());
        assertTrue(resultDTO.getPaid());
        assertFalse(resultDTO.getPaying());
        assertFalse(resultDTO.getExpired());
        assertEquals("PAY-AI-DEMO-0001-NEW", resultDTO.getPayOrderNo());
        assertEquals(1300L, resultDTO.getDepositAmountCent());
        assertEquals(paidTime, resultDTO.getPaidTime());
    }
}
