package com.ymm.coldchainai.payment.infrastructure.persistence.converter;

import com.ymm.coldchainai.payment.application.enumtype.PaymentErrorCodeEnum;
import com.ymm.coldchainai.payment.domain.enumtype.DepositPayStatusEnum;
import com.ymm.coldchainai.payment.domain.model.ColdChainDepositPayOrder;
import com.ymm.coldchainai.payment.infrastructure.persistence.dataobject.ColdChainDepositPayOrderDO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 冷运定金支付单持久化对象转换器。
 *
 * <p>该转换器负责把数据库DO恢复成经过支付业务规则校验的领域对象，防止非法金额、未知支付状态或缺失成功时间的数据进入Application层。</p>
 *
 * <p>在挖矿流程中，该组件相当于财务档案翻译员和验单员。它不仅翻译账本字段，还会检查收款单是否合法；缺少这一步，
 * Agent可能把数据库脏数据当成真实支付结论回答给用户。</p>
 */
@Component
public class ColdChainDepositPayOrderConverter {

    /**
     * 将定金支付单数据库对象转换成领域对象。
     *
     * @param depositPayOrderDO 定金支付单数据库对象
     * @return 经过支付规则校验的定金支付单领域对象
     */
    public ColdChainDepositPayOrder convertToDomain(ColdChainDepositPayOrderDO depositPayOrderDO) {
        if (Objects.isNull(depositPayOrderDO)) {
            throw createPayOrderDataException("定金支付单数据库对象不能为空", null);
        }

        try {
            // 数据库只保存支付状态编码，进入领域层前必须转换成明确的状态枚举。
            DepositPayStatusEnum payStatus = DepositPayStatusEnum.getByCode(depositPayOrderDO.getPayStatus());

            // restore会统一校验支付单主键、租户、金额、时间和支付成功状态。Converter不能直接构造字段对象绕过领域规则。
            return ColdChainDepositPayOrder.restore(
                    depositPayOrderDO.getId(),
                    depositPayOrderDO.getTenantId(),
                    depositPayOrderDO.getPayOrderNo(),
                    depositPayOrderDO.getOrderNo(),
                    depositPayOrderDO.getDriverId(),
                    depositPayOrderDO.getDepositAmountCent(),
                    payStatus,
                    depositPayOrderDO.getCreateTime(),
                    depositPayOrderDO.getPayExpireTime(),
                    depositPayOrderDO.getPaidTime(),
                    depositPayOrderDO.getFailureReason());
        } catch (IllegalArgumentException exception) {
            // payOrderNo只用于定位异常数据，空值时使用unknown避免异常处理逻辑再次失败。
            String payOrderNo = StringUtils.defaultIfBlank(depositPayOrderDO.getPayOrderNo(), "unknown");
            throw createPayOrderDataException("支付单数据无法恢复成领域对象，payOrderNo=%s".formatted(payOrderNo), exception);
        }
    }

    /**
     * 创建定金支付单数据异常。
     *
     * @param detailMessage 具体数据问题
     * @param cause 导致转换失败的原始异常，可以为空
     * @return 包含支付模块统一错误信息的系统异常
     */
    private IllegalStateException createPayOrderDataException(String detailMessage, Throwable cause) {
        String errorMessage = "%s：%s".formatted(PaymentErrorCodeEnum.DEPOSIT_PAY_ORDER_DATA_ERROR.getMessage(), detailMessage);

        if (Objects.isNull(cause)) {
            return new IllegalStateException(errorMessage);
        }

        return new IllegalStateException(errorMessage, cause);
    }
}
