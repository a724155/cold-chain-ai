package com.ymm.coldchainai.rag.knowledge.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 满帮内部规范RAG问答结果DTO。
 *
 * <p>除最终自然语言答案外，同时保留知识文档编码和版本，
 * 便于后续审计某次回答实际依赖的是哪一版公司规范。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class InternalRuleKnowledgeAnswerDTO {

    /**
     * 用户原始问题。
     */
    private final String question;

    /**
     * RAG生成的最终答案。
     */
    private final String answer;

    /**
     * 当前知识文档编码。
     */
    private final String documentCode;

    /**
     * 当前知识文档版本。
     */
    private final String documentVersion;
}
