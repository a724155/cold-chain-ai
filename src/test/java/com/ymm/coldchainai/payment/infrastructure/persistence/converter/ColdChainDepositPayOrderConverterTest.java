package com.ymm.coldchainai.payment.infrastructure.persistence.converter;

import com.ymm.coldchainai.payment.domain.enumtype.DepositPayStatusEnum;
import com.ymm.coldchainai.payment.domain.model.ColdChainDepositPayOrder;
import com.ymm.coldchainai.payment.infrastructure.persistence.dataobject.ColdChainDepositPayOrderDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ColdChainDepositPayOrderConverter单元测试。
 *
 * <p>该测试不连接数据库，
 * 重点验证支付状态转换和异常支付数据拦截。</p>
 */
class ColdChainDepositPayOrderConverterTest {

    /**
     * 测试合法支付单DO可以恢复成领域对象。
     */
    @Test
    void shouldConvertPayOrderDOToDomain() {
        ColdChainDepositPayOrderDO depositPayOrderDO = createPaidPayOrderDO();
        ColdChainDepositPayOrderConverter converter = new ColdChainDepositPayOrderConverter();

        ColdChainDepositPayOrder depositPayOrder = converter.convertToDomain(depositPayOrderDO);

        assertEquals("PAY-AI-DEMO-0001-NEW", depositPayOrder.getPayOrderNo());
        assertEquals("CC-AI-DEMO-0001", depositPayOrder.getOrderNo());
        assertEquals(DepositPayStatusEnum.PAID, depositPayOrder.getPayStatus());
        assertEquals(1300L, depositPayOrder.getDepositAmountCent());
    }

    /**
     * 测试未知支付状态编码会被转换器拦截。
     */
    @Test
    void shouldThrowExceptionWhenPayStatusIsUnknown() {
        ColdChainDepositPayOrderDO depositPayOrderDO = createPaidPayOrderDO();
        depositPayOrderDO.setPayStatus(99);

        ColdChainDepositPayOrderConverter converter = new ColdChainDepositPayOrderConverter();

        assertThrows(IllegalStateException.class, () -> converter.convertToDomain(depositPayOrderDO));
    }

    /**
     * 测试支付成功但缺少paidTime的数据会被转换器拦截。
     */
    @Test
    void shouldThrowExceptionWhenPaidOrderHasNoPaidTime() {
        ColdChainDepositPayOrderDO depositPayOrderDO = createPaidPayOrderDO();
        depositPayOrderDO.setPaidTime(null);

        ColdChainDepositPayOrderConverter converter = new ColdChainDepositPayOrderConverter();

        assertThrows(IllegalStateException.class, () -> converter.convertToDomain(depositPayOrderDO));
    }

    /**
     * 创建测试使用的支付成功DO。
     *
     * @return 字段完整的支付单DO
     */
    private ColdChainDepositPayOrderDO createPaidPayOrderDO() {
        LocalDateTime createTime = LocalDateTime.of(2026, 7, 22, 10, 0);

        ColdChainDepositPayOrderDO depositPayOrderDO = new ColdChainDepositPayOrderDO();
        depositPayOrderDO.setId(1L);
        depositPayOrderDO.setTenantId(1001L);
        depositPayOrderDO.setPayOrderNo("PAY-AI-DEMO-0001-NEW");
        depositPayOrderDO.setOrderNo("CC-AI-DEMO-0001");
        depositPayOrderDO.setDriverId(12369L);
        depositPayOrderDO.setDepositAmountCent(1300L);
        depositPayOrderDO.setPayStatus(DepositPayStatusEnum.PAID.getCode());
        depositPayOrderDO.setCreateTime(createTime);
        depositPayOrderDO.setPayExpireTime(createTime.plusMinutes(10));
        depositPayOrderDO.setPaidTime(createTime.plusMinutes(2));

        return depositPayOrderDO;
    }
}