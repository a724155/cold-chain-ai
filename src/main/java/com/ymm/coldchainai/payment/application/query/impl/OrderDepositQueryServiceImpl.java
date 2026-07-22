package com.ymm.coldchainai.payment.application.query.impl;

import com.ymm.coldchainai.payment.application.enumtype.PaymentErrorCodeEnum;
import com.ymm.coldchainai.payment.application.query.IOrderDepositQueryService;
import com.ymm.coldchainai.payment.application.query.dto.OrderDepositQueryResultDTO;
import com.ymm.coldchainai.payment.application.query.model.OrderDepositQuery;
import com.ymm.coldchainai.payment.domain.model.ColdChainDepositPayOrder;
import com.ymm.coldchainai.payment.domain.repository.IColdChainPayOrderRepository;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 订单定金支付查询服务实现。
 *
 * <p>该类负责调用支付单Repository、识别支付单是否存在，并根据领域对象计算支付成功、支付中和超时等查询结论。</p>
 *
 * <p>在挖矿流程中，该类相当于负责定金查询的财务项目经理：
 * 它让财务仓库找到最新收款单，再根据收款状态和失效时间整理成客户能够理解的结论。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * 当前采用“查询最新创建支付单”的暂定规则。真实上线前必须确认：如果最新支付单失败，但更早的一笔已经成功，最终应该返回哪一笔，不能直接沿用教学规则。</p>
 *
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class OrderDepositQueryServiceImpl implements IOrderDepositQueryService {

    /**
     * 查询参数为空时使用的业务提示。
     */
    private static final String ORDER_DEPOSIT_QUERY_IS_NULL_MESSAGE = "订单定金支付查询对象不能为空";

    /**
     * 冷运定金支付单Repository。
     */
    private final IColdChainPayOrderRepository coldChainPayOrderRepository;

    /**
     * 查询冷运订单最新一笔定金支付状态。
     *
     * @param query 已经完成基础校验的订单定金查询参数
     * @return 结构化定金支付查询结果
     */
    @Override
    public OrderDepositQueryResultDTO queryOrderDeposit(OrderDepositQuery query) {
        if (Objects.isNull(query)) {
            throw new BusinessException(PaymentErrorCodeEnum.ORDER_DEPOSIT_QUERY_PARAMETER_ERROR, ORDER_DEPOSIT_QUERY_IS_NULL_MESSAGE);
        }

        // Repository相当于财务档案仓库，Application层只要求查询指定租户和订单的最新支付单，不直接操作Mapper，也不关心具体SQL。
        Optional<ColdChainDepositPayOrder> depositPayOrderOptional = coldChainPayOrderRepository.findLatestDepositPayOrder(query.getTenantId(), query.getOrderNo());

        if (depositPayOrderOptional.isEmpty()) {
            // 未创建支付单属于正常业务结果，不返回null，也不抛出系统异常。
            return OrderDepositQueryResultDTO.notCreated(query.getOrderNo());
        }

        ColdChainDepositPayOrder depositPayOrder = depositPayOrderOptional.get();

        // 领域对象根据后端查询时间判断非最终状态支付单是否已经超时。
        boolean expired = depositPayOrder.isExpiredAt(query.getQueryTime());

        return OrderDepositQueryResultDTO.created(
                depositPayOrder.getOrderNo(),
                depositPayOrder.getPayOrderNo(),
                depositPayOrder.getDepositAmountCent(),
                depositPayOrder.getPayStatus().getCode(),
                depositPayOrder.getPayStatus().getDescription(),
                depositPayOrder.isPaid(),
                depositPayOrder.isPaying(),
                expired,
                depositPayOrder.getCreateTime(),
                depositPayOrder.getPayExpireTime(),
                depositPayOrder.getPaidTime(),
                depositPayOrder.getFailureReason());
    }
}
