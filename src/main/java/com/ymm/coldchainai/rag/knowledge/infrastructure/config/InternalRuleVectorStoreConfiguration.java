package com.ymm.coldchainai.rag.knowledge.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 满帮内部规范PGVector配置。
 *
 * <p>该配置把RAG专用JdbcTemplate、EmbeddingModel和PGVector存储策略组合成
 * internalRuleVectorStore，供后续知识入库和语义检索共同使用。</p>
 *
 * <p>整体流程：
 * Document文本 → EmbeddingModel转换向量 → PgVectorStore保存 → 用户问题向量检索。</p>
 *
 * <p>在挖矿流程中：
 * EmbeddingModel相当于矿石检测设备，把文本Chunk转换成1024维特征指纹；
 * PgVectorStore相当于智能地质档案仓库，不仅保存资料，还保存每份资料的特征，
 * 后续可以根据客户需求找到最相似的知识。</p>
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InternalRuleVectorStoreConfiguration {

    /**
     * RAG PostgreSQL JdbcTemplate提供器。
     *
     * <p>该JdbcTemplate只连接RAG知识库，不连接订单业务MySQL。
     * 订单属于交易数据，知识向量属于AI检索数据，两者职责不同，需要数据库隔离。</p>
     */
    private final RagPgVectorJdbcTemplateProvider ragPgVectorJdbcTemplateProvider;

    /**
     * Spring AI Embedding模型。
     *
     * <p>负责把自然语言转换成数字向量。
     * 例如“司机支付定金规则”和“司机付款要求”虽然文字不同，
     * 但Embedding后可能产生相近向量，方便语义检索。</p>
     */
    private final EmbeddingModel embeddingModel;

    /**
     * 内部规范PGVector配置。
     *
     * <p>包含数据库Schema、表名、向量维度、批量写入数量等运行参数。</p>
     */
    private final InternalRulePgVectorProperties internalRulePgVectorProperties;

    /**
     * 创建满帮内部规范专属VectorStore。
     *
     * <p>VectorStore负责RAG核心两个过程：</p>
     *
     * <p>
     * 1. 知识入库：
     * Document → EmbeddingModel → Vector → PostgreSQL。
     *
     * 2. 知识检索：
     * 用户问题 → EmbeddingModel → 问题向量 → PGVector相似度搜索。
     * </p>
     *
     * <p>在挖矿流程中，相当于建设智能矿石仓库：
     * 先把原始矿石检测成特征数据，再按照特征存入仓库，
     * 后续客户寻找类似矿石时，可以快速定位。</p>
     *
     * @return 满帮内部规范专属VectorStore
     */
    @Bean(name = "internalRuleVectorStore")
    public VectorStore internalRuleVectorStore() {
        /*
         * 创建PGVector存储组件。
         * JdbcTemplate决定向哪个PostgreSQL数据库保存向量；EmbeddingModel决定使用哪个模型生成向量。
         * 两者组合后，VectorStore才具备“文本转向量并保存”的完整能力。
         */
        return PgVectorStore.builder(ragPgVectorJdbcTemplateProvider.getJdbcTemplate(), embeddingModel)
                // 向量维度必须和Embedding模型输出一致，否则数据库无法正确存储。
                .dimensions(internalRulePgVectorProperties.getDimensions())

                /*
                 * 使用余弦距离计算向量相似度。
                 * 例如：“司机支付规则”和“司机付款要求”文字不同，但语义接近，余弦距离可以判断二者相关程度。
                 */
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)

                // HNSW索引用于提升大量向量数据下的近似搜索性能，避免每次查询全表扫描。
                .indexType(PgVectorStore.PgIndexType.HNSW)

                // 控制是否由Spring AI自动初始化PGVector表结构。本地开发环境可以开启方便测试；生产环境通常由DBA提前建表，避免应用启动修改数据库结构。
                .initializeSchema(Boolean.TRUE.equals(internalRulePgVectorProperties.getInitializeSchema()))

                // 设置向量表所在Schema，实现不同知识库之间的数据隔离。
                .schemaName(internalRulePgVectorProperties.getSchemaName())

                // 设置实际保存Embedding向量的PostgreSQL表名称。
                .vectorTableName(internalRulePgVectorProperties.getTableName())

                // 设置批量处理Document数量。例如10000个Chunk入库时，不一次性全部处理，而是拆成多个批次，降低Embedding接口压力和数据库压力。
                .maxDocumentBatchSize(internalRulePgVectorProperties.getMaxDocumentBatchSize())

                // 完成Builder构建，返回Spring管理的VectorStore Bean。
                .build();
    }
}
