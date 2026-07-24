package com.ymm.coldchainai.rag.knowledge.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 满帮内部规范知识索引构建结果DTO。
 *
 * <p>该DTO只记录本次建立知识索引后的核心结果，不把Embedding浮点向量直接返回给Controller。</p>
 *
 * <p>在挖矿流程中，该对象相当于地质资料入库完成后的交付凭证：记录处理的是哪份资料、哪个版本以及最终入库了多少块知识原料。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class InternalRuleKnowledgeIndexDTO {

    /**
     * 稳定文档编码。
     */
    private final String documentCode;

    /**
     * 当前文档版本。
     */
    private final String documentVersion;

    /**
     * 本次实际写入PGVector的Chunk数量。
     */
    private final Integer chunkCount;
}
