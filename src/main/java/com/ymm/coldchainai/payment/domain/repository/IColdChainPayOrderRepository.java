package com.ymm.coldchainai.payment.domain.repository;

import com.ymm.coldchainai.payment.domain.model.ColdChainDepositPayOrder;

import java.util.Optional;

/**
 * 冷运定金支付单仓储端口。
 *
 * <p>Application和Domain层通过该接口查询支付单，不直接依赖MyBatis、Mapper XML或具体数据库表。</p>
 *
 * <p>在挖矿流程中，该接口相当于项目经理向财务账本仓库提交查询申请的统一规范：项目经理只说明租户和业务订单号，不需要知道财务人员如何查询MySQL。</p>
 */
public interface IColdChainPayOrderRepository {

    /**
     * 查询指定业务订单最新创建的一笔定金支付单。
     *
     * <p>一张业务订单可能因为关闭、超时或重新支付产生多次支付尝试，当前约定按照支付单创建时间倒序查询最新一笔。</p>
     *
     * @param tenantId 当前查询所属租户ID
     * @param orderNo 冷运业务订单号
     * @return 最新定金支付单，不存在时返回Optional.empty()
     */
    Optional<ColdChainDepositPayOrder> findLatestDepositPayOrder(Long tenantId, String orderNo);
}
