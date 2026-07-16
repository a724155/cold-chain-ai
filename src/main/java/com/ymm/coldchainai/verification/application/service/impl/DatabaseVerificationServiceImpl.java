package com.ymm.coldchainai.verification.application.service.impl;

import com.ymm.coldchainai.verification.application.service.IDatabaseVerificationService;
import com.ymm.coldchainai.verification.infrastructure.persistence.mapper.IDatabaseVerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 数据库连通性验证服务实现。
 *
 * <p>该实现通过 MyBatis Mapper 执行 SELECT 1，
 * 验证 Mapper 扫描、Mapper XML 加载、DataSource 和 MySQL 查询是否正常。</p>
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DatabaseVerificationServiceImpl implements IDatabaseVerificationService {

    /**
     * 数据库连通性查询的预期返回值。
     */
    private static final Integer EXPECTED_DATABASE_CONNECTION_VALUE = 1;

    /**
     * 数据库返回结果不符合预期时使用的系统异常信息。
     */
    private static final String DATABASE_CONNECTION_RESULT_ERROR_MESSAGE = "数据库连通性验证结果不符合预期";

    /**
     * 数据库连通性验证 Mapper。
     *
     * <p>该对象不是手写实现类，而是 MyBatis 根据 Mapper 接口和 XML 动态创建的代理对象。</p>
     */
    private final IDatabaseVerificationMapper databaseVerificationMapper;

    /**
     * 验证数据库连接和最小 SQL 查询是否正常。
     *
     * @return 数据库返回的验证数值，正常情况下固定为 1
     */
    @Override
    public Integer verifyDatabaseConnection() {
        // 通过 MyBatis Mapper 执行 XML 中定义的 SELECT 1，真正触发一次 MySQL 数据库连接和查询。
        Integer databaseConnectionValue = databaseVerificationMapper.selectDatabaseConnectionValue();

        if (!Objects.equals(EXPECTED_DATABASE_CONNECTION_VALUE, databaseConnectionValue)) {
            /*
             * SELECT 1 正常情况下必须返回整数 1。
             * 如果返回空值或其他数值，说明数据库驱动、MyBatis映射或返回值转换出现非预期状态，
             * 因此抛出系统异常，由全局异常处理器记录完整堆栈并返回 requestId。
             */
            throw new IllegalStateException(DATABASE_CONNECTION_RESULT_ERROR_MESSAGE);
        }

        return databaseConnectionValue;
    }
}
