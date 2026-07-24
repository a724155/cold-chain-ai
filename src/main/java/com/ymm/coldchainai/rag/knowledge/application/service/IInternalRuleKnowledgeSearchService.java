package com.ymm.coldchainai.rag.knowledge.application.service;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchDTO;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeSearchQuery;

/**
 * 满帮内部规范知识检索服务。
 *
 * <p>该接口定义“根据自然语言问题查找最相关内部规范Chunk”的完整查询用例。</p>
 *
 * <p>该接口只负责Retrieval，不调用ChatModel生成最终答案。
 * 下一阶段Agent会在此基础上把召回Chunk作为可信知识上下文交给模型。</p>
 */
public interface IInternalRuleKnowledgeSearchService {

    /**
     * 根据用户问题从内部规范向量知识库中检索相关Chunk。
     *
     * @param searchQuery 已完成参数校验的内部规范查询
     * @return 按相似度排序的知识检索结果
     */
    InternalRuleKnowledgeSearchDTO search(InternalRuleKnowledgeSearchQuery searchQuery);
}
