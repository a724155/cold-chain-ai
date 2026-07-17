package com.ymm.coldchainai.shared.exception.code;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 冷运 AI 系统公共错误码枚举。
 *
 * <p>该枚举只管理所有业务模块都可能使用的公共错误，
 * 例如参数校验、请求格式、HTTP方法和未知系统异常。</p>
 *
 * <p>订单、支付、知识库等模块的专属错误码不能全部堆放在该枚举中，
 * 应当由对应业务模块建立自己的错误码枚举。</p>
 */
@Getter
@AllArgsConstructor
public enum CommonErrorCodeEnum implements IErrorCode {

    /**
     * 请求参数未通过 Bean Validation 校验。
     */
    PARAMETER_VALIDATION_ERROR(40000, "请求参数校验失败"),

    /**
     * 默认业务处理失败。
     */
    BUSINESS_ERROR(40001, "业务处理失败"),

    /**
     * 请求体为空或 JSON 格式错误。
     */
    REQUEST_BODY_ERROR(40002, "请求体格式错误，请检查 JSON 格式"),

    /**
     * 当前接口不支持调用方使用的 HTTP 请求方法。
     */
    REQUEST_METHOD_NOT_SUPPORTED(40500, "当前请求方法不支持"),

    /**
     * 当前接口不支持调用方提交的 Content-Type。
     */
    MEDIA_TYPE_NOT_SUPPORTED(41500, "请求 Content-Type 不支持，请使用 application/json"),

    /**
     * 未被其他错误类型覆盖的未知系统异常。
     */
    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试");

    /**
     * 业务错误编码。
     */
    private final Integer code;

    /**
     * 默认提示信息。
     */
    private final String message;
}
