package com.ymm.coldchainai.order.infrastructure.persistence.dataobject;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 冷运订单数据库对象。
 *
 * <p>该对象只负责承载MyBatis从cold_chain_order表中查询出的字段，不能直接作为Controller响应，也不能直接返回给Agent模型。</p>
 *
 * <p>在挖矿流程中，该对象相当于档案仓库中的原始登记表。登记表只说明数据库保存了什么，不能代替经过业务规则校验的订单领域对象；
 * 如果缺少DO与领域对象的隔离，数据库字段变化会直接污染Application和Agent层。</p>
 */
@Getter
@Setter
public class ColdChainOrderDO {

    /**
     * 数据库订单主键。
     */
    private Long id;

    /**
     * 订单所属租户ID。
     */
    private Long tenantId;

    /**
     * 对外稳定订单号。
     */
    private String orderNo;

    /**
     * 当前订单司机ID。
     */
    private Long driverId;

    /**
     * 装货城市。
     */
    private String pickupCity;

    /**
     * 卸货城市。
     */
    private String deliveryCity;

    /**
     * 数据库订单状态编码。
     */
    private Integer orderStatus;

    /**
     * 订单成交时间。
     */
    private LocalDateTime dealTime;
}
