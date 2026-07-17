package com.ymm.coldchainai.shared.exception;

import com.ymm.coldchainai.shared.exception.code.CommonErrorCodeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BusinessException 单元测试。
 *
 * <p>该测试重点验证错误码枚举、异常构造器链和默认值兜底逻辑，
 * 不需要启动Spring容器。</p>
 */
class BusinessExceptionTest {

    /**
     * 测试单参数构造方法会自动使用公共默认业务错误码。
     */
    @Test
    void shouldUseDefaultErrorCodeWhenOnlyMessageIsProvided() {
        // 指定业务提示信息，用于验证单参数构造方法使用默认BUSINESS_ERROR。
        String expectedMessage = "模型问题不能为空";

        BusinessException businessException = new BusinessException(expectedMessage);

        assertEquals(CommonErrorCodeEnum.BUSINESS_ERROR.getCode(), businessException.getCode());
        assertEquals(expectedMessage, businessException.getMessage());
    }

    /**
     * 测试错误码和提示信息为空时使用公共默认业务错误。
     */
    @Test
    void shouldUseDefaultValuesWhenErrorCodeAndMessageAreBlank() {
        // 传入空错误码和空白信息，验证异常对象仍然包含有效编码和提示信息。
        BusinessException businessException = new BusinessException(null, " ");

        assertEquals(CommonErrorCodeEnum.BUSINESS_ERROR.getCode(), businessException.getCode());
        assertEquals(CommonErrorCodeEnum.BUSINESS_ERROR.getMessage(), businessException.getMessage());
    }

    /**
     * 测试指定错误码和自定义提示信息能够被完整保存。
     */
    @Test
    void shouldKeepSpecifiedErrorCodeAndCustomMessage() {
        // 请求体错误码用于验证BusinessException可以接受任意IErrorCode实现。
        CommonErrorCodeEnum errorCode = CommonErrorCodeEnum.REQUEST_BODY_ERROR;

        // 自定义提示用于验证具体业务场景可以覆盖枚举默认提示。
        String expectedMessage = "请求体缺少question字段";

        BusinessException businessException = new BusinessException(errorCode, expectedMessage);

        assertEquals(errorCode.getCode(), businessException.getCode());
        assertEquals(expectedMessage, businessException.getMessage());
    }
}