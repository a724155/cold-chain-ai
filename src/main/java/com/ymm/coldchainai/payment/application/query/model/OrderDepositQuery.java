package com.ymm.coldchainai.payment.application.query.model;

import com.ymm.coldchainai.payment.application.enumtype.PaymentErrorCodeEnum;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 订单定金支付查询参数。
 *
 * <p>该对象是支付Application层内部使用的查询模型，后续Tool请求不能直接代替该对象，必须先补充受信任租户上下文和后端查询时间。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * 开发前必须与产品确认查询依据是业务订单号、支付单号还是两者都支持，
 * 以及多次支付时返回哪一笔。当前暂定根据业务订单号查询最新一笔定金支付单。</p>
 *
 * <p><strong>安全提醒：</strong>
 * tenantId必须来自后端认证上下文；queryTime必须由后端生成；模型后续只能提供orderNo，不能伪造租户或系统时间。</p>
 *
 * <p>在挖矿流程中，该对象相当于项目经理整理后的标准财务查询单：客户只说明要查哪张订单，项目经理负责补齐真实公司身份和查询时间。</p>
 */
@Getter
public class OrderDepositQuery {

    /**
     * 当前查询所属租户ID。
     */
    private final Long tenantId;

    /**
     * 待查询的冷运业务订单号。
     */
    private final String orderNo;

    /**
     * 本次查询使用的后端时间基准。
     *
     * <p>该字段用于判断支付单是否已经超时，不由模型或前端传入。</p>
     */
    private final LocalDateTime queryTime;

    /**
     * 创建订单定金查询参数。
     *
     * @param tenantId 当前查询所属租户ID
     * @param orderNo 待查询业务订单号
     * @param queryTime 后端生成的查询时间
     */
    private OrderDepositQuery(Long tenantId, String orderNo, LocalDateTime queryTime) {
        this.tenantId = tenantId;
        this.orderNo = orderNo;
        this.queryTime = queryTime;
    }

    /**
     * 创建已经完成基础业务校验的订单定金查询。
     *
     * @param tenantId 当前查询所属租户ID
     * @param orderNo 待查询业务订单号
     * @param queryTime 后端生成的查询时间
     * @return 合法的订单定金查询参数
     */
    public static OrderDepositQuery create(Long tenantId, String orderNo, LocalDateTime queryTime) {
        if (Objects.isNull(tenantId) || tenantId <= 0L) {
            throw new BusinessException(PaymentErrorCodeEnum.ORDER_DEPOSIT_QUERY_PARAMETER_ERROR, "租户ID必须大于0");
        }

        if (StringUtils.isBlank(orderNo)) {
            throw new BusinessException(PaymentErrorCodeEnum.ORDER_DEPOSIT_QUERY_PARAMETER_ERROR, "业务订单号不能为空");
        }

        if (Objects.isNull(queryTime)) {
            throw new BusinessException(PaymentErrorCodeEnum.ORDER_DEPOSIT_QUERY_PARAMETER_ERROR, "定金支付查询时间不能为空");
        }

        return new OrderDepositQuery(tenantId, StringUtils.trim(orderNo), queryTime);
    }
}
