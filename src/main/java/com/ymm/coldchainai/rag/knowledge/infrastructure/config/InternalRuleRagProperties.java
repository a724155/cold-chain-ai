package com.ymm.coldchainai.rag.knowledge.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 满帮内部规范RAG文档配置。
 *
 * <p>该类把application-local.yml中的文档路径、版本和切片参数转换成强类型Java配置，避免Loader内部到处通过字符串读取配置。</p>
 *
 * <p>在挖矿流程中，该配置相当于矿场原料加工车间的设备参数表：它规定从哪里取地质档案、每块原料切多大以及最多允许加工多少块。
 * 如果这些参数散落在业务代码中，后续调整切片策略会非常困难。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "cold-chain-ai.rag.internal-rule")
public class InternalRuleRagProperties {

    /**
     * PDF资源位置。
     */
    private String resourceLocation;

    /**
     * 稳定文档编码。
     */
    private String documentCode;

    /**
     * 文档展示名称。
     */
    private String documentName;

    /**
     * 文档版本。
     */
    private String documentVersion;

    /**
     * PagePdfDocumentReader每个原始Document包含的PDF页数。1
     * PDF先按照物理页转换成Document，再继续进行Token切片。
     */
    private Integer pagesPerDocument;

    /**
     * TokenTextSplitter每个Chunk的目标Token数量。180
     */
    private Integer chunkSize;

    /**
     * Chunk允许保留的最小字符数量。60
     * 太短的文本块不单独形成知识Chunk，避免产生大量无意义碎片。
     */
    private Integer minChunkSizeChars;

    /**
     * 后续允许进入Embedding流程的最小Chunk字符数量。20
     * 小于该字符长度的Chunk不进入后续Embedding流程。
     */
    private Integer minChunkLengthToEmbed;

    /**
     * 单份知识文档允许生成的最大Chunk数量。200
     * 防止异常PDF产生海量切片。
     */
    private Integer maxNumChunks;

    /**
     * 文本切片时是否保留分隔符。true
     */
    private Boolean keepSeparator;
}
