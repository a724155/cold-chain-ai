package com.ymm.coldchainai.rag.knowledge.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 满帮内部规范知识检索结果DTO。
 *
 * <p>该对象记录本次检索问题、实际检索参数和按照相似度排序后的知识Chunk。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class InternalRuleKnowledgeSearchDTO {

    /**
     * 本次实际进行Embedding和相似度检索的问题。
     */
    private final String query;

    /**
     * 本次检索配置的最大返回数量。
     */
    private final Integer topK;

    /**
     * 本次检索使用的最低相似度阈值。
     */
    private final Double similarityThreshold;

    /**
     * 本次实际召回的Chunk数量。
     */
    private final Integer resultCount;

    /**
     * 按相似度从高到低排列的Chunk列表。
     */
    private final List<InternalRuleKnowledgeSearchItemDTO> resultItemList;
}
