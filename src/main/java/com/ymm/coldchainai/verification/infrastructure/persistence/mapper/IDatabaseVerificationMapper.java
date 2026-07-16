package com.ymm.coldchainai.verification.infrastructure.persistence.mapper;

/**
 * 数据库连通性验证 Mapper。
 *
 * <p>该 Mapper 只用于第一阶段验证 MyBatis、DataSource 和 MySQL 是否真正连通，
 * 不负责任何订单、支付或 Agent 业务数据访问。</p>
 */
public interface IDatabaseVerificationMapper {

    /**
     * 执行最小数据库查询并返回固定数值。
     *
     * <p>该方法对应 DatabaseVerificationMapper.xml 中的 SELECT 1。
     * 如果能够返回整数 1，说明 Mapper 扫描、XML 加载、数据库连接和 SQL 执行链路均正常。</p>
     *
     * @return 数据库返回的固定整数 1
     */
    Integer selectDatabaseConnectionValue();
}
