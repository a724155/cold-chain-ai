package com.ymm.coldchainai.order.infrastructure.persistence.repository;

import com.ymm.coldchainai.order.domain.model.ColdChainOrder;
import com.ymm.coldchainai.order.domain.repository.IColdChainOrderRepository;
import com.ymm.coldchainai.order.infrastructure.persistence.converter.ColdChainOrderConverter;
import com.ymm.coldchainai.order.infrastructure.persistence.dataobject.ColdChainOrderDO;
import com.ymm.coldchainai.order.infrastructure.persistence.mapper.IColdChainOrderMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 基于MyBatis的冷运订单仓储实现。
 *
 * <p>该类负责调用订单Mapper并把数据库对象转换成订单领域对象，对上实现IColdChainOrderRepository，对下依赖MyBatis基础设施。</p>
 *
 * <p>在挖矿流程中，该Repository相当于矿场档案仓库主管：它接收符合规范的查询申请，安排档案员查询账本，再让转换器检查并整理结果。
 * 如果Application直接调用Mapper，项目经理就会越过仓储规范直接翻数据库账本。</p>
 */
@Repository
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ColdChainOrderRepositoryImpl implements IColdChainOrderRepository {

    /**
     * 冷运订单MyBatis Mapper。
     */
    private final IColdChainOrderMapper coldChainOrderMapper;

    /**
     * 冷运订单持久化对象转换器。
     */
    private final ColdChainOrderConverter coldChainOrderConverter;

    /**
     * 查询司机在指定成交时间范围内的订单。
     *
     * @param tenantId 当前查询所属租户ID
     * @param driverId 待查询司机ID
     * @param dealStartTime 成交时间范围起点，包含
     * @param dealEndTime 成交时间范围终点，不包含
     * @param maxResultCount 最大返回数量
     * @return 满足条件的冷运订单领域对象列表
     */
    @Override
    public List<ColdChainOrder> listDriverDealOrderList(Long tenantId, Long driverId, LocalDateTime dealStartTime,
                                                        LocalDateTime dealEndTime, Integer maxResultCount) {
        validateQueryParameters(tenantId, driverId, dealStartTime, dealEndTime, maxResultCount);

        // Mapper只负责数据库查询，返回的是DO列表。Repository必须完成DO到领域对象的转换，不能把数据库对象泄露给Application层。
        List<ColdChainOrderDO> coldChainOrderDOList = coldChainOrderMapper.selectDriverDealOrderList(
                tenantId, driverId, dealStartTime, dealEndTime, maxResultCount);

        if (CollectionUtils.isEmpty(coldChainOrderDOList)) {
            // 没有订单属于正常查询结果，统一返回空List，禁止返回null。
            return List.of();
        }

        /*
         * 不过滤列表中的null元素。如果Mapper异常返回null元素，Converter会主动抛出数据异常，
         * 避免静默丢失订单后让Agent返回不完整结果。
         */
        return coldChainOrderDOList.stream().map(coldChainOrderConverter::convertToDomain).toList();
    }

    /**
     * 防御性校验Repository查询参数。
     *
     * <p>正常情况下DriverOrderQuery已经完成业务校验，Repository仍然进行基础防御，防止后续其他调用入口绕过Application查询对象。</p>
     *
     * @param tenantId 当前查询所属租户ID
     * @param driverId 待查询司机ID
     * @param dealStartTime 成交时间范围起点
     * @param dealEndTime 成交时间范围终点
     * @param maxResultCount 最大返回数量
     */
    private void validateQueryParameters(Long tenantId, Long driverId, LocalDateTime dealStartTime, LocalDateTime dealEndTime, Integer maxResultCount) {
        if (Objects.isNull(tenantId) || tenantId <= 0L) {
            throw new IllegalArgumentException("订单Repository查询的tenantId必须大于0");
        }

        if (Objects.isNull(driverId) || driverId <= 0L) {
            throw new IllegalArgumentException("订单Repository查询的driverId必须大于0");
        }

        if (Objects.isNull(dealStartTime) || Objects.isNull(dealEndTime)) {
            throw new IllegalArgumentException("订单Repository查询的成交时间范围不能为空");
        }

        if (!dealStartTime.isBefore(dealEndTime)) {
            throw new IllegalArgumentException("订单Repository查询的开始时间必须早于结束时间");
        }

        if (Objects.isNull(maxResultCount) || maxResultCount <= 0) {
            throw new IllegalArgumentException("订单Repository查询的最大返回数量必须大于0");
        }
    }
}
