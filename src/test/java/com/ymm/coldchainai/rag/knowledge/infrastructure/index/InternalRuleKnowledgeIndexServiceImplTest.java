package com.ymm.coldchainai.rag.knowledge.infrastructure.index;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeIndexDTO;
import com.ymm.coldchainai.rag.knowledge.infrastructure.config.InternalRuleRagProperties;
import com.ymm.coldchainai.rag.knowledge.infrastructure.document.InternalRulePdfDocumentLoader;
import com.ymm.coldchainai.rag.knowledge.infrastructure.document.RagDocumentMetadataKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 满帮内部规范知识索引构建服务单元测试。
 *
 * <p>本测试只验证索引构建流程自身，不读取真实PDF、不调用EmbeddingModel，
 * 也不会连接阿里云PGVector。PDF Loader和VectorStore全部通过Mockito模拟。</p>
 *
 * <p>重点验证：Chunk加载、旧知识删除、新知识写入、执行顺序、空文档防御以及
 * PDF读取失败、旧索引删除失败、新索引写入失败等异常场景。</p>
 */
@ExtendWith(MockitoExtension.class)
class InternalRuleKnowledgeIndexServiceImplTest {

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
     * Mock PDF读取和Chunk切分组件，避免单测真正读取classpath PDF。
     */
    @Mock
    private InternalRulePdfDocumentLoader internalRulePdfDocumentLoader;

    /**
     * Mock内部规范VectorStore，避免单测真正调用Embedding并访问PGVector。
     */
    @Mock
    private VectorStore internalRuleVectorStore;

    /**
     * 内部规范文档配置。
     */
    private InternalRuleRagProperties internalRuleRagProperties;

    /**
     * 当前需要测试的知识索引构建服务。
     */
    private InternalRuleKnowledgeIndexServiceImpl internalRuleKnowledgeIndexService;

    /**
     * 每个测试执行前重新创建配置和被测对象，避免不同测试之间共享状态。
     */
    @BeforeEach
    void setUp() {
        // 创建索引构建需要的当前知识文档身份配置。
        internalRuleRagProperties = new InternalRuleRagProperties();
        internalRuleRagProperties.setDocumentCode(DOCUMENT_CODE);
        internalRuleRagProperties.setDocumentName(DOCUMENT_NAME);
        internalRuleRagProperties.setDocumentVersion(DOCUMENT_VERSION);

        // 直接通过构造方法创建被测Service，不启动Spring容器。
        internalRuleKnowledgeIndexService = new InternalRuleKnowledgeIndexServiceImpl(
                internalRulePdfDocumentLoader,
                internalRuleVectorStore,
                internalRuleRagProperties);
    }

    /**
     * 验证正常重建索引时能够先删除旧知识，再写入新的PDF Chunk，并返回正确结果。
     */
    @Test
    void shouldRebuildInternalRuleIndexSuccessfully() {
        // 构造考勤规则Chunk，模拟PDF Loader完成读取和切片后的第一个Document。
        Document attendanceDocument = createDocument(
                "chunk-1",
                "员工每天应在上午9:00之前完成上班打卡，9:00整打卡视为迟到。",
                1);

        // 构造Git规则Chunk，模拟同一PDF切出的第二个Document。
        Document gitRuleDocument = createDocument(
                "chunk-2",
                "禁止直接在master分支进行代码操作。",
                2);

        // List中的Document就是后续真正应该交给VectorStore.add()生成Embedding的数据。
        List<Document> chunkDocumentList = List.of(attendanceDocument, gitRuleDocument);

        when(internalRulePdfDocumentLoader.loadAndSplitDocumentList()).thenReturn(chunkDocumentList);

        // 执行完整的知识索引重建流程。
        InternalRuleKnowledgeIndexDTO indexDTO = internalRuleKnowledgeIndexService.rebuildIndex();

        // 捕获真正交给VectorStore.delete()的Metadata过滤条件。
        ArgumentCaptor<Filter.Expression> filterExpressionCaptor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(internalRuleVectorStore).delete(filterExpressionCaptor.capture());

        // 过滤条件必须真实存在，避免出现没有过滤条件就错误删除知识的情况。
        assertNotNull(filterExpressionCaptor.getValue());

        // 新知识入库必须使用PDF Loader实际返回的同一批Chunk数据。
        verify(internalRuleVectorStore).add(same(chunkDocumentList));

        /*
         * 重建索引最关键的顺序必须是：
         * 先删除当前文档旧知识，再写入新的Chunk。
         * 如果顺序反过来，delete可能把刚写进去的新知识一起删除。
         */
        var orderedInvocation = inOrder(internalRuleVectorStore);
        orderedInvocation.verify(internalRuleVectorStore).delete(any(Filter.Expression.class));
        orderedInvocation.verify(internalRuleVectorStore).add(same(chunkDocumentList));

        // 验证最终DTO正确返回当前知识文档身份、版本以及实际Chunk数量。
        assertAll(
                () -> assertNotNull(indexDTO),
                () -> assertEquals(DOCUMENT_CODE, indexDTO.getDocumentCode()),
                () -> assertEquals(DOCUMENT_VERSION, indexDTO.getDocumentVersion()),
                () -> assertEquals(2, indexDTO.getChunkCount()));
    }

