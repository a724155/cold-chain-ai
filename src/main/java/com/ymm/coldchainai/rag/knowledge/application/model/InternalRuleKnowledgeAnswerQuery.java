package com.ymm.coldchainai.rag.knowledge.application.model;

import com.ymm.coldchainai.rag.knowledge.application.enumtype.RagErrorCodeEnum;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 满帮内部规范RAG问答查询对象。
 *
 * <p>Controller请求进入Application层以后统一转换成该对象，
 * 避免HTTP协议对象直接传入ChatClient基础设施。</p>
 */
@Getter
public class InternalRuleKnowledgeAnswerQuery {

    /**
     * 用户需要根据内部规范回答的问题。
     */
    private final String question;

    /**
     * 创建内部规范RAG问答查询。
     *
     * @param question 用户问题
     * @return 已完成基础校验的问答查询
     */
    private InternalRuleKnowledgeAnswerQuery(String question) {
        this.question = question;
    }

    /**
     * 创建合法的RAG问答查询对象。
     *
     * @param question 用户问题
     * @return 合法查询对象
     */
    public static InternalRuleKnowledgeAnswerQuery create(String question) {
        if (StringUtils.isBlank(question)) {
            throw new BusinessException(RagErrorCodeEnum.RAG_KNOWLEDGE_ANSWER_PARAMETER_ERROR, "内部规范问答问题不能为空");
        }

        return new InternalRuleKnowledgeAnswerQuery(StringUtils.trim(question));
    }
}
