package com.ymm.coldchainai.rag.knowledge.interfaces.web.response;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchItemDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 内部规范单个知识Chunk检索响应。
 */
@Getter
@AllArgsConstructor
public class InternalRuleKnowledgeSearchItemResponse {

    /**
     * 相似度排序名次。
     */
    private final Integer rank;

    /**
     * Chunk与问题之间的相似度Score。
     */
    private final Double score;

    /**
     * 被召回的知识原文。
     */
    private final String content;

    /**
     * 文档编码。
     */
    private final String documentCode;

    /**
     * 文档名称。
     */
    private final String documentName;

    /**
     * 文档版本。
     */
    private final String documentVersion;

    /**
     * 原始Chunk顺序。
     */
    private final Integer chunkIndex;

    /**
     * 将Application单个知识检索结果转换成接口响应。
     *
     * <p>该转换方法自身完成空值校验，避免未来被其他调用方单独复用时因为itemDTO为空产生NPE。</p>
     *
     * @param itemDTO 单个知识检索DTO
     * @return 单个知识检索接口响应
     */
    public static InternalRuleKnowledgeSearchItemResponse fromDTO(InternalRuleKnowledgeSearchItemDTO itemDTO) {

        if (Objects.isNull(itemDTO)) {
            throw new IllegalArgumentException("内部规范单个知识检索DTO不能为空");
        }

        return new InternalRuleKnowledgeSearchItemResponse(
                itemDTO.getRank(),
                itemDTO.getScore(),
                itemDTO.getContent(),
                itemDTO.getDocumentCode(),
                itemDTO.getDocumentName(),
                itemDTO.getDocumentVersion(),
                itemDTO.getChunkIndex());
    }
}
