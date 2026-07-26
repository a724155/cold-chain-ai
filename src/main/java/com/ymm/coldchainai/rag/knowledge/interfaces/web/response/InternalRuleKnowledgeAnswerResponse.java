package com.ymm.coldchainai.rag.knowledge.interfaces.web.response;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeAnswerDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * 满帮内部规范RAG问答验证响应。
 *
 * <p>除模型答案外返回documentCode和documentVersion，
 * 方便研发阶段确认回答依赖的知识版本。</p>
 */
@Getter
@AllArgsConstructor
public class InternalRuleKnowledgeAnswerResponse {

    /**
     * 用户问题。
     */
    private final String question;

    /**
     * RAG最终答案。
     */
    private final String answer;

    /**
     * 知识文档编码。
     */
    private final String documentCode;

    /**
     * 知识文档版本。
     */
    private final String documentVersion;

    /**
     * 将Application DTO转换成HTTP响应。
     *
     * @param answerDTO RAG问答结果
     * @return HTTP响应
     */
    public static InternalRuleKnowledgeAnswerResponse fromDTO(InternalRuleKnowledgeAnswerDTO answerDTO) {
        if (Objects.isNull(answerDTO)) {
            throw new IllegalArgumentException("内部规范RAG问答DTO不能为空");
        }

        return new InternalRuleKnowledgeAnswerResponse(
                answerDTO.getQuestion(),
                answerDTO.getAnswer(),
                answerDTO.getDocumentCode(),
                answerDTO.getDocumentVersion());
    }
}
