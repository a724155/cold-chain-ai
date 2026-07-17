package com.ymm.coldchainai.shared.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * YmmResult 单元测试。
 *
 * <p>该测试不启动 Spring 容器，也不连接模型或数据库，
 * 只验证统一返回对象自身的构造规则。</p>
 */
class YmmResultTest {

    /**
     * 成功业务编码。
     */
    private static final Integer EXPECTED_SUCCESS_CODE = 0;

    /**
     * 成功提示信息。
     */
    private static final String EXPECTED_SUCCESS_MESSAGE = "success";

    /**
     * 默认失败业务编码。
     */
    private static final Integer EXPECTED_DEFAULT_FAIL_CODE = 40001;

    /**
     * 默认失败提示信息。
     */
    private static final String EXPECTED_DEFAULT_FAIL_MESSAGE = "操作失败";

    /**
     * 测试携带业务数据的成功结果。
     */
    @Test
    void shouldCreateSuccessResultWithData() {
        // 测试数据用于验证 success(data) 能够原样保存业务返回值。
        String expectedData = "冷运AI模型连接成功";

        // 调用统一成功结果工厂方法创建测试对象。
        YmmResult<String> result = YmmResult.success(expectedData);

        assertEquals(EXPECTED_SUCCESS_CODE, result.getCode());
        assertEquals(EXPECTED_SUCCESS_MESSAGE, result.getMessage());
        assertEquals(expectedData, result.getData());
    }

    /**
     * 测试不携带业务数据的成功结果。
     */
    @Test
    void shouldCreateSuccessResultWithoutData() {
        // 调用无参成功工厂方法，验证没有业务数据时 data 保持为空。
        YmmResult<Void> result = YmmResult.success();

        assertEquals(EXPECTED_SUCCESS_CODE, result.getCode());
        assertEquals(EXPECTED_SUCCESS_MESSAGE, result.getMessage());
        assertNull(result.getData());
    }

    /**
     * 测试失败编码和失败信息为空时的默认兜底规则。
     */
    @Test
    void shouldUseDefaultValuesWhenFailCodeAndMessageAreBlank() {
        // 传入空编码和空白信息，验证 YmmResult 不会产生缺少编码或提示信息的响应。
        YmmResult<Void> result = YmmResult.fail(null, " ");

        assertEquals(EXPECTED_DEFAULT_FAIL_CODE, result.getCode());
        assertEquals(EXPECTED_DEFAULT_FAIL_MESSAGE, result.getMessage());
        assertNull(result.getData());
    }
}
