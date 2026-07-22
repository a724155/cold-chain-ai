package com.ymm.coldchainai.order.infrastructure.persistence.mapper;

import com.ymm.coldchainai.order.infrastructure.persistence.dataobject.ColdChainOrderDO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 冷运订单MyBatis Mapper。
 *
 * <p>该接口负责把明确的数据库查询参数交给Mapper XML执行，不负责租户权限判断、业务日期计算和订单摘要转换。</p>
 *
 * <p>在挖矿流程中，该Mapper相当于真正翻阅订单账本的档案员。
 * 它只根据已经整理好的查询条件查找记录，不负责决定客户是否有权查看这些档案。</p>
 */
public interface IColdChainOrderMapper {

    /**
     * 查询司机在指定时间范围内发生过成交的订单。
     *
     * @param tenantId 当前查询所属租户ID
     * @param driverId 待查询司机ID
     * @param dealStartTime 成交时间左闭区间起点
     * @param dealEndTime 成交时间右开区间终点
     * @param maxResultCount 最大返回数量
     * @return 冷运订单数据库对象列表
     */
    List<ColdChainOrderDO> selectDriverDealOrderList(@Param("tenantId") Long tenantId,
                                                     @Param("driverId") Long driverId,
                                                     @Param("dealStartTime") LocalDateTime dealStartTime,
                                                     @Param("dealEndTime") LocalDateTime dealEndTime,
                                                     @Param("maxResultCount") Integer maxResultCount);
}
