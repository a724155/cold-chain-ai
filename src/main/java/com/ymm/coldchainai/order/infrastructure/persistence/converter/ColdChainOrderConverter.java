package com.ymm.coldchainai.order.infrastructure.persistence.converter;

import com.ymm.coldchainai.order.application.enumtype.OrderErrorCodeEnum;
import com.ymm.coldchainai.order.domain.enumtype.OrderStatusEnum;
import com.ymm.coldchainai.order.domain.model.ColdChainOrder;
import com.ymm.coldchainai.order.infrastructure.persistence.dataobject.ColdChainOrderDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 冷运订单持久化对象转换器。
 *
 * <p>该转换器负责把数据库DO恢复成经过业务规则校验的ColdChainOrder领域对象，防止数据库中的异常状态、空订单号或错误司机ID直接流入Application层。</p>
 *
 * <p>在挖矿流程中，该组件相当于档案翻译员和质量检查员：它把仓库登记表翻译成业务专家能够理解的订单，同时检查登记内容是否合格。
 * 如果缺少这一步，错误数据库数据可能直接被Agent当作真实业务事实回答给用户。</p>
 */
@Component
public class ColdChainOrderConverter {

    /**
     * 将冷运订单数据库对象转换成领域对象。
     *
     * @param coldChainOrderDO 冷运订单数据库对象
     * @return 经过必要字段校验的冷运订单领域对象
     */
    public ColdChainOrder convertToDomain(ColdChainOrderDO coldChainOrderDO) {
        if (Objects.isNull(coldChainOrderDO)) {
            throw createOrderDataException("冷运订单数据库对象不能为空", null);
        }

        try {
            // 数据库只保存状态编码，进入领域层之前必须转换成明确的订单状态枚举。
            OrderStatusEnum orderStatus = OrderStatusEnum.getByCode(coldChainOrderDO.getOrderStatus());

            // restore会再次校验主键、租户、订单号、司机、城市、状态和成交时间。Converter不能直接使用普通构造方法绕过领域对象的合法性检查。
            return ColdChainOrder.restore(
                    coldChainOrderDO.getId(),
                    coldChainOrderDO.getTenantId(),
                    coldChainOrderDO.getOrderNo(),
                    coldChainOrderDO.getDriverId(),
                    coldChainOrderDO.getPickupCity(),
                    coldChainOrderDO.getDeliveryCity(),
                    orderStatus,
                    coldChainOrderDO.getDealTime());
        } catch (IllegalArgumentException exception) {
            // orderNo只用于定位异常数据，空值时使用unknown避免异常处理逻辑自身再次出错。
            String orderNo = StringUtils.defaultIfBlank(coldChainOrderDO.getOrderNo(), "unknown");
            throw createOrderDataException("订单数据无法恢复成领域对象，orderNo=%s".formatted(orderNo), exception);
        }
    }

    /**
     * 创建订单数据异常。
     *
     * @param detailMessage 具体数据问题
     * @param cause 导致转换失败的原始异常，可以为空
     * @return 包含订单模块统一错误信息的系统异常
     */
    private IllegalStateException createOrderDataException(String detailMessage, Throwable cause) {
        String errorMessage = "%s：%s".formatted(OrderErrorCodeEnum.ORDER_DATA_ERROR.getMessage(), detailMessage);

        if (Objects.isNull(cause)) {
            return new IllegalStateException(errorMessage);
        }

        return new IllegalStateException(errorMessage, cause);
    }
}
