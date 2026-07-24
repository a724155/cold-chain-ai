package com.ymm.coldchainai.rag.knowledge.application.service;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeIndexDTO;

/**
 * 满帮内部规范知识索引服务。
 *
 * <p>该接口定义“重新建立内部规范知识索引”这一完整用例。
 * 调用方只需要发起重建，不需要了解底层PDF Reader、EmbeddingModel或者PGVector。</p>
 *
 * <p>在挖矿流程中，该接口相当于项目经理下达的“重新整理地质资料库”任务，
 * 至于如何切割资料、生成特征指纹和存入档案仓库，都由基础设施层负责。</p>
 */
public interface IInternalRuleKnowledgeIndexService {

    /**
     * 重新读取内部规范PDF、切分Chunk、生成Embedding并写入PGVector。
     *
     * @return 本次知识索引构建结果
     */
    InternalRuleKnowledgeIndexDTO rebuildIndex();
}
