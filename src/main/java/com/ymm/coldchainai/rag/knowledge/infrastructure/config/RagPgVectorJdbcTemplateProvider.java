package com.ymm.coldchainai.rag.knowledge.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * RAG专用PostgreSQL JdbcTemplate提供器。
 *
 * <p>该组件内部创建并持有一套独立Hikari连接池，专门连接阿里云cold_chain_ai_rag数据库。</p>
 *
 * <p><strong>为什么不直接声明第二个DataSource Bean：</strong>
 * 当前项目的业务MySQL依赖Spring Boot根据spring.datasource自动配置。
 * 如果这里直接向Spring容器注册新的DataSource Bean，可能影响默认MySQL数据源自动配置。
 * 因此RAG连接池由本组件内部管理，只向上提供JdbcTemplate。</p>
 *
 * <p>在挖矿流程中，现有MySQL相当于业务账本档案室；
 * 当前组件则单独建立了一条通往“地质向量档案库”的专线。
 * 两条线路独立，RAG数据库故障不能改变业务MySQL的连接配置。</p>
 */
@Slf4j
@Component
public class RagPgVectorJdbcTemplateProvider {

    /**
     * RAG PostgreSQL连接池最大连接数。
     *
     * <p>当前仅用于本地RAG学习和少量知识检索，不需要建立大量数据库连接。</p>
     */
    private static final Integer MAXIMUM_POOL_SIZE = 5;

    /**
     * RAG PostgreSQL连接池最小空闲连接数。
     */
    private static final Integer MINIMUM_IDLE = 1;

    /**
     * 获取数据库连接允许等待的最大时间，单位毫秒。
     */
    private static final Long CONNECTION_TIMEOUT_MILLIS = 10_000L;

    /**
     * RAG PostgreSQL Hikari连接池。
     *
     * <p>它不是Spring DataSource Bean，因此不会参与业务MySQL自动配置竞争。</p>
     */
    private final HikariDataSource ragPgVectorDataSource;

    /**
     * PGVector使用的JdbcTemplate。
     */
    private final JdbcTemplate ragPgVectorJdbcTemplate;

    /**
     * 创建RAG专用PostgreSQL连接池和JdbcTemplate。
     *
     * <p>这里需要根据配置主动创建HikariDataSource，因此存在明确构造逻辑，不使用Lombok自动生成构造方法。</p>
     *
     * @param pgVectorProperties 阿里云PGVector配置
     */
    @Autowired
    public RagPgVectorJdbcTemplateProvider(InternalRulePgVectorProperties pgVectorProperties) {
        // 创建完全独立于业务MySQL的RAG PostgreSQL连接池。
        HikariDataSource hikariDataSource = new HikariDataSource();

        // JDBC地址已经明确包含cold_chain_ai_rag数据库名称，不能连接默认postgres数据库。
        hikariDataSource.setJdbcUrl(pgVectorProperties.getJdbcUrl());

        // RAG数据库账号来自IDEA环境变量，不从代码中写死。
        hikariDataSource.setUsername(pgVectorProperties.getUsername());

        // 数据库密码同样只来自环境变量，日志中禁止输出该值。
        hikariDataSource.setPassword(pgVectorProperties.getPassword());

        // 给连接池设置稳定名称，后续日志、JMX和线上指标能够明确区分MySQL与RAG PostgreSQL。
        hikariDataSource.setPoolName("ColdChainAiRagPgVectorPool");

        // 当前RAG规模很小，限制最大连接数避免学习环境无意义占用阿里云RDS连接。
        hikariDataSource.setMaximumPoolSize(MAXIMUM_POOL_SIZE);

        // 始终保留一个空闲连接即可满足当前本地开发请求。
        hikariDataSource.setMinimumIdle(MINIMUM_IDLE);

        // 阿里云网络异常时最多等待10秒获得连接，避免请求无限期阻塞。
        hikariDataSource.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);

        this.ragPgVectorDataSource = hikariDataSource;

        // PgVectorStore后续只依赖JdbcTemplate，不直接操作Hikari连接池。
        this.ragPgVectorJdbcTemplate = new JdbcTemplate(hikariDataSource);

        log.info("RAG PostgreSQL连接池初始化完成，poolName={}，jdbcUrl={}", hikariDataSource.getPoolName(), pgVectorProperties.getJdbcUrl());
    }

    /**
     * 获取PGVector专用JdbcTemplate。
     *
     * @return 只连接阿里云RAG PostgreSQL的JdbcTemplate
     */
    public JdbcTemplate getJdbcTemplate() {
        return ragPgVectorJdbcTemplate;
    }

    /**
     * Spring容器关闭时主动释放RAG PostgreSQL连接池。
     *
     * <p>如果不关闭连接池，DevTools重启或者应用退出时可能残留数据库连接。</p>
     */
    @PreDestroy
    public void close() {
        if (!ragPgVectorDataSource.isClosed()) {
            // 主动释放所有RAG PostgreSQL连接。
            ragPgVectorDataSource.close();

            log.info("RAG PostgreSQL连接池已关闭，poolName={}", ragPgVectorDataSource.getPoolName());
        }
    }
}
