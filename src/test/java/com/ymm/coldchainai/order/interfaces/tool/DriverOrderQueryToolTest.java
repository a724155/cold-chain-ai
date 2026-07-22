package com.ymm.coldchainai.order.interfaces.tool;

import com.ymm.coldchainai.agent.core.infrastructure.tool.AgentToolContextKeys;
import com.ymm.coldchainai.order.application.query.IDriverOrderQueryService;
import com.ymm.coldchainai.order.application.query.dto.DriverOrderSummaryDTO;
import com.ymm.coldchainai.order.application.query.model.DriverOrderQuery;
import com.ymm.coldchainai.order.interfaces.tool.response.DriverOrderQueryToolResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.time.LocalDateTime;
import java.util.List;
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
 * DriverOrderQueryTool单元测试。
 *
 * <p>该测试不调用真实模型和数据库，
 * 重点验证Tool能够从受信任ToolContext读取租户信息并调用Application Service。</p>
 */
@ExtendWith(MockitoExtension.class)
class DriverOrderQueryToolTest {

    /**
     * 模拟司机成交订单查询服务。
     */
    @Mock
    private IDriverOrderQueryService driverOrderQueryService;

    /**
     * 将模拟Application Service注入被测试Tool。
     */
    @InjectMocks
    private DriverOrderQueryTool driverOrderQueryTool;

    /**
     * 测试Tool使用受信任租户上下文查询并返回订单。
     */
    @Test
    void shouldQueryDriverOrderWithTrustedTenantContext() {
        DriverOrderSummaryDTO driverOrderSummaryDTO = DriverOrderSummaryDTO.of(
                "CC-AI-DEMO-0001",
                "南京市",
                "上海市",
                LocalDateTime.of(2026, 7, 22, 9, 30),
                20,
                "已成交");

        List<DriverOrderSummaryDTO> driverOrderSummaryDTOList = List.of(driverOrderSummaryDTO);

        when(driverOrderQueryService.queryDriverDealOrderList(any(DriverOrderQuery.class))).thenReturn(driverOrderSummaryDTOList);

        ToolContext toolContext = createToolContext();

        DriverOrderQueryToolResponse response = driverOrderQueryTool.queryDriverDealOrders(12369L, "2026-07-22", 20, toolContext);

        assertTrue(response.getSuccess());
        assertTrue(response.getHasDealOrder());
        assertEquals(1, response.getOrderCount());
        assertEquals("CC-AI-DEMO-0001", response.getOrderList().getFirst().getOrderNo());

        ArgumentCaptor<DriverOrderQuery> driverOrderQueryCaptor = ArgumentCaptor.forClass(DriverOrderQuery.class);
        verify(driverOrderQueryService).queryDriverDealOrderList(driverOrderQueryCaptor.capture());

        DriverOrderQuery driverOrderQuery = driverOrderQueryCaptor.getValue();

        // tenantId必须来自ToolContext中的1001，而不是模型Tool参数。
        assertEquals(1001L, driverOrderQuery.getTenantId());
        assertEquals(12369L, driverOrderQuery.getDriverId());
    }

    /**
     * 测试日期格式错误时返回结构化失败结果。
     */
    @Test
    void shouldReturnFailResponseWhenQueryDateFormatIsInvalid() {
        DriverOrderQueryToolResponse response = driverOrderQueryTool.queryDriverDealOrders(12369L, "2026/07/22", 20, createToolContext());

        assertFalse(response.getSuccess());
        assertEquals(0, response.getOrderCount());
        assertEquals("查询日期格式错误，请使用yyyy-MM-dd", response.getErrorMessage());

        // 日期在进入Application Service前已经失败，因此不能继续查询数据库。
        verify(driverOrderQueryService, never()).queryDriverDealOrderList(any(DriverOrderQuery.class));
    }

    /**
     * 测试缺少受信任ToolContext时直接抛出系统异常。
     */
    @Test
    void shouldThrowExceptionWhenToolContextIsMissing() {
        assertThrows(IllegalStateException.class, () -> driverOrderQueryTool.queryDriverDealOrders(12369L, "2026-07-22", 20, null));

        verify(driverOrderQueryService, never()).queryDriverDealOrderList(any(DriverOrderQuery.class));
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
