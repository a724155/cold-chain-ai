package com.ymm.coldchainai.rag.knowledge.infrastructure.retrieval;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchDTO;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchItemDTO;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeSearchQuery;
import com.ymm.coldchainai.rag.knowledge.infrastructure.config.InternalRuleRagProperties;
import com.ymm.coldchainai.rag.knowledge.infrastructure.config.InternalRuleRetrievalProperties;
import com.ymm.coldchainai.rag.knowledge.infrastructure.document.RagDocumentMetadataKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStoreRetriever;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 满帮内部规范知识检索服务单元测试。
 *
 * <p>本测试只验证Application检索逻辑，不连接真实PGVector，也不会调用EmbeddingModel。
 * VectorStoreRetriever通过Mockito模拟，从而把测试范围严格限制在当前类自身。</p>
 *
 * <p>主要验证SearchRequest构建、结果转换、Metadata读取、空结果以及异常防御是否符合预期。</p>
 */
@ExtendWith(MockitoExtension.class)
class InternalRuleKnowledgeSearchServiceImplTest {

    /**
     * 测试使用的内部规范文档编码。
     */
    private static final String DOCUMENT_CODE = "mmb-internal-rules";

    /**
     * 测试使用的内部规范文档名称。
     */
    private static final String DOCUMENT_NAME = "满帮集团内部规范文档";

    /**
     * 测试使用的内部规范文档版本。
     */
    private static final String DOCUMENT_VERSION = "V1.0";

    /**
     * 测试使用的TopK。
     */
    private static final Integer TOP_K = 5;

    /**
     * 当前项目已经确定保持不变的相似度阈值。
     */
    private static final Double SIMILARITY_THRESHOLD = 0.0D;

    /**
     * Mock只读向量检索器，避免单元测试真实访问阿里云PGVector。
     */
    @Mock
    private VectorStoreRetriever internalRuleVectorStoreRetriever;

    /**
     * 内部规范文档配置。
     */
    private InternalRuleRagProperties internalRuleRagProperties;

    /**
     * 内部规范检索配置。
     */
    private InternalRuleRetrievalProperties internalRuleRetrievalProperties;

    /**
     * 当前需要测试的知识检索服务。
     */
    private InternalRuleKnowledgeSearchServiceImpl internalRuleKnowledgeSearchService;

    /**
     * 每个测试执行前重新创建配置和被测对象，避免不同测试之间共享可变状态。
     */
    @BeforeEach
    void setUp() {
        // 构造当前内部规范文档配置，供Metadata Filter使用。
        internalRuleRagProperties = new InternalRuleRagProperties();
        internalRuleRagProperties.setDocumentCode(DOCUMENT_CODE);
        internalRuleRagProperties.setDocumentName(DOCUMENT_NAME);
        internalRuleRagProperties.setDocumentVersion(DOCUMENT_VERSION);

        // 构造当前检索策略，与local环境保持TopK=5、threshold=0.0。
        internalRuleRetrievalProperties = new InternalRuleRetrievalProperties();
        internalRuleRetrievalProperties.setTopK(TOP_K);
        internalRuleRetrievalProperties.setSimilarityThreshold(SIMILARITY_THRESHOLD);

        // 直接通过构造方法创建被测对象，不启动Spring容器。
        internalRuleKnowledgeSearchService = new InternalRuleKnowledgeSearchServiceImpl(
                internalRuleVectorStoreRetriever,
                internalRuleRagProperties,
                internalRuleRetrievalProperties);
    }

