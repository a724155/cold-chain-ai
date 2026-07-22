package com.ymm.coldchainai.order.infrastructure.persistence.repository;

import com.ymm.coldchainai.order.domain.enumtype.OrderStatusEnum;
import com.ymm.coldchainai.order.domain.model.ColdChainOrder;
import com.ymm.coldchainai.order.infrastructure.persistence.converter.ColdChainOrderConverter;
import com.ymm.coldchainai.order.infrastructure.persistence.dataobject.ColdChainOrderDO;
import com.ymm.coldchainai.order.infrastructure.persistence.mapper.IColdChainOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * ColdChainOrderRepositoryImpl单元测试。
 *
 * <p>该测试使用Mockito模拟Mapper和Converter，
 * 不连接真实MySQL，重点验证Repository的空集合处理和转换编排。</p>
 */
@ExtendWith(MockitoExtension.class)
class ColdChainOrderRepositoryImplTest {

    /**
     * 模拟冷运订单Mapper。
     */
    @Mock
    private IColdChainOrderMapper coldChainOrderMapper;

    /**
     * 模拟冷运订单转换器。
     */
    @Mock
    private ColdChainOrderConverter coldChainOrderConverter;

    /**
     * 将模拟依赖注入被测试Repository。
     */
    @InjectMocks
    private ColdChainOrderRepositoryImpl coldChainOrderRepository;

    /**
     * 测试Mapper没有返回订单时Repository统一返回空List。
     */
    @Test
    void shouldReturnEmptyListWhenMapperReturnsNull() {
        LocalDateTime dealStartTime = LocalDateTime.of(2026, 7, 22, 0, 0);
        LocalDateTime dealEndTime = dealStartTime.plusDays(1);

        when(coldChainOrderMapper.selectDriverDealOrderList(1001L, 12369L, dealStartTime, dealEndTime, 20)).thenReturn(null);

        List<ColdChainOrder> coldChainOrderList = coldChainOrderRepository.listDriverDealOrderList(1001L, 12369L, dealStartTime, dealEndTime, 20);

        assertTrue(coldChainOrderList.isEmpty());
    }

    /**
     * 测试Repository会把Mapper返回的DO转换成领域对象。
     */
    @Test
    void shouldConvertMapperResultToDomainList() {
        LocalDateTime dealStartTime = LocalDateTime.of(2026, 7, 22, 0, 0);
        LocalDateTime dealEndTime = dealStartTime.plusDays(1);

        ColdChainOrderDO coldChainOrderDO = new ColdChainOrderDO();
        ColdChainOrder coldChainOrder = ColdChainOrder.restore(1L, 1001L, "CC-AI-DEMO-0001", 12369L, "南京市", "上海市", OrderStatusEnum.DEAL_CONFIRMED, LocalDateTime.of(2026, 7, 22, 9, 30));

        List<ColdChainOrderDO> coldChainOrderDOList = List.of(coldChainOrderDO);

        when(coldChainOrderMapper.selectDriverDealOrderList(1001L, 12369L, dealStartTime, dealEndTime, 20)).thenReturn(coldChainOrderDOList);
        when(coldChainOrderConverter.convertToDomain(coldChainOrderDO)).thenReturn(coldChainOrder);

        List<ColdChainOrder> coldChainOrderList = coldChainOrderRepository.listDriverDealOrderList(1001L, 12369L, dealStartTime, dealEndTime, 20);

        assertEquals(1, coldChainOrderList.size());
        assertEquals("CC-AI-DEMO-0001", coldChainOrderList.getFirst().getOrderNo());
    }
}
