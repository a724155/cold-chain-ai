package com.ymm.coldchainai.rag.knowledge.application.service;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeAnswerDTO;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeAnswerQuery;

/**
 * 满帮内部规范RAG问答服务。
 *
 * <p>该接口定义“根据内部规范知识库回答用户问题”的完整Application用例。</p>
 */
public interface IInternalRuleKnowledgeAnswerService {

    /**
     * 根据满帮内部规范知识库回答用户问题。
     *
     * @param answerQuery 已完成基础参数校验的问答请求
     * @return RAG最终答案
     */
    InternalRuleKnowledgeAnswerDTO answer(InternalRuleKnowledgeAnswerQuery answerQuery);
}