    /**
     * 验证正常检索时能够正确构建SearchRequest，并把Document转换为稳定DTO。
     */
    @Test
    void shouldSearchInternalRuleKnowledgeSuccessfully() {
        // 模拟PGVector返回第一名考勤规则Chunk。
        Document attendanceDocument = createDocument(
                "chunk-1",
                "员工每天应在上午9:00之前完成上班打卡，9:00整打卡视为迟到。",
                0.91D,
                1);

        // 模拟PGVector返回第二名工作日规则Chunk。
        Document workdayDocument = createDocument(
                "chunk-2",
                "公司原则上周六周日双休，但每个月最后一个周六需要正常上班。",
                0.82D,
                2);

        // 当被测代码执行向量检索时，固定返回已经准备好的两个Document。
        when(internalRuleVectorStoreRetriever.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(attendanceDocument, workdayDocument));

        // 构造真实的Application查询对象。
        InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create("我上午9点整打卡算迟到吗？");

        // 执行真正需要测试的检索逻辑。
        InternalRuleKnowledgeSearchDTO searchDTO = internalRuleKnowledgeSearchService.search(searchQuery);

        // 捕获实际发送给VectorStoreRetriever的SearchRequest，验证查询参数没有在中间被改错。
        ArgumentCaptor<SearchRequest> searchRequestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(internalRuleVectorStoreRetriever).similaritySearch(searchRequestCaptor.capture());

        // 获取被测代码真实构造出来的SearchRequest。
        SearchRequest actualSearchRequest = searchRequestCaptor.getValue();

        // 同时验证问题、TopK、阈值以及Metadata Filter都正确进入SearchRequest。
        assertAll(
                () -> assertEquals("我上午9点整打卡算迟到吗？", actualSearchRequest.getQuery()),
                () -> assertEquals(TOP_K.intValue(), actualSearchRequest.getTopK()),
                () -> assertEquals(SIMILARITY_THRESHOLD, actualSearchRequest.getSimilarityThreshold()),
                () -> assertTrue(actualSearchRequest.hasFilterExpression()));

        // 验证整体检索结果没有丢失用户问题和检索配置。
        assertAll(
                () -> assertNotNull(searchDTO),
                () -> assertEquals("我上午9点整打卡算迟到吗？", searchDTO.getQuery()),
                () -> assertEquals(TOP_K, searchDTO.getTopK()),
                () -> assertEquals(SIMILARITY_THRESHOLD, searchDTO.getSimilarityThreshold()),
                () -> assertEquals(2, searchDTO.getResultCount()),
                () -> assertEquals(2, searchDTO.getResultItemList().size()));

        // 获取第一名结果，验证正文、Score、Metadata和排名全部正确转换。
        InternalRuleKnowledgeSearchItemDTO firstItemDTO = searchDTO.getResultItemList().getFirst();

        assertAll(
                () -> assertEquals(1, firstItemDTO.getRank()),
                () -> assertEquals(0.91D, firstItemDTO.getScore()),
                () -> assertEquals("员工每天应在上午9:00之前完成上班打卡，9:00整打卡视为迟到。", firstItemDTO.getContent()),
                () -> assertEquals(DOCUMENT_CODE, firstItemDTO.getDocumentCode()),
                () -> assertEquals(DOCUMENT_NAME, firstItemDTO.getDocumentName()),
                () -> assertEquals(DOCUMENT_VERSION, firstItemDTO.getDocumentVersion()),
                () -> assertEquals(1, firstItemDTO.getChunkIndex()));

        // 获取第二名结果，重点验证代码确实按照Document返回顺序生成rank=2。
        InternalRuleKnowledgeSearchItemDTO secondItemDTO = searchDTO.getResultItemList().get(1);

        assertAll(
                () -> assertEquals(2, secondItemDTO.getRank()),
                () -> assertEquals(0.82D, secondItemDTO.getScore()),
                () -> assertEquals(2, secondItemDTO.getChunkIndex()));
    }

    /**
     * 验证VectorStoreRetriever返回null时按照“没有召回知识”处理，而不是发生空指针。
     */
    @Test
    void shouldReturnEmptyResultWhenRetrieverReturnsNull() {
        // 模拟底层VectorStoreRetriever异常实现返回null，验证当前Service仍然能够安全兜底。
        when(internalRuleVectorStoreRetriever.similaritySearch(any(SearchRequest.class))).thenReturn(null);

        InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create("公司食堂晚上几点停止供应晚饭？");

        // 执行检索，null List应该被转换成正常空结果。
        InternalRuleKnowledgeSearchDTO searchDTO = internalRuleKnowledgeSearchService.search(searchQuery);

        assertAll(
                () -> assertNotNull(searchDTO),
                () -> assertEquals(0, searchDTO.getResultCount()),
                () -> assertNotNull(searchDTO.getResultItemList()),
                () -> assertTrue(searchDTO.getResultItemList().isEmpty()));
    }

    /**
     * 验证没有任何Chunk命中时正常返回空结果。
     */
    @Test
    void shouldReturnEmptyResultWhenNoDocumentMatched() {
        // 正常的“没有知识命中”应该由底层返回空List，而不是抛异常。
        when(internalRuleVectorStoreRetriever.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create("公司有没有停车补贴？");

        InternalRuleKnowledgeSearchDTO searchDTO = internalRuleKnowledgeSearchService.search(searchQuery);

        assertAll(
                () -> assertNotNull(searchDTO),
                () -> assertEquals(0, searchDTO.getResultCount()),
                () -> assertTrue(searchDTO.getResultItemList().isEmpty()));
    }

    /**
     * 验证searchQuery本身为空时立即阻断，不允许继续访问向量数据库。
     */
    @Test
    void shouldRejectNullSearchQuery() {
        // null属于调用方编程错误，当前Service必须在进入VectorStore之前立即失败。
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> internalRuleKnowledgeSearchService.search(null));

        assertEquals("内部规范知识检索查询对象不能为空", exception.getMessage());

        // 参数已经非法时绝不能继续调用PGVector。
        verifyNoInteractions(internalRuleVectorStoreRetriever);
    }

