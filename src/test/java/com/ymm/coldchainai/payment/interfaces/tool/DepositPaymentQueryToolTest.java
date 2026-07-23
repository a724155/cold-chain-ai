package com.ymm.coldchainai.payment.interfaces.tool;

import com.ymm.coldchainai.agent.core.infrastructure.tool.AgentToolContextKeys;
import com.ymm.coldchainai.payment.application.enumtype.PaymentErrorCodeEnum;
import com.ymm.coldchainai.payment.application.query.IOrderDepositQueryService;
import com.ymm.coldchainai.payment.application.query.dto.OrderDepositQueryResultDTO;
import com.ymm.coldchainai.payment.application.query.model.OrderDepositQuery;
import com.ymm.coldchainai.payment.domain.enumtype.DepositPayStatusEnum;
import com.ymm.coldchainai.payment.interfaces.tool.response.DepositPaymentQueryToolResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DepositPaymentQueryTool单元测试。
 *
 * <p>该测试不调用真实模型和数据库，
 * 重点验证Tool从受信任ToolContext读取租户并调用支付Application Service。</p>
 */
@ExtendWith(MockitoExtension.class)
class DepositPaymentQueryToolTest {

    /**
     * 模拟订单定金支付查询Service。
     */
    @Mock
    private IOrderDepositQueryService orderDepositQueryService;

    /**
     * 将模拟Application Service注入被测试Tool。
     */
    @InjectMocks
    private DepositPaymentQueryTool depositPaymentQueryTool;

    /**
     * 测试Tool使用受信任租户上下文查询支付成功结果。
     */
    @Test
    void shouldQueryDepositPaymentWithTrustedTenantContext() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 22, 10, 0);
        LocalDateTime paidTime = createTime.plusMinutes(2);

        OrderDepositQueryResultDTO resultDTO = OrderDepositQueryResultDTO.created(
                "CC-AI-DEMO-0001",
                "PAY-AI-DEMO-0001-NEW",
                1300L,
                DepositPayStatusEnum.PAID.getCode(),
                DepositPayStatusEnum.PAID.getDescription(),
                true,
                false,
                false,
                createTime,
                createTime.plusMinutes(10),
                paidTime,
                null);

        when(orderDepositQueryService.queryOrderDeposit(any(OrderDepositQuery.class))).thenReturn(resultDTO);

        DepositPaymentQueryToolResponse response = depositPaymentQueryTool.queryOrderDepositPayment("CC-AI-DEMO-0001", createToolContext());

        assertTrue(response.getSuccess());
        assertTrue(response.getPayOrderCreated());
        assertTrue(response.getPaid());
        assertEquals("PAY-AI-DEMO-0001-NEW", response.getPayOrderNo());
        assertEquals(1300L, response.getDepositAmountCent());

        ArgumentCaptor<OrderDepositQuery> orderDepositQueryCaptor = ArgumentCaptor.forClass(OrderDepositQuery.class);
        verify(orderDepositQueryService).queryOrderDeposit(orderDepositQueryCaptor.capture());

        OrderDepositQuery orderDepositQuery = orderDepositQueryCaptor.getValue();

        // tenantId必须来自ToolContext，模型只提供orderNo。
        assertEquals(1001L, orderDepositQuery.getTenantId());
        assertEquals("CC-AI-DEMO-0001", orderDepositQuery.getOrderNo());
    }

    /**
     * 测试未创建支付单时Tool仍然返回正常成功结果。
     */
    @Test
    void shouldReturnNotCreatedResultWhenOrderHasNoPayOrder() {
        when(orderDepositQueryService.queryOrderDeposit(any(OrderDepositQuery.class))).thenReturn(OrderDepositQueryResultDTO.notCreated("CC-AI-DEMO-0004"));

        DepositPaymentQueryToolResponse response = depositPaymentQueryTool.queryOrderDepositPayment("CC-AI-DEMO-0004", createToolContext());

        assertTrue(response.getSuccess());
        assertFalse(response.getPayOrderCreated());
        assertFalse(response.getPaid());
        assertFalse(response.getExpired());
    }

    /**
     * 测试订单号为空时返回结构化业务失败结果。
     */
    @Test
    void shouldReturnFailResponseWhenOrderNoIsBlank() {
        DepositPaymentQueryToolResponse response = depositPaymentQueryTool.queryOrderDepositPayment(" ", createToolContext());

        assertFalse(response.getSuccess());
        assertEquals(PaymentErrorCodeEnum.ORDER_DEPOSIT_QUERY_PARAMETER_ERROR.getCode(), response.getErrorCode());
        assertEquals("业务订单号不能为空", response.getErrorMessage());

        verify(orderDepositQueryService, never()).queryOrderDeposit(any(OrderDepositQuery.class));
    }

    /**
     * 测试缺少ToolContext时直接抛出系统异常。
     */
    @Test
    void shouldThrowExceptionWhenToolContextIsMissing() {
        assertThrows(IllegalStateException.class, () -> depositPaymentQueryTool.queryOrderDepositPayment("CC-AI-DEMO-0001", null));

        verify(orderDepositQueryService, never()).queryOrderDeposit(any(OrderDepositQuery.class));
    }

    /**
     * 创建测试使用的受信任ToolContext。
     *
     * @return 包含任务、用户和租户信息的ToolContext
     */
    private ToolContext createToolContext() {
        Map<String, Object> toolContextMap = Map.of(
                AgentToolContextKeys.REQUEST_ID, "request-001",
                AgentToolContextKeys.AGENT_CODE, "cold-chain-general",
                AgentToolContextKeys.CURRENT_USER_ID, 90001L,
                AgentToolContextKeys.CURRENT_TENANT_ID, 1001L);

        return new ToolContext(toolContextMap);
    }
}
