package com.ymm.coldchainai.rag.knowledge.infrastructure.retrieval;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchDTO;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchItemDTO;
import com.ymm.coldchainai.rag.knowledge.application.enumtype.RagErrorCodeEnum;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeSearchQuery;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeSearchService;
import com.ymm.coldchainai.rag.knowledge.infrastructure.config.InternalRuleRagProperties;
import com.ymm.coldchainai.rag.knowledge.infrastructure.config.InternalRuleRetrievalProperties;
import com.ymm.coldchainai.rag.knowledge.infrastructure.document.RagDocumentMetadataKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStoreRetriever;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 满帮内部规范向量知识检索实现。
 *
 * <p>该类负责把用户自然语言问题交给EmbeddingModel生成问题向量，再通过PGVector寻找语义最相近的知识Chunk。</p>
 *
 * <p>当前类只依赖VectorStoreRetriever，而不是完整VectorStore。
 * 检索组件没有新增、删除知识的权限，知识写入职责仍然属于KnowledgeIndexService。</p>
 *
 * <p>在挖矿流程中，该组件相当于只拥有“查阅档案”权限的地质资料检索员：
 * 可以从档案库取资料，但不能修改、删除或者重新入库资料。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InternalRuleKnowledgeSearchServiceImpl implements IInternalRuleKnowledgeSearchService {

    /**
     * 内部规范只读向量检索器。
     */
    private final VectorStoreRetriever internalRuleVectorStoreRetriever;

    /**
     * 内部规范文档身份与版本配置。
     */
    private final InternalRuleRagProperties internalRuleRagProperties;

    /**
     * TopK与相似度阈值配置。
     */
    private final InternalRuleRetrievalProperties internalRuleRetrievalProperties;

    /**
     * 根据自然语言问题检索最相关的内部规范Chunk。
     *
     * <p>该方法是RAG知识检索入口，负责将用户问题转换成向量检索请求，
     * 调用VectorStore查询相关知识Chunk，并转换成Application层DTO返回。</p>
     *
     * <p>在挖矿流程中，该方法相当于矿场查询员：
     * 客户提出一个需求后，查询员先确认查询范围和矿区，
     * 再让智能设备寻找最匹配的矿石样本，最后整理成客户能够理解的检索报告。</p>
     *
     * @param searchQuery 已完成基础校验的内部规范查询
     * @return 按相似度从高到低排序的知识Chunk
     */
    @Override
    public InternalRuleKnowledgeSearchDTO search(InternalRuleKnowledgeSearchQuery searchQuery) {
        if (Objects.isNull(searchQuery)) {
            throw new IllegalArgumentException("内部规范知识检索查询对象不能为空");
        }

        try {
            // Metadata过滤相当于给向量检索增加业务WHERE条件。即使未来同一张VectorStore表保存多份PDF，也只能检索当前内部规范当前版本的数据。
            // 创建Metadata过滤条件，限制本次检索只能查询当前内部规范文档，避免同一个VectorStore存储多个知识库时产生跨文档误召回。
            Filter.Expression documentFilterExpression = createDocumentFilterExpression();

            /*
             * 构建Spring AI SearchRequest，定义本次向量检索的核心参数：
             * query：用户原始问题，后续会通过EmbeddingModel转换成向量；
             * topK：最多返回多少个语义最接近的Chunk；
             * similarityThreshold：最低相似度阈值，低于该值的数据不会返回；
             * filterExpression：限制检索范围的Metadata条件。
             * 例如用户询问“司机支付定金规则”，PGVector不会直接匹配字符串，而是根据问题向量和知识Chunk向量之间的距离判断语义相关性。
             */
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(searchQuery.getQuery())
                    .topK(internalRuleRetrievalProperties.getTopK())
                    .similarityThreshold(internalRuleRetrievalProperties.getSimilarityThreshold())
                    .filterExpression(documentFilterExpression)
                    .build();

            // similaritySearch内部会先通过同一个EmbeddingModel把用户问题转换成1024维向量，再让PGVector使用向量距离查找最接近的Document。
            List<Document> documentList = ListUtils.emptyIfNull(internalRuleVectorStoreRetriever.similaritySearch(searchRequest));

            // 没有任何Chunk达到检索条件属于正常查询结果，不属于数据库故障。
            if (documentList.isEmpty()) {
                log.info("内部规范知识检索完成但未召回Chunk，queryLength={}，topK={}，similarityThreshold={}",
                        searchQuery.getQuery().length(),
                        internalRuleRetrievalProperties.getTopK(),
                        internalRuleRetrievalProperties.getSimilarityThreshold());

                return InternalRuleKnowledgeSearchDTO.of(
                        searchQuery.getQuery(),
                        internalRuleRetrievalProperties.getTopK(),
                        internalRuleRetrievalProperties.getSimilarityThreshold(),
                        0,
                        List.of());
            }

            // 将Spring AI Document转换成Application层稳定DTO，避免Controller或上层业务直接依赖Spring AI框架对象。
            List<InternalRuleKnowledgeSearchItemDTO> resultItemList = convertSearchResultList(documentList);
            // 第一条结果通常代表最高相似度Chunk，记录最高Score方便线上排查召回质量。
            Double topScore = resultItemList.getFirst().getScore();

            log.info("内部规范知识检索完成，queryLength={}，topK={}，similarityThreshold={}，resultCount={}，topScore={}",
                    searchQuery.getQuery().length(),
                    internalRuleRetrievalProperties.getTopK(),
                    internalRuleRetrievalProperties.getSimilarityThreshold(),
                    resultItemList.size(),
                    topScore);
            // 返回Application层检索结果，包含查询条件、召回数量以及最终知识Chunk列表。
            return InternalRuleKnowledgeSearchDTO.of(
                    searchQuery.getQuery(),
                    internalRuleRetrievalProperties.getTopK(),
                    internalRuleRetrievalProperties.getSimilarityThreshold(),
                    resultItemList.size(),
                    List.copyOf(resultItemList));
        } catch (Exception exception) {
            throw createKnowledgeSearchException("内部规范问题生成Embedding或执行PGVector相似度检索失败", exception);
        }
    }

    /**
     * 创建当前内部规范文档的Metadata过滤条件。
     *
     * <p>同时限制documentCode和documentVersion，
     * 避免未来新旧版本知识同时存在时召回已经废弃的规则。</p>
     *
     * @return 当前文档和版本的Metadata过滤表达式
     */
    private Filter.Expression createDocumentFilterExpression() {
        FilterExpressionBuilder filterExpressionBuilder = new FilterExpressionBuilder();

        return filterExpressionBuilder.and(filterExpressionBuilder.eq(RagDocumentMetadataKeys.DOCUMENT_CODE, internalRuleRagProperties.getDocumentCode()),
                        filterExpressionBuilder.eq(RagDocumentMetadataKeys.DOCUMENT_VERSION, internalRuleRagProperties.getDocumentVersion()))
                .build();
    }

    /**
     * 将向量检索得到的Document列表转换成Application DTO。
     *
     * <p>VectorStore返回的是Spring AI框架中的Document对象，
     * 该方法负责把基础设施层对象转换成Application层可使用的业务DTO。</p>
     *
     * <p>转换过程中会保留：
     * 1. 检索命中的文本内容；
     * 2. 向量相似度Score；
     * 3. 文档编码、名称、版本等业务Metadata；
     * 4. Chunk编号，方便后续定位具体知识片段。</p>
     *
     * <p>在挖矿流程中，该方法相当于：
     * 智能仓库找到符合客户要求的钻石原料后，
     * 档案管理员不会直接把仓库内部记录交给客户，
     * 而是整理成客户能理解的钻石信息报告。</p>
     *
     * @param documentList 按相似度排序的Document列表
     * @return 保留正文、Score和核心Metadata的DTO列表
     */
    private List<InternalRuleKnowledgeSearchItemDTO> convertSearchResultList(List<Document> documentList) {

        // 创建结果集合，用于保存转换后的业务DTO。初始容量使用documentList.size()，减少ArrayList扩容次数，提高批量转换效率。
        List<InternalRuleKnowledgeSearchItemDTO> resultItemList = new ArrayList<>(documentList.size());

        // 遍历每个向量检索结果，将Spring AI Document逐个校验并转换成Application层业务DTO。
        // 每一次循环代表处理一个命中的知识Chunk，最终形成可以交给Tool和Agent使用的结构化知识列表。
        for (int index = 0; index < documentList.size(); index++) {

            // 获取当前向量检索命中的Document。index对应当前Document在相似度排序结果中的位置，第一条通常代表最相关知识。
            Document document = documentList.get(index);

            if (Objects.isNull(document)) {
                throw new IllegalStateException("向量检索结果包含空Document，index=%s".formatted(index));
            }

            if (StringUtils.isBlank(document.getText())) {
                throw new IllegalStateException("向量检索结果Document正文为空，documentId=%s".formatted(document.getId()));
            }

            // Metadata统一使用MapUtils读取，避免缺少某个Metadata字段时发生空指针。Metadata保存Document额外业务信息，例如文档编码、版本、Chunk编号。
            // VectorStore检索返回的Metadata可能不存在，因此使用emptyIfNull避免空指针。
            Map<String, Object> metadataMap = MapUtils.emptyIfNull(document.getMetadata());

            /*
             * 将Spring AI Document转换成业务DTO。
             * Document中的text属于原始知识内容；score表示当前Chunk和用户问题的语义匹配程度；
             * Metadata用于后续展示来源、版本和问题排查。Application层只接收整理后的DTO，不直接依赖Spring AI Document。
             */
            InternalRuleKnowledgeSearchItemDTO itemDTO = InternalRuleKnowledgeSearchItemDTO.of(
                    index + 1,
                    document.getScore(),
                    document.getText(),
                    MapUtils.getString(metadataMap, RagDocumentMetadataKeys.DOCUMENT_CODE),
                    MapUtils.getString(metadataMap, RagDocumentMetadataKeys.DOCUMENT_NAME),
                    MapUtils.getString(metadataMap, RagDocumentMetadataKeys.DOCUMENT_VERSION),
                    MapUtils.getInteger(metadataMap, RagDocumentMetadataKeys.CHUNK_INDEX));

            resultItemList.add(itemDTO);
        }

        return resultItemList;
    }

    /**
     * 创建RAG知识检索系统异常。
     *
     * @param detailMessage 具体检索失败原因
     * @param cause 原始异常
     * @return RAG知识检索异常
     */
    private IllegalStateException createKnowledgeSearchException(String detailMessage, Throwable cause) {
        String errorMessage = "%s：%s".formatted(RagErrorCodeEnum.RAG_KNOWLEDGE_SEARCH_ERROR.getMessage(), detailMessage);

        if (Objects.isNull(cause)) {
            return new IllegalStateException(errorMessage);
        }

        return new IllegalStateException(errorMessage, cause);
    }
}
