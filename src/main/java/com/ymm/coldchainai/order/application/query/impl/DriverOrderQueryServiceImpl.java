package com.ymm.coldchainai.order.application.query.impl;

import com.ymm.coldchainai.order.application.enumtype.OrderErrorCodeEnum;
import com.ymm.coldchainai.order.application.query.IDriverOrderQueryService;
import com.ymm.coldchainai.order.application.query.dto.DriverOrderSummaryDTO;
import com.ymm.coldchainai.order.application.query.model.DriverOrderQuery;
import com.ymm.coldchainai.order.domain.model.ColdChainOrder;
import com.ymm.coldchainai.order.domain.repository.IColdChainOrderRepository;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 司机成交订单查询服务实现。
 *
 * <p>该类负责计算业务日期范围、调用订单Repository，
 * 并将订单领域对象转换成Application层摘要DTO。</p>
 *
 * <p>在挖矿流程中，该类相当于负责订单查询任务的项目经理：
 * 它把“查询某天订单”拆解成明确的开始时间、结束时间和数量限制，
 * 然后安排档案仓库查询，最后整理成可以交给Agent的摘要。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * 当前按照dealTime判断订单在哪一天成交，并且不根据当前订单状态排除取消订单。
 * 该规则上线前必须由产品确认，不能把本示例规则直接当成真实生产规则。</p>
 *
 * <p>当前类暂时不添加@Service，因为MyBatis Repository实现将在下一小步创建。
 * 现在提前注册为Spring Bean会因为缺少IColdChainOrderRepository实现而导致项目无法启动。</p>
 */
@RequiredArgsConstructor
public class DriverOrderQueryServiceImpl implements IDriverOrderQueryService {

    /**
     * 订单查询对象为空时使用的业务提示。
     */
    private static final String ORDER_QUERY_IS_NULL_MESSAGE = "司机成交订单查询对象不能为空";

    /**
     * 冷运订单仓储端口。
     */
    private final IColdChainOrderRepository coldChainOrderRepository;

    /**
     * 查询司机在指定日期发生过成交的订单摘要。
     *
     * @param query 已经完成基础校验的司机订单查询
     * @return 司机成交订单摘要列表
     */
    @Override
    public List<DriverOrderSummaryDTO> queryDriverDealOrderList(DriverOrderQuery query) {
        if (Objects.isNull(query)) {
            throw new BusinessException(OrderErrorCodeEnum.DRIVER_ORDER_QUERY_PARAMETER_ERROR, ORDER_QUERY_IS_NULL_MESSAGE);
        }

        // dealStartTime表示查询日期当天00:00:00，作为成交时间左闭区间起点。
        LocalDateTime dealStartTime = query.getQueryDate().atStartOfDay();

        // dealEndTime表示下一天00:00:00，作为成交时间右开区间终点。
        LocalDateTime dealEndTime = dealStartTime.plusDays(1);

        // Repository只接收明确的查询条件，不接收Application层DriverOrderQuery对象，避免Domain端口反向依赖Application查询模型。
        List<ColdChainOrder> coldChainOrderList = coldChainOrderRepository.listDriverDealOrderList(
                query.getTenantId(), query.getDriverId(), dealStartTime, dealEndTime, query.getMaxResultCount());

        if (CollectionUtils.isEmpty(coldChainOrderList)) {
            // 没有成交订单属于正常查询结果，返回空列表而不是返回null或抛出异常。
            return List.of();
        }

        // 过滤异常空元素后，将领域对象转换成Application层订单摘要。
        return coldChainOrderList.stream()
                .filter(Objects::nonNull)
                .map(this::convertToSummaryDTO)
                .toList();
    }

    /**
     * 将冷运订单领域对象转换成查询摘要DTO。
     *
     * @param coldChainOrder 冷运订单领域对象
     * @return 司机成交订单摘要
     */
    private DriverOrderSummaryDTO convertToSummaryDTO(ColdChainOrder coldChainOrder) {
        return DriverOrderSummaryDTO.of(
                coldChainOrder.getOrderNo(),
                coldChainOrder.getPickupCity(),
                coldChainOrder.getDeliveryCity(),
                coldChainOrder.getDealTime(),
                coldChainOrder.getOrderStatus().getCode(),
                coldChainOrder.getOrderStatus().getDescription());
    }
}
