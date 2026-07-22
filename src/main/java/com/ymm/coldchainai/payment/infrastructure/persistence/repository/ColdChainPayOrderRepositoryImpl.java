package com.ymm.coldchainai.payment.infrastructure.persistence.repository;

import com.ymm.coldchainai.payment.domain.model.ColdChainDepositPayOrder;
import com.ymm.coldchainai.payment.domain.repository.IColdChainPayOrderRepository;
import com.ymm.coldchainai.payment.infrastructure.persistence.converter.ColdChainDepositPayOrderConverter;
import com.ymm.coldchainai.payment.infrastructure.persistence.dataobject.ColdChainDepositPayOrderDO;
import com.ymm.coldchainai.payment.infrastructure.persistence.mapper.IColdChainPayOrderMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/**
 * 基于MyBatis的冷运定金支付单仓储实现。
 *
 * <p>该类负责调用支付单Mapper，并把数据库对象转换成支付领域对象。
 * 对上实现IColdChainPayOrderRepository，对下依赖MyBatis基础设施。</p>
 *
 * <p>在挖矿流程中，该Repository相当于财务档案仓库主管：
 * 它接收规范查询申请，安排档案员查账，再让验单员检查支付记录。
 * 如果Application直接调用Mapper，就会越过仓储规范直接操作财务账本。</p>
 */
@Repository
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ColdChainPayOrderRepositoryImpl implements IColdChainPayOrderRepository {

    /**
     * 冷运定金支付单MyBatis Mapper。
     */
    private final IColdChainPayOrderMapper coldChainPayOrderMapper;

    /**
     * 定金支付单持久化对象转换器。
     */
    private final ColdChainDepositPayOrderConverter coldChainDepositPayOrderConverter;

    /**
     * 查询指定业务订单最新创建的一笔定金支付单。
     *
     * @param tenantId 当前查询所属租户ID
     * @param orderNo 冷运业务订单号
     * @return 最新定金支付单，不存在时返回Optional.empty()
     */
    @Override
    public Optional<ColdChainDepositPayOrder> findLatestDepositPayOrder(Long tenantId, String orderNo) {
        validateQueryParameters(tenantId, orderNo);

        // Mapper返回null表示当前租户和业务订单尚未创建定金支付单。
        ColdChainDepositPayOrderDO depositPayOrderDO = coldChainPayOrderMapper.selectLatestDepositPayOrder(tenantId, orderNo);

        if (Objects.isNull(depositPayOrderDO)) {
            return Optional.empty();
        }

        // 查询到数据后必须先转换并校验，不能把数据库DO直接返回给Application层。
        ColdChainDepositPayOrder depositPayOrder = coldChainDepositPayOrderConverter.convertToDomain(depositPayOrderDO);

        return Optional.of(depositPayOrder);
    }

    /**
     * 防御性校验支付单Repository查询参数。
     *
     * <p>正常情况下OrderDepositQuery已经完成业务校验，
     * Repository仍然进行基础防御，防止其他调用入口绕过Application查询模型。</p>
     *
     * @param tenantId 当前查询所属租户ID
     * @param orderNo 冷运业务订单号
     */
    private void validateQueryParameters(Long tenantId, String orderNo) {
        if (Objects.isNull(tenantId) || tenantId <= 0L) {
            throw new IllegalArgumentException("定金支付单Repository查询的tenantId必须大于0");
        }

        if (StringUtils.isBlank(orderNo)) {
            throw new IllegalArgumentException("定金支付单Repository查询的orderNo不能为空");
        }
    }
}
