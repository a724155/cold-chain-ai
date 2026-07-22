package com.ymm.coldchainai.order.domain.model;

import com.ymm.coldchainai.order.domain.enumtype.OrderStatusEnum;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 冷运订单领域对象。
 *
 * <p>该对象表达一张冷运订单在业务中的核心属性，不依赖MyBatis、数据库表、HTTP Request或Spring AI。</p>
 *
 * <p>在挖矿流程中，该对象相当于经过业务专家确认的一颗标准钻石：数据库DO只是仓库里的登记表，而领域对象负责保证订单编号、司机、
 * 成交时间和业务状态等核心信息是合法且可以用于业务判断的。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * pickupCity、deliveryCity、dealTime和orderStatus是否足够支撑产品展示与Agent回答，
 * 必须根据PRD确认。后端不能因为当前示例只查询订单号，就擅自认为以后永远不需要车型、
 * 货物类型、温区、装卸时间或取消原因。</p>
 */
@Getter
public class ColdChainOrder {

    /**
     * 数据库订单主键。
     */
    private final Long id;

    /**
     * 订单所属租户ID。
     */
    private final Long tenantId;

    /**
     * 对外稳定订单号。
     */
    private final String orderNo;

    /**
     * 当前订单对应的司机ID。
     */
    private final Long driverId;

    /**
     * 装货城市。
     */
    private final String pickupCity;

    /**
     * 卸货城市。
     */
    private final String deliveryCity;

    /**
     * 当前订单状态。
     */
    private final OrderStatusEnum orderStatus;

    /**
     * 订单成交时间。
     */
    private final LocalDateTime dealTime;

    /**
     * 从持久化数据恢复订单领域对象。
     *
     * <p>构造方法保持私有，外部必须通过restore方法恢复，
     * 防止Mapper或其他代码绕过必要字段校验直接构造不完整订单。</p>
     *
     * @param id 数据库订单主键
     * @param tenantId 订单所属租户ID
     * @param orderNo 对外订单号
     * @param driverId 司机ID
     * @param pickupCity 装货城市
     * @param deliveryCity 卸货城市
     * @param orderStatus 当前订单状态
     * @param dealTime 订单成交时间
     */
    private ColdChainOrder(Long id, Long tenantId, String orderNo, Long driverId, String pickupCity, String deliveryCity, OrderStatusEnum orderStatus, LocalDateTime dealTime) {
        this.id = id;
        this.tenantId = tenantId;
        this.orderNo = orderNo;
        this.driverId = driverId;
        this.pickupCity = pickupCity;
        this.deliveryCity = deliveryCity;
        this.orderStatus = orderStatus;
        this.dealTime = dealTime;
    }

    /**
     * 根据数据库数据恢复合法订单领域对象。
     *
     * @param id 数据库订单主键
     * @param tenantId 订单所属租户ID
     * @param orderNo 对外订单号
     * @param driverId 司机ID
     * @param pickupCity 装货城市
     * @param deliveryCity 卸货城市
     * @param orderStatus 当前订单状态
     * @param dealTime 订单成交时间
     * @return 合法冷运订单领域对象
     */
    public static ColdChainOrder restore(Long id, Long tenantId, String orderNo, Long driverId, String pickupCity, String deliveryCity, OrderStatusEnum orderStatus, LocalDateTime dealTime) {
        if (Objects.isNull(id) || id <= 0L) {
            throw new IllegalArgumentException("订单主键必须大于0");
        }

        if (Objects.isNull(tenantId) || tenantId <= 0L) {
            throw new IllegalArgumentException("订单租户ID必须大于0");
        }

        if (StringUtils.isBlank(orderNo)) {
            throw new IllegalArgumentException("订单号不能为空");
        }

        if (Objects.isNull(driverId) || driverId <= 0L) {
            throw new IllegalArgumentException("订单司机ID必须大于0");
        }

        if (StringUtils.isBlank(pickupCity)) {
            throw new IllegalArgumentException("订单装货城市不能为空");
        }

        if (StringUtils.isBlank(deliveryCity)) {
            throw new IllegalArgumentException("订单卸货城市不能为空");
        }

        if (Objects.isNull(orderStatus)) {
            throw new IllegalArgumentException("订单状态不能为空");
        }

        if (Objects.isNull(dealTime)) {
            throw new IllegalArgumentException("订单成交时间不能为空");
        }

        return new ColdChainOrder(id, tenantId, orderNo, driverId, pickupCity, deliveryCity, orderStatus, dealTime);
    }

    /**
     * 判断当前订单是否在指定日期发生过成交。
     *
     * @param queryDate 待判断业务日期
     * @return 指定日期发生过成交时返回true
     */
    public boolean isDealOn(LocalDate queryDate) {
        if (Objects.isNull(queryDate)) {
            return false;
        }

        return Objects.equals(queryDate, dealTime.toLocalDate());
    }
}