    /**
     * 验证PDF Loader返回null时明确阻断索引构建，不允许执行删除或者写入操作。
     */
    @Test
    void shouldRejectNullChunkDocumentList() {
        // 模拟PDF读取组件异常实现返回null。
        when(internalRulePdfDocumentLoader.loadAndSplitDocumentList()).thenReturn(null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeIndexService.rebuildIndex());

        assertTrue(exception.getMessage().contains("RAG知识索引构建失败"));

        // 没有任何有效Chunk时绝不能删除线上现有知识，否则可能造成整个知识库被清空。
        verifyNoInteractions(internalRuleVectorStore);
    }

    /**
     * 验证PDF没有生成任何Chunk时明确阻断索引构建。
     *
     * <p>这一条非常重要：重建不是“先删再说”，必须确认新知识已经成功生成，
     * 才允许删除当前旧索引。</p>
     */
    @Test
    void shouldRejectEmptyChunkDocumentList() {
        when(internalRulePdfDocumentLoader.loadAndSplitDocumentList()).thenReturn(List.of());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeIndexService.rebuildIndex());

        assertTrue(exception.getMessage().contains("RAG知识索引构建失败"));

        // 新知识为空时不能触碰PGVector中的旧知识。
        verifyNoInteractions(internalRuleVectorStore);
    }

    /**
     * 验证PDF读取或者Chunk切分失败时能够包装成RAG知识索引异常。
     */
    @Test
    void shouldWrapExceptionWhenPdfDocumentLoaderFails() {
        // 模拟PDF文件损坏、classpath资源不存在或者TokenTextSplitter执行异常。
        RuntimeException documentLoadException = new RuntimeException("模拟PDF读取失败");

        when(internalRulePdfDocumentLoader.loadAndSplitDocumentList()).thenThrow(documentLoadException);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeIndexService.rebuildIndex());

        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识索引构建失败")),
                () -> assertNotNull(exception.getCause()),
                () -> assertSame(documentLoadException, exception.getCause()));

        // PDF都没有准备成功时不能访问VectorStore。
        verifyNoInteractions(internalRuleVectorStore);
    }

    /**
     * 验证删除旧PGVector知识失败时立即终止，不允许继续写入新知识。
     */
    @Test
    void shouldStopRebuildWhenDeletingOldKnowledgeFails() {
        Document document = createDocument(
                "chunk-1",
                "员工每天应在上午9:00之前完成上班打卡。",
                1);

        List<Document> chunkDocumentList = List.of(document);

        when(internalRulePdfDocumentLoader.loadAndSplitDocumentList()).thenReturn(chunkDocumentList);

        // 模拟PGVector删除当前documentCode旧知识时数据库发生异常。
        RuntimeException deleteException = new RuntimeException("模拟PGVector删除旧知识失败");
        doThrow(deleteException).when(internalRuleVectorStore).delete(any(Filter.Expression.class));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeIndexService.rebuildIndex());

        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识索引构建失败")),
                () -> assertNotNull(exception.getCause()),
                () -> assertSame(deleteException, exception.getCause()));

        /*
         * 删除失败意味着旧数据状态已经无法确认，
         * 此时禁止继续add新知识，否则可能导致新旧版本同时存在。
         */
        verify(internalRuleVectorStore, never()).add(any());
    }

    /**
     * 验证新知识写入PGVector失败时能够明确暴露系统异常。
     */
    @Test
    void shouldWrapExceptionWhenAddingNewKnowledgeFails() {
        Document document = createDocument(
                "chunk-1",
                "禁止直接在master分支进行代码操作。",
                1);

        List<Document> chunkDocumentList = List.of(document);

        when(internalRulePdfDocumentLoader.loadAndSplitDocumentList()).thenReturn(chunkDocumentList);

        /*
         * 模拟Embedding接口异常、向量维度异常、PostgreSQL连接异常
         * 或PGVector INSERT失败等真实索引写入故障。
         */
        RuntimeException addException = new RuntimeException("模拟PGVector新增知识失败");
        doThrow(addException).when(internalRuleVectorStore).add(same(chunkDocumentList));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeIndexService.rebuildIndex());

        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识索引构建失败")),
                () -> assertNotNull(exception.getCause()),
                () -> assertSame(addException, exception.getCause()));

        // 写入失败之前按照当前重建策略，旧知识删除操作应该已经执行。
        verify(internalRuleVectorStore).delete(any(Filter.Expression.class));
        verify(internalRuleVectorStore).add(same(chunkDocumentList));
    }

    /**
     * 创建索引测试使用的Spring AI Document。
     *
     * @param documentId Document唯一标识
     * @param content Chunk知识正文
     * @param chunkIndex Chunk在当前PDF切片结果中的位置
     * @return 模拟PDF Loader生成的Document
     */
    private Document createDocument(String documentId, String content, Integer chunkIndex) {
        return Document.builder()
                .id(documentId)
                .text(content)
                .metadata(RagDocumentMetadataKeys.DOCUMENT_CODE, DOCUMENT_CODE)
                .metadata(RagDocumentMetadataKeys.DOCUMENT_NAME, DOCUMENT_NAME)
                .metadata(RagDocumentMetadataKeys.DOCUMENT_VERSION, DOCUMENT_VERSION)
                .metadata(RagDocumentMetadataKeys.CHUNK_INDEX, chunkIndex)
                .build();
    }
}
