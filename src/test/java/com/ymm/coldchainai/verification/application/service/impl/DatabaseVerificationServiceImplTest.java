package com.ymm.coldchainai.verification.application.service.impl;

import com.ymm.coldchainai.verification.infrastructure.persistence.mapper.IDatabaseVerificationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据库连通性验证服务单元测试。
 *
 * <p>该测试使用 Mockito 模拟 Mapper，不连接真实 MySQL，
 * 重点验证 Application Service 对 Mapper 返回结果的处理逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
class DatabaseVerificationServiceImplTest {

    /**
     * 数据库验证查询的预期返回值。
     */
    private static final Integer EXPECTED_DATABASE_CONNECTION_VALUE = 1;

    /**
     * 数据库验证结果异常时的预期错误信息。
     */
    private static final String EXPECTED_RESULT_ERROR_MESSAGE = "数据库连通性验证结果不符合预期";

    /**
     * 模拟 MyBatis Mapper，测试期间不会执行真实 SQL。
     */
    @Mock
    private IDatabaseVerificationMapper databaseVerificationMapper;

    /**
     * 将模拟 Mapper 通过构造器注入被测试的 Application Service。
     */
    @InjectMocks
    private DatabaseVerificationServiceImpl databaseVerificationService;

    /**
     * 测试 Mapper 返回 1 时数据库验证成功。
     */
    @Test
    void shouldReturnOneWhenMapperReturnsExpectedValue() {
        // 预设 Mapper 执行查询后返回整数 1，不会访问真实数据库。
        when(databaseVerificationMapper.selectDatabaseConnectionValue()).thenReturn(EXPECTED_DATABASE_CONNECTION_VALUE);

        // 调用被测试服务，验证 Application Service 能够正确处理正常查询结果。
        Integer databaseConnectionValue = databaseVerificationService.verifyDatabaseConnection();

        assertEquals(EXPECTED_DATABASE_CONNECTION_VALUE, databaseConnectionValue);

        // 验证 Mapper 方法只被调用一次，防止业务代码产生重复数据库查询。
        verify(databaseVerificationMapper, times(1)).selectDatabaseConnectionValue();
    }

    /**
     * 测试 Mapper 返回空值时抛出系统异常。
     */
    @Test
    void shouldThrowIllegalStateExceptionWhenMapperReturnsNull() {
        // 模拟数据库驱动、映射或结果转换异常导致 Mapper 返回空值。
        when(databaseVerificationMapper.selectDatabaseConnectionValue()).thenReturn(null);

        // 捕获异常并验证异常类型和提示信息符合系统异常设计。
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> databaseVerificationService.verifyDatabaseConnection());

        assertEquals(EXPECTED_RESULT_ERROR_MESSAGE, exception.getMessage());

        // 即使结果异常，Mapper 查询也应该只执行一次。
        verify(databaseVerificationMapper, times(1)).selectDatabaseConnectionValue();
    }
}
