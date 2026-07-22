package com.ymm.coldchainai.order.application.query.model;

import com.ymm.coldchainai.order.application.enumtype.OrderErrorCodeEnum;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 司机成交订单查询参数。
 *
 * <p>该对象是订单Application层内部使用的查询模型，后续Tool请求不能直接代替该对象，必须完成受信任上下文补充和参数转换。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * 开发前必须与产品确认“指定日期”的含义、默认日期、最大返回数量、是否包含成交后取消的订单以及没有结果时的展示方式。</p>
 *
 * <p><strong>安全提醒：</strong>
 * tenantId必须来自当前登录上下文，不能由模型生成，也不能直接信任前端参数。
 * driverId和queryDate可以来自Tool请求，但仍然需要进行权限和范围校验。</p>
 *
 * <p>在挖矿流程中，该对象相当于项目经理整理后的标准作业指令：
 * 客户的自然语言不能直接交给档案仓库，必须先转换成明确的租户、司机、日期和数量限制。</p>
 */
@Getter
public class DriverOrderQuery {

    /**
     * 默认最大返回订单数量。
     */
    private static final int DEFAULT_MAX_RESULT_COUNT = 20;

    /**
     * 单次查询允许返回的最大订单数量。
     */
    private static final int MAX_RESULT_COUNT = 100;

    /**
     * 当前查询所属租户ID。
     */
    private final Long tenantId;

    /**
     * 待查询司机ID。
     */
    private final Long driverId;

    /**
     * 待查询成交日期。
     */
    private final LocalDate queryDate;

    /**
     * 本次允许返回的最大订单数量。
     */
    private final Integer maxResultCount;

    /**
     * 创建已经完成业务参数校验的查询对象。
     *
     * @param tenantId 当前查询所属租户ID
     * @param driverId 待查询司机ID
     * @param queryDate 待查询成交日期
     * @param maxResultCount 最大返回数量，可以为空
     */
    private DriverOrderQuery(Long tenantId, Long driverId, LocalDate queryDate, Integer maxResultCount) {
        this.tenantId = tenantId;
        this.driverId = driverId;
        this.queryDate = queryDate;
        this.maxResultCount = maxResultCount;
    }

    /**
     * 创建司机成交订单查询。
     *
     * @param tenantId 当前查询所属租户ID
     * @param driverId 待查询司机ID
     * @param queryDate 待查询成交日期
     * @param maxResultCount 最大返回数量，可以为空
     * @return 合法的司机成交订单查询
     */
    public static DriverOrderQuery create(Long tenantId, Long driverId, LocalDate queryDate, Integer maxResultCount) {
        if (Objects.isNull(tenantId) || tenantId <= 0L) {
            throw new BusinessException(OrderErrorCodeEnum.DRIVER_ORDER_QUERY_PARAMETER_ERROR, "租户ID必须大于0");
        }

        if (Objects.isNull(driverId) || driverId <= 0L) {
            throw new BusinessException(OrderErrorCodeEnum.DRIVER_ORDER_QUERY_PARAMETER_ERROR, "司机ID必须大于0");
        }

        if (Objects.isNull(queryDate)) {
            throw new BusinessException(OrderErrorCodeEnum.DRIVER_ORDER_QUERY_PARAMETER_ERROR, "订单查询日期不能为空");
        }

        // 调用方没有指定数量时使用默认值，避免一次性查询不受限制的数据。
        int resolvedMaxResultCount = Objects.isNull(maxResultCount) ? DEFAULT_MAX_RESULT_COUNT : maxResultCount;

        if (resolvedMaxResultCount <= 0 || resolvedMaxResultCount > MAX_RESULT_COUNT) {
            throw new BusinessException(OrderErrorCodeEnum.DRIVER_ORDER_QUERY_PARAMETER_ERROR, "最大返回数量必须在1到100之间");
        }

        return new DriverOrderQuery(tenantId, driverId, queryDate, resolvedMaxResultCount);
    }
}
