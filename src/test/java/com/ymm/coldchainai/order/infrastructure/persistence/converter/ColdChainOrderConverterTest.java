package com.ymm.coldchainai.order.infrastructure.persistence.converter;

import com.ymm.coldchainai.order.domain.enumtype.OrderStatusEnum;
import com.ymm.coldchainai.order.domain.model.ColdChainOrder;
import com.ymm.coldchainai.order.infrastructure.persistence.dataobject.ColdChainOrderDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ColdChainOrderConverter单元测试。
 *
 * <p>该测试不启动Spring容器和数据库，
 * 重点验证数据库状态编码到订单领域对象的转换和异常数据拦截。</p>
 */
class ColdChainOrderConverterTest {

    /**
     * 测试合法数据库对象可以恢复成订单领域对象。
     */
    @Test
    void shouldConvertOrderDOToDomain() {
        ColdChainOrderDO coldChainOrderDO = createColdChainOrderDO();
        ColdChainOrderConverter coldChainOrderConverter = new ColdChainOrderConverter();

        ColdChainOrder coldChainOrder = coldChainOrderConverter.convertToDomain(coldChainOrderDO);

        assertEquals(1L, coldChainOrder.getId());
        assertEquals(1001L, coldChainOrder.getTenantId());
        assertEquals("CC-AI-DEMO-0001", coldChainOrder.getOrderNo());
        assertEquals(OrderStatusEnum.DEAL_CONFIRMED, coldChainOrder.getOrderStatus());
    }

    /**
     * 测试数据库存在未知订单状态时拒绝返回不完整领域对象。
     */
    @Test
    void shouldThrowExceptionWhenOrderStatusIsUnknown() {
        ColdChainOrderDO coldChainOrderDO = createColdChainOrderDO();

        // 状态99不在OrderStatusEnum中，用于模拟数据库脏数据或代码版本不一致。
        coldChainOrderDO.setOrderStatus(99);

        ColdChainOrderConverter coldChainOrderConverter = new ColdChainOrderConverter();

        assertThrows(IllegalStateException.class, () -> coldChainOrderConverter.convertToDomain(coldChainOrderDO));
    }

    /**
     * 创建测试使用的冷运订单数据库对象。
     *
     * @return 字段完整的冷运订单DO
     */
    private ColdChainOrderDO createColdChainOrderDO() {
        ColdChainOrderDO coldChainOrderDO = new ColdChainOrderDO();

        coldChainOrderDO.setId(1L);
        coldChainOrderDO.setTenantId(1001L);
        coldChainOrderDO.setOrderNo("CC-AI-DEMO-0001");
        coldChainOrderDO.setDriverId(12369L);
        coldChainOrderDO.setPickupCity("南京市");
        coldChainOrderDO.setDeliveryCity("上海市");
        coldChainOrderDO.setOrderStatus(OrderStatusEnum.DEAL_CONFIRMED.getCode());
        coldChainOrderDO.setDealTime(LocalDateTime.of(2026, 7, 22, 9, 30));

        return coldChainOrderDO;
    }
}
