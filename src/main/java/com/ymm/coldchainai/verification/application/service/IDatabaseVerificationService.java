package com.ymm.coldchainai.verification.application.service;

/**
 * 数据库连通性验证服务。
 *
 * <p>该服务用于第一阶段验证 Spring Boot、MyBatis 和 MySQL 的完整调用链，
 * Controller 不直接调用 Mapper，仍然通过 Application Service 完成用例编排。</p>
 */
public interface IDatabaseVerificationService {

    /**
     * 验证数据库连接和最小 SQL 查询是否正常。
     *
     * @return 数据库返回的验证数值，正常情况下固定为 1
     */
    Integer verifyDatabaseConnection();
}