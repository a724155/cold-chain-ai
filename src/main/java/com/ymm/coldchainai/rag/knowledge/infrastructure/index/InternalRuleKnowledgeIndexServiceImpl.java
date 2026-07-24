package com.ymm.coldchainai.rag.knowledge.infrastructure.index;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeIndexDTO;
import com.ymm.coldchainai.rag.knowledge.application.enumtype.RagErrorCodeEnum;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeIndexService;
import com.ymm.coldchainai.rag.knowledge.infrastructure.config.InternalRuleRagProperties;
import com.ymm.coldchainai.rag.knowledge.infrastructure.document.InternalRulePdfDocumentLoader;
import com.ymm.coldchainai.rag.knowledge.infrastructure.document.RagDocumentMetadataKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 满帮内部规范知识索引服务实现。
 *
 * <p>该类完成RAG知识入库的完整ETL链路：</p>
 *
 * <p>PDF → Document → Chunk → EmbeddingModel → PGVector。</p>
 *
 * <p>当前rebuildIndex主要用于local环境和教学阶段验证。真实生产知识库更新更适合采用版本化索引、灰度切换和失败回滚，
 * 避免“先删除旧知识再写新知识”过程中出现知识暂时不可用。</p>
 *
 * <p>在挖矿流程中，该组件相当于地质资料加工车间负责人：
 * Loader负责切原料，EmbeddingModel负责生成每块资料的特征指纹，
 * VectorStore负责把正文、标签和特征指纹一起送入专业档案仓库。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InternalRuleKnowledgeIndexServiceImpl implements IInternalRuleKnowledgeIndexService {

    /**
     * 内部规范PDF读取与切片组件。
     */
    private final InternalRulePdfDocumentLoader internalRulePdfDocumentLoader;

    /**
     * 满帮内部规范专属VectorStore。
     */
    private final VectorStore internalRuleVectorStore;

    /**
     * 内部规范PDF和版本配置。
     */
    private final InternalRuleRagProperties internalRuleRagProperties;

    /**
     * 重新建立满帮内部规范知识索引。
     *
     * @return 本次实际写入PGVector的文档信息和Chunk数量
     */
    @Override
    public InternalRuleKnowledgeIndexDTO rebuildIndex() {
        /*
         * 第一步只负责读取PDF并切片。
         * 此时Document中已经包含正文以及documentCode、version、chunkIndex等Metadata，
         * 但还没有调用远程Embedding模型。
         */
        List<Document> chunkDocumentList = internalRulePdfDocumentLoader.loadAndSplitDocumentList();

        if (CollectionUtils.isEmpty(chunkDocumentList)) {
            throw createKnowledgeIndexException("内部规范PDF没有生成可用于向量化的Chunk", null);
        }

        try {
            // local环境重复执行reindex时，先删除同一documentCode的旧Chunk，避免每次Postman调用都在PGVector中重复追加同一份PDF。
            deleteExistingDocumentIndex();

            /*
             * VectorStore.add()是本轮最关键的一步。
             * 它会对chunkDocumentList中的正文调用EmbeddingModel，当前EmbeddingModel实际对应百炼text-embedding-v4，
             * 每个Chunk最终生成1024维向量，再和正文、Metadata一起写入PGVector。
             */
            internalRuleVectorStore.add(chunkDocumentList);

            log.info("内部规范知识索引构建成功，documentCode={}，documentVersion={}，chunkCount={}",
                    internalRuleRagProperties.getDocumentCode(),
                    internalRuleRagProperties.getDocumentVersion(),
                    chunkDocumentList.size());

            return InternalRuleKnowledgeIndexDTO.of(
                    internalRuleRagProperties.getDocumentCode(),
                    internalRuleRagProperties.getDocumentVersion(),
                    chunkDocumentList.size());
        } catch (Exception exception) {
            throw createKnowledgeIndexException("内部规范Chunk生成Embedding或写入PGVector失败", exception);
        }
    }

    /**
     * 删除当前文档编码已经存在的旧向量数据。
     * <p>RAG知识更新时，不能直接向VectorStore追加新Chunk，
     * 否则旧版本知识和新版本知识会同时存在，导致相似度检索命中历史错误内容。</p>
     *
     * <p>当前方法根据Document Metadata中的documentCode删除对应知识，
     * 而不是清空整个internal_rule_vector_store表。
     * 例如：internal_rule_vector_store中可能保存：
     * 司机规则文档
     * 支付规则文档
     * 订单规则文档
     * 本次只更新司机规则，因此只能删除司机规则对应Chunk。
     * </p>
     * <p>在挖矿流程中，该方法相当于：矿场准备重新加工某个矿区的矿石时，
     * 先清理这个矿区仓库里的旧批次钻石，而不是把整个矿场仓库全部清空。</p>
     */
    private void deleteExistingDocumentIndex() {

        // 创建Metadata过滤条件构造器，用于根据Document保存时携带的业务标签筛选数据。
        // VectorStore中的每个Chunk都会保存documentCode，因此可以通过它定位属于某份文档的数据。
        FilterExpressionBuilder filterExpressionBuilder = new FilterExpressionBuilder();

        /*
         * 根据Document Metadata中的document_code精确构建删除条件。
         * 例如：
         * Chunk Metadata:
         * {
         *   document_code:"driver-rule-v1"
         * }
         * 那么这里只删除driver-rule-v1对应的数据。不直接删除整张向量表，避免未来多个知识库共用一个VectorStore时误删其他业务知识。
         */
        Filter.Expression documentCodeFilterExpression = filterExpressionBuilder
                .eq(RagDocumentMetadataKeys.DOCUMENT_CODE, internalRuleRagProperties.getDocumentCode())
                .build();

        // VectorStore根据Metadata过滤条件删除旧Chunk。删除完成后，后续重新Embedding生成的新Chunk不会和历史版本产生冲突。
        internalRuleVectorStore.delete(documentCodeFilterExpression);
        // 记录清理结果，方便后续排查知识更新流程是否执行成功。
        log.info("内部规范旧知识索引清理完成，documentCode={}", internalRuleRagProperties.getDocumentCode());
    }

    /**
     * 创建RAG知识索引构建异常。
     *
     * @param detailMessage 具体构建失败原因
     * @param cause 原始异常，可以为空
     * @return 知识索引构建异常
     */
    private IllegalStateException createKnowledgeIndexException(String detailMessage, Throwable cause) {
        String errorMessage = "%s：%s".formatted(RagErrorCodeEnum.RAG_KNOWLEDGE_INDEX_ERROR.getMessage(), detailMessage);

        if (Objects.isNull(cause)) {
            return new IllegalStateException(errorMessage);
        }

        return new IllegalStateException(errorMessage, cause);
    }
}