    /**
     * 验证PGVector检索异常时能够包装成明确的RAG知识检索异常，并保留原始cause。
     */
    @Test
    void shouldWrapExceptionWhenVectorStoreRetrieverFails() {
        // 模拟阿里云PGVector连接失败、SQL失败或者Embedding检索链路异常。
        RuntimeException vectorStoreException = new RuntimeException("模拟PGVector检索失败");

        when(internalRuleVectorStoreRetriever.similaritySearch(any(SearchRequest.class))).thenThrow(vectorStoreException);

        InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create("9点整打卡算迟到吗？");

        // Service不能把底层RuntimeException裸着泄漏到上层，需要包装成RAG语义异常。
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeSearchService.search(searchQuery));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识检索失败")),
                () -> assertSame(vectorStoreException, exception.getCause()));
    }

    /**
     * 验证检索结果列表中出现null Document时明确失败，而不是静默丢弃错误数据。
     */
    @Test
    void shouldRejectNullDocumentInSearchResultList() {
        Document validDocument = createDocument(
                "chunk-1",
                "禁止直接在master分支进行代码操作。",
                0.90D,
                1);

        // ArrayList允许显式放入null，用于模拟异常VectorStore实现返回脏数据。
        List<Document> documentList = new ArrayList<>();
        documentList.add(validDocument);
        documentList.add(null);

        when(internalRuleVectorStoreRetriever.similaritySearch(any(SearchRequest.class))).thenReturn(documentList);

        InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create("可以直接在master分支修改代码吗？");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeSearchService.search(searchQuery));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识检索失败")),
                () -> assertNotNull(exception.getCause()),
                () -> assertTrue(exception.getCause().getMessage().contains("向量检索结果包含空Document")));
    }

    /**
     * 验证检索结果Document正文为空时明确失败，避免把没有正文的知识交给上层Agent。
     */
    @Test
    void shouldRejectBlankDocumentContent() {
        // 构造只有空白字符的Document，模拟异常向量数据。
        Document blankDocument = Document.builder()
                .id("chunk-blank")
                .text(" ")
                .score(0.88D)
                .metadata(RagDocumentMetadataKeys.DOCUMENT_CODE, DOCUMENT_CODE)
                .metadata(RagDocumentMetadataKeys.DOCUMENT_VERSION, DOCUMENT_VERSION)
                .metadata(RagDocumentMetadataKeys.CHUNK_INDEX, 1)
                .build();

        when(internalRuleVectorStoreRetriever.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(blankDocument));

        InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create("测试空知识正文");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeSearchService.search(searchQuery));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识检索失败")),
                () -> assertNotNull(exception.getCause()),
                () -> assertTrue(exception.getCause().getMessage().contains("Document正文为空")));
    }

    /**
     * 验证Document缺少业务Metadata时不会发生NPE。
     *
     * <p>Metadata缺失属于数据完整性问题，但当前转换逻辑使用MapUtils安全读取，
     * 因此允许DTO对应字段为空，后续可以单独增加知识数据完整性校验。</p>
     */
    @Test
    void shouldNotThrowNullPointerWhenDocumentMetadataIsMissing() {
        // 构造有正文和Score、但没有业务Metadata的Document。
        Document documentWithoutMetadata = Document.builder()
                .id("chunk-no-metadata")
                .text("禁止直接在master分支操作代码。")
                .score(0.87D)
                .build();

        when(internalRuleVectorStoreRetriever.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(documentWithoutMetadata));

        InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create("master分支能直接改代码吗？");

        InternalRuleKnowledgeSearchDTO searchDTO = internalRuleKnowledgeSearchService.search(searchQuery);

        // 获取唯一结果，验证Metadata字段安全返回null而不是发生NPE。
        InternalRuleKnowledgeSearchItemDTO itemDTO = searchDTO.getResultItemList().getFirst();

        assertAll(
                () -> assertEquals(1, searchDTO.getResultCount()),
                () -> assertEquals("禁止直接在master分支操作代码。", itemDTO.getContent()),
                () -> assertEquals(0.87D, itemDTO.getScore()),
                () -> assertNull(itemDTO.getDocumentCode()),
                () -> assertNull(itemDTO.getDocumentName()),
                () -> assertNull(itemDTO.getDocumentVersion()),
                () -> assertNull(itemDTO.getChunkIndex()));
    }

    /**
     * 创建测试使用的Spring AI Document。
     *
     * <p>Document模拟PGVector similaritySearch真实返回的数据，
     * 同时包含知识正文、相似度Score以及RAG业务Metadata。</p>
     *
     * @param documentId Document唯一标识
     * @param content 知识Chunk正文
     * @param score 向量检索相似度Score
     * @param chunkIndex Chunk顺序
     * @return 可直接作为VectorStoreRetriever返回值的Document
     */
    private Document createDocument(String documentId, String content, Double score, Integer chunkIndex) {
        return Document.builder()
                .id(documentId)
                .text(content)
                .score(score)
                .metadata(RagDocumentMetadataKeys.DOCUMENT_CODE, DOCUMENT_CODE)
                .metadata(RagDocumentMetadataKeys.DOCUMENT_NAME, DOCUMENT_NAME)
                .metadata(RagDocumentMetadataKeys.DOCUMENT_VERSION, DOCUMENT_VERSION)
                .metadata(RagDocumentMetadataKeys.CHUNK_INDEX, chunkIndex)
                .build();
    }
}
