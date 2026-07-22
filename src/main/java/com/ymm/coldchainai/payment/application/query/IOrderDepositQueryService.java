package com.ymm.coldchainai.payment.application.query;

import com.ymm.coldchainai.payment.application.query.dto.OrderDepositQueryResultDTO;
import com.ymm.coldchainai.payment.application.query.model.OrderDepositQuery;

/**
 * 订单定金支付查询服务。
 *
 * <p>该服务完成“查询一张冷运订单最新定金支付状态”的完整业务用例。
 * 后续支付Tool只能调用该Application Service，不能直接访问Mapper或支付表。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * 正式上线前必须确认多次支付选择规则、支付中状态解释、超时展示方式、金额单位和失败原因展示范围，不能由后端自行假设。</p>
 */
public interface IOrderDepositQueryService {

    /**
     * 查询冷运订单最新一笔定金支付状态。
     *
     * @param query 已经完成基础校验的订单定金查询参数
     * @return 结构化定金支付查询结果
     */
    OrderDepositQueryResultDTO queryOrderDeposit(OrderDepositQuery query);
}
