package com.ymm.coldchainai.rag.knowledge.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内部规范单个知识Chunk检索结果。
 *
 * <p>该DTO不仅返回Chunk正文，还保留Score和关键Metadata，
 * 方便当前阶段判断向量检索到底命中了哪块原始知识。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class InternalRuleKnowledgeSearchItemDTO {

    /**
     * 当前结果在本次相似度排序中的名次，从1开始。
     */
    private final Integer rank;

    /**
     * 当前Chunk与用户问题之间的相似度Score。
     *
     * <p>值越高代表语义越接近。</p>
     */
    private final Double score;

    /**
     * 被检索到的Chunk原始文本。
     */
    private final String content;

    /**
     * Chunk所属文档编码。
     */
    private final String documentCode;

    /**
     * Chunk所属文档名称。
     */
    private final String documentName;

    /**
     * Chunk所属文档版本。
     */
    private final String documentVersion;

    /**
     * Chunk在原知识文档切片结果中的顺序。
     */
    private final Integer chunkIndex;
}
