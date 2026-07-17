package com.ymm.coldchainai.shared.exception;

import com.ymm.coldchainai.shared.exception.code.CommonErrorCodeEnum;
import com.ymm.coldchainai.shared.exception.code.IErrorCode;
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
     * 业务失败编码。
     *
     * <p>具体编码来自实现 IErrorCode 的错误码枚举，
     * BusinessException 自身不再维护散落的数字常量。</p>
     */
    private final Integer code;

    /**
     * 使用统一错误码创建业务异常。
     *
     * @param errorCode 统一错误码，包含业务编码和默认提示信息
     */
    public BusinessException(IErrorCode errorCode) {
        // 默认使用错误码枚举中定义的提示信息。
        this(errorCode, Objects.isNull(errorCode) ? null : errorCode.getMessage());
    }

    /**
     * 使用统一错误码和自定义提示信息创建业务异常。
     *
     * <p>错误编码由枚举统一管理，具体提示信息可以根据当前业务场景覆盖。
     * 例如同样属于Agent参数错误，可以分别提示命令为空或问题为空。</p>
     *
     * @param errorCode 统一错误码
     * @param message 可以返回给调用方的具体业务提示信息
     */
    public BusinessException(IErrorCode errorCode, String message) {
        // RuntimeException保存最终提示信息，日志和全局异常处理器可以通过getMessage()获取。
        super(resolveMessage(errorCode, message));

        // 错误码对象为空时使用公共默认业务失败编码，避免异常响应缺少业务编码。
        this.code = resolveCode(errorCode);
    }

    /**
     * 使用公共默认业务错误码和指定提示信息创建业务异常。
     *
     * @param message 可以返回给调用方的业务提示信息
     */
    public BusinessException(String message) {
        // 未明确指定业务错误码时，统一使用公共BUSINESS_ERROR。
        this(CommonErrorCodeEnum.BUSINESS_ERROR, message);
    }

    /**
     * 安全解析业务错误编码。
     *
     * @param errorCode 统一错误码
     * @return 有效业务错误编码
     */
    private static Integer resolveCode(IErrorCode errorCode) {
        if (Objects.isNull(errorCode)) {
            return CommonErrorCodeEnum.BUSINESS_ERROR.getCode();
        }

        return errorCode.getCode();
    }

    /**
     * 安全解析业务异常提示信息。
     *
     * @param errorCode 统一错误码
     * @param message 本次业务异常的自定义提示信息
     * @return 最终写入RuntimeException的提示信息
     */
    private static String resolveMessage(IErrorCode errorCode, String message) {
        // 错误码为空时使用公共默认业务失败信息作为兜底。
        String defaultMessage = Objects.isNull(errorCode) ? CommonErrorCodeEnum.BUSINESS_ERROR.getMessage() : errorCode.getMessage();

        return StringUtils.defaultIfBlank(message, defaultMessage);
    }
}
