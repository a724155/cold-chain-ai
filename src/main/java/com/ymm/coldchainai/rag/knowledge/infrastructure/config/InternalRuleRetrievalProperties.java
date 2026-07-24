package com.ymm.coldchainai.rag.knowledge.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 满帮内部规范向量检索配置。
 *
 * <p>该配置控制PGVector语义检索阶段返回多少个候选Chunk，
 * 以及候选Chunk必须达到怎样的最低相似度。</p>
 *
 * <p>在挖矿流程中，该配置相当于地质档案搜索标准：topK决定档案员最多拿回多少份候选资料，
 * similarityThreshold决定相似程度低于什么水平的资料直接丢弃。</p>
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "cold-chain-ai.rag.internal-rule.retrieval")
public class InternalRuleRetrievalProperties {

    /**
     * 单次向量检索最多返回的候选Chunk数量。
     */
    @NotNull
    @Min(1)
    @Max(20)
    private Integer topK;

    /**
     * 最低相似度阈值。
     *
     * <p>取值范围为0到1，值越大表示要求检索结果与问题越相似。</p>
     */
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private Double similarityThreshold;
}
