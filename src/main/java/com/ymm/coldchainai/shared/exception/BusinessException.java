package com.ymm.coldchainai.shared.exception;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.util.Objects;

/**
 * 冷运 AI 系统业务异常。
 *
 * <p>该异常用于表示可预期、可明确告知调用方的业务失败，
 * 例如参数不符合业务要求、订单状态不允许操作或当前用户没有权限。</p>
 *
 * <p>业务代码可以抛出该异常，由 GlobalExceptionHandler 统一捕获并转换成 YmmResult，
 * 因此抛出 BusinessException 不会导致整个 Spring Boot 程序崩溃。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * Java 序列化版本号，用于保证异常对象序列化时的版本兼容性。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认业务失败编码。
     */
    private static final Integer DEFAULT_BUSINESS_ERROR_CODE = 40001;

    /**
     * 默认业务失败信息。
     */
    private static final String DEFAULT_BUSINESS_ERROR_MESSAGE = "业务处理失败";

    /**
     * 业务失败编码。
     */
    private final Integer code;

    /**
     * 使用指定业务编码和提示信息创建业务异常。
     *
     * @param code 业务失败编码
     * @param message 可以返回给调用方的业务提示信息
     */
    public BusinessException(Integer code, String message) {
        // RuntimeException 保存异常信息，日志和全局异常处理器都可以通过 getMessage() 获取。
        super(StringUtils.defaultIfBlank(message, DEFAULT_BUSINESS_ERROR_MESSAGE));

        // 业务编码为空时使用默认编码，避免异常响应缺少必要字段。
        this.code = Objects.isNull(code) ? DEFAULT_BUSINESS_ERROR_CODE : code;
    }

    /**
     * 使用默认业务编码和指定提示信息创建业务异常。
     *
     * @param message 可以返回给调用方的业务提示信息
     */
    public BusinessException(String message) {
        this(DEFAULT_BUSINESS_ERROR_CODE, message);
    }
}
