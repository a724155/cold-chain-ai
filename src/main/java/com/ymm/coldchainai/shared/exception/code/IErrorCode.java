package com.ymm.coldchainai.shared.exception.code;

/**
 * 冷运 AI 系统统一错误码接口。
 *
 * <p>公共错误码、Agent错误码、订单错误码、支付错误码等枚举都需要实现该接口，
 * 使统一返回对象和异常处理器不依赖某一个具体业务枚举。</p>
 */
public interface IErrorCode {

    /**
     * 获取业务错误编码。
     *
     * @return 业务错误编码
     */
    Integer getCode();

    /**
     * 获取错误码对应的默认提示信息。
     *
     * @return 默认提示信息
     */
    String getMessage();
}
