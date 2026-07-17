package com.ymm.coldchainai.shared.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BusinessException 单元测试。
 *
 * <p>该测试重点验证异常构造器链和默认值兜底逻辑，
 * 不需要启动 Spring 容器。</p>
 */
class BusinessExceptionTest {

    /**
     * 默认业务失败编码。
     */
    private static final Integer EXPECTED_DEFAULT_BUSINESS_ERROR_CODE = 40001;

    /**
     * 默认业务失败信息。
     */
    private static final String EXPECTED_DEFAULT_BUSINESS_ERROR_MESSAGE = "业务处理失败";

    /**
     * 测试单参数构造方法会自动使用默认业务编码。
     */
    @Test
    void shouldUseDefaultCodeWhenOnlyMessageIsProvided() {
        // 指定业务提示信息，用于验证单参数构造方法向双参数构造方法传递默认编码。
        String expectedMessage = "模型问题不能为空";

        // 单参数构造方法内部会通过 this(...) 调用双参数构造方法。
        BusinessException businessException = new BusinessException(expectedMessage);

        assertEquals(EXPECTED_DEFAULT_BUSINESS_ERROR_CODE, businessException.getCode());
        assertEquals(expectedMessage, businessException.getMessage());
    }

    /**
     * 测试业务编码和提示信息为空时的默认值兜底规则。
     */
    @Test
    void shouldUseDefaultValuesWhenCodeAndMessageAreBlank() {
        // 传入空编码和空白信息，验证异常对象仍然包含有效业务编码和提示信息。
        BusinessException businessException = new BusinessException(null, " ");

        assertEquals(EXPECTED_DEFAULT_BUSINESS_ERROR_CODE, businessException.getCode());
        assertEquals(EXPECTED_DEFAULT_BUSINESS_ERROR_MESSAGE, businessException.getMessage());
    }

    /**
     * 测试指定业务编码和提示信息能够被完整保存。
     */
    @Test
    void shouldKeepSpecifiedCodeAndMessage() {
        // 自定义业务失败编码用于区分不同业务错误类型。
        Integer expectedCode = 40010;

        // 自定义业务提示信息用于返回给前端展示。
        String expectedMessage = "当前用户没有访问权限";

        BusinessException businessException = new BusinessException(expectedCode, expectedMessage);

        assertEquals(expectedCode, businessException.getCode());
        assertEquals(expectedMessage, businessException.getMessage());
    }
}
