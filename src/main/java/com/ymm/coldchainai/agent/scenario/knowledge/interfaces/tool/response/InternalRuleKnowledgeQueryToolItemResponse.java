package com.ymm.coldchainai.agent.scenario.knowledge.interfaces.tool.response;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchItemDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 内部规范知识查询Tool的单个知识片段响应。
 *
 * <p>该对象专门暴露给大模型，保留相似度排名、知识原文和Chunk位置，
 * 但不把Spring AI Document、VectorStore等基础设施对象泄漏给Agent层。</p>
 *
 * <p>在挖矿流程中，该对象相当于档案管理员交给矿场总调度员的一张资料卡：
 * 上面写清楚资料排名、原文和所在页块，模型只需要阅读资料，不需要知道PGVector如何查询。</p>
 */
@Getter
@AllArgsConstructor
public class InternalRuleKnowledgeQueryToolItemResponse {

    /**
     * 当前知识片段在本次检索结果中的排名，从1开始。
     */
    private final Integer rank;

    /**
     * 用户问题与当前知识Chunk之间的相似度Score。
     */
    private final Double score;

    /**
     * PGVector实际召回的知识原文。
     *
     * <p>模型最终回答内部规范问题时，只允许依据这些原文判断，
     * 不能凭自身常识补充公司规则。</p>
     */
    private final String content;

    /**
     * 当前知识片段在原文切片结果中的位置。
     */
    private final Integer chunkIndex;

    /**
     * 将Application层单个知识检索结果转换为Tool响应。
     *
     * @param itemDTO Application层知识检索结果
     * @return Agent Tool可直接返回给模型的知识片段
     */
    public static InternalRuleKnowledgeQueryToolItemResponse fromDTO(InternalRuleKnowledgeSearchItemDTO itemDTO) {
        if (Objects.isNull(itemDTO)) {
            throw new IllegalArgumentException("内部规范知识查询Tool单个结果DTO不能为空");
        }

        return new InternalRuleKnowledgeQueryToolItemResponse(
                itemDTO.getRank(),
                itemDTO.getScore(),
                itemDTO.getContent(),
                itemDTO.getChunkIndex());
    }
}
