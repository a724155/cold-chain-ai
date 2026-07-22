package com.ymm.coldchainai.payment.infrastructure.persistence.mapper;

import com.ymm.coldchainai.payment.infrastructure.persistence.dataobject.ColdChainDepositPayOrderDO;
import org.apache.ibatis.annotations.Param;

/**
 * 冷运定金支付单MyBatis Mapper。
 *
 * <p>该接口只负责把明确的数据库查询条件交给Mapper XML，
 * 不负责决定查询哪一笔支付单，也不负责判断支付是否成功或超时。</p>
 *
 * <p>在挖矿流程中，该Mapper相当于真正翻阅财务账本的档案员。
 * 它按照仓储主管给出的租户和订单条件查找记录，但不制定财务业务规则。</p>
 */
public interface IColdChainPayOrderMapper {

    /**
     * 查询指定租户和业务订单最新创建的一笔定金支付单。
     *
     * @param tenantId 当前查询所属租户ID
     * @param orderNo 冷运业务订单号
     * @return 最新一笔定金支付单数据库对象，不存在时返回null
     */
    ColdChainDepositPayOrderDO selectLatestDepositPayOrder(@Param("tenantId") Long tenantId, @Param("orderNo") String orderNo);
}
