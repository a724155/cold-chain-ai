package com.ymm.coldchainai.order.domain.repository;

import com.ymm.coldchainai.order.domain.model.ColdChainOrder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 冷运订单仓储端口。
 *
 * <p>Application和Domain层通过该接口查询订单，不直接依赖MyBatis、Mapper XML或者具体数据库表。</p>
 *
 * <p>在挖矿流程中，该接口相当于项目经理向矿场档案仓库提交查询申请的统一规范：
 * 项目经理只说明查询哪个租户、哪个司机和哪个时间范围，不需要知道档案员最终使用MySQL还是其他存储系统。</p>
 */
public interface IColdChainOrderRepository {

    /**
     * 查询司机在指定成交时间范围内的订单。
     *
     * <p>时间范围使用左闭右开区间：[dealStartTime, dealEndTime)，
     * 避免依赖23:59:59.999等容易遗漏精度的结束时间。</p>
     *
     * @param tenantId 当前查询所属租户ID
     * @param driverId 待查询司机ID
     * @param dealStartTime 成交时间范围起点，包含
     * @param dealEndTime 成交时间范围终点，不包含
     * @param maxResultCount 最大返回数量
     * @return 满足条件的冷运订单列表
     */
    List<ColdChainOrder> listDriverDealOrderList(Long tenantId, Long driverId, LocalDateTime dealStartTime, LocalDateTime dealEndTime, Integer maxResultCount);
}
