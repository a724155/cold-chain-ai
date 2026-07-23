package com.ymm.coldchainai.payment.infrastructure.persistence.repository;

import com.ymm.coldchainai.payment.domain.enumtype.DepositPayStatusEnum;
import com.ymm.coldchainai.payment.domain.model.ColdChainDepositPayOrder;
import com.ymm.coldchainai.payment.infrastructure.persistence.converter.ColdChainDepositPayOrderConverter;
import com.ymm.coldchainai.payment.infrastructure.persistence.dataobject.ColdChainDepositPayOrderDO;
import com.ymm.coldchainai.payment.infrastructure.persistence.mapper.IColdChainPayOrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * ColdChainPayOrderRepositoryImpl单元测试。
 *
 * <p>该测试使用Mockito模拟Mapper和Converter，
 * 验证未查询到支付单和DO转换领域对象的Repository编排。</p>
 */
@ExtendWith(MockitoExtension.class)
class ColdChainPayOrderRepositoryImplTest {

    /**
     * 模拟支付单Mapper。
     */
    @Mock
    private IColdChainPayOrderMapper coldChainPayOrderMapper;

    /**
     * 模拟支付单转换器。
     */
    @Mock
    private ColdChainDepositPayOrderConverter coldChainDepositPayOrderConverter;

    /**
     * 将模拟依赖注入被测试Repository。
     */
    @InjectMocks
    private ColdChainPayOrderRepositoryImpl coldChainPayOrderRepository;

    /**
     * 测试Mapper返回null时Repository返回Optional.empty()。
     */
    @Test
    void shouldReturnEmptyOptionalWhenMapperReturnsNull() {
        when(coldChainPayOrderMapper.selectLatestDepositPayOrder(1001L, "CC-AI-DEMO-0004")).thenReturn(null);

        Optional<ColdChainDepositPayOrder> depositPayOrderOptional = coldChainPayOrderRepository.findLatestDepositPayOrder(1001L, "CC-AI-DEMO-0004");

        assertTrue(depositPayOrderOptional.isEmpty());
    }

    /**
     * 测试Repository会把Mapper返回的DO转换成领域对象。
     */
    @Test
    void shouldConvertMapperResultToDomain() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 22, 10, 0);

        ColdChainDepositPayOrderDO depositPayOrderDO = new ColdChainDepositPayOrderDO();

        ColdChainDepositPayOrder depositPayOrder = ColdChainDepositPayOrder.restore(
                1L, 1001L, "PAY-AI-DEMO-0001-NEW", "CC-AI-DEMO-0001", 12369L,
                1300L, DepositPayStatusEnum.PAID, createTime, createTime.plusMinutes(10), createTime.plusMinutes(2), null);

        when(coldChainPayOrderMapper.selectLatestDepositPayOrder(1001L, "CC-AI-DEMO-0001")).thenReturn(depositPayOrderDO);
        when(coldChainDepositPayOrderConverter.convertToDomain(depositPayOrderDO)).thenReturn(depositPayOrder);

        Optional<ColdChainDepositPayOrder> depositPayOrderOptional = coldChainPayOrderRepository.findLatestDepositPayOrder(1001L, "CC-AI-DEMO-0001");

        assertTrue(depositPayOrderOptional.isPresent());
        assertEquals("PAY-AI-DEMO-0001-NEW", depositPayOrderOptional.get().getPayOrderNo());
    }
}
