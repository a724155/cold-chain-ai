package com.ymm.coldchainai.rag.knowledge.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 内部规范RAG的PGVector配置。
 *
 * <p>该配置专门描述阿里云RDS PostgreSQL连接信息以及PGVector存储参数，
 * 与现有spring.datasource业务MySQL配置完全独立。</p>
 *
 * <p>在挖矿流程中，该配置相当于地质资料仓库的地址和仓储规格：
 * 它告诉系统向量档案库在哪里、使用哪间仓库、每份特征指纹有多少维。
 * 业务订单账本仍然留在原MySQL档案室，两者不能混用。</p>
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "cold-chain-ai.rag.pgvector")
public class InternalRulePgVectorProperties {

    /**
     * 阿里云RDS PostgreSQL完整JDBC地址。
     *
     * <p>当前local环境最终连接cold_chain_ai_rag数据库，
     * 不能连接默认postgres数据库。</p>
     */
    @NotBlank
    private String jdbcUrl;

    /**
     * RAG PostgreSQL数据库账号。
     */
    @NotBlank
    private String username;

    /**
     * RAG PostgreSQL数据库密码。
     *
     * <p>只允许从环境变量注入，禁止把真实密码提交到Git。</p>
     */
    @NotBlank
    private String password;

    /**
     * PGVector表所在PostgreSQL Schema。
     */
    @NotBlank
    private String schemaName;

    /**
     * 满帮内部规范使用的向量表名称。
     */
    @NotBlank
    private String tableName;

    /**
     * Embedding向量维度。
     *
     * <p>必须与text-embedding-v4真实输出维度完全一致。</p>
     */
    @NotNull
    @Min(1)
    private Integer dimensions;

    /**
     * 是否允许Spring AI初始化PGVector所需表结构。
     */
    @NotNull
    private Boolean initializeSchema;

    /**
     * 单批最多处理的Document数量。
     */
    @NotNull
    @Min(1)
    private Integer maxDocumentBatchSize;
}
