package com.ymm.coldchainai.order.application.query;

import com.ymm.coldchainai.order.application.query.dto.DriverOrderSummaryDTO;
import com.ymm.coldchainai.order.application.query.model.DriverOrderQuery;

import java.util.List;

/**
 * 司机成交订单查询服务。
 *
 * <p>该服务完成“查询某个司机指定日期成交订单”的完整业务用例，后续Agent Tool只能调用该Application Service，不能直接访问Repository或Mapper。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * 正式开发前需要通过PRD确认成交定义、查询范围、返回上限、权限要求和输出字段。Application Service不能在产品规则不明确时自行决定过滤哪些订单。</p>
 */
public interface IDriverOrderQueryService {

    /**
     * 查询司机在指定日期发生过成交的订单摘要。
     *
     * @param query 已经完成基础校验的司机订单查询
     * @return 司机成交订单摘要列表，没有结果时返回空列表
     */
    List<DriverOrderSummaryDTO> queryDriverDealOrderList(DriverOrderQuery query);
}
