package com.ymm.coldchainai.agent.scenario.knowledge.interfaces.tool;

import com.ymm.coldchainai.agent.scenario.knowledge.interfaces.tool.response.InternalRuleKnowledgeQueryToolItemResponse;
import com.ymm.coldchainai.agent.scenario.knowledge.interfaces.tool.response.InternalRuleKnowledgeQueryToolResponse;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchDTO;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchItemDTO;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeSearchQuery;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * 满帮内部规范知识查询Tool单元测试。
 *
 * <p>本测试只验证Agent Tool边界逻辑，不连接真实PGVector，也不会调用EmbeddingModel或者ChatModel。
 * IInternalRuleKnowledgeSearchService通过Mockito模拟，从而只验证Tool参数转换、Service调用、
 * Tool Response转换以及各种异常数据防御。</p>
 */
@ExtendWith(MockitoExtension.class)
class InternalRuleKnowledgeQueryToolTest {

    /**
     * 测试使用的知识文档编码。
     */
    private static final String DOCUMENT_CODE = "mmb-internal-rules";

    /**
     * 测试使用的知识文档名称。
     */
    private static final String DOCUMENT_NAME = "满帮集团内部规范文档";

    /**
     * 测试使用的知识文档版本。
     */
    private static final String DOCUMENT_VERSION = "V1.0";

    /**
     * Mock内部规范知识检索Application Service，避免Tool单测真实访问向量数据库。
     */
    @Mock
    private IInternalRuleKnowledgeSearchService internalRuleKnowledgeSearchService;

    /**
     * 当前需要测试的内部规范知识查询Tool。
     */
    private InternalRuleKnowledgeQueryTool internalRuleKnowledgeQueryTool;

    /**
     * 每个测试执行前重新创建Tool，保证不同测试之间没有共享状态。
     */
    @BeforeEach
    void setUp() {
        // Tool只有一个Application Service依赖，因此直接通过Lombok生成的构造方法创建被测对象。
        internalRuleKnowledgeQueryTool = new InternalRuleKnowledgeQueryTool(internalRuleKnowledgeSearchService);
    }

    /**
     * 验证内部规范Tool正常调用Application Service并正确转换知识检索结果。
     */
    @Test
    void shouldQueryInternalRulesSuccessfully() {
        // 构造第一名考勤知识Chunk，模拟PGVector已经完成的检索结果。
        InternalRuleKnowledgeSearchItemDTO attendanceItemDTO = InternalRuleKnowledgeSearchItemDTO.of(
                1,
                0.93D,
                "员工每天应在上午9:00之前完成上班打卡，9:00整打卡视为迟到。",
                DOCUMENT_CODE,
                DOCUMENT_NAME,
                DOCUMENT_VERSION,
                1);

        // 构造第二名工作日知识Chunk，用于验证多结果转换顺序不会发生变化。
        InternalRuleKnowledgeSearchItemDTO workdayItemDTO = InternalRuleKnowledgeSearchItemDTO.of(
                2,
                0.84D,
                "公司原则上周六周日双休，但每个月最后一个周六需要正常上班。",
                DOCUMENT_CODE,
                DOCUMENT_NAME,
                DOCUMENT_VERSION,
                2);

        // 模拟Application Service返回已经完成向量检索的结果。
        InternalRuleKnowledgeSearchDTO searchDTO = InternalRuleKnowledgeSearchDTO.of(
                "我上午9点整打卡算迟到吗？",
                5,
                0.0D,
                2,
                List.of(attendanceItemDTO, workdayItemDTO));

        when(internalRuleKnowledgeSearchService.search(any(InternalRuleKnowledgeSearchQuery.class))).thenReturn(searchDTO);

        // 直接调用真正暴露给Spring AI的Tool方法。
        InternalRuleKnowledgeQueryToolResponse response = internalRuleKnowledgeQueryTool.queryInternalRules("我上午9点整打卡算迟到吗？");

        // 捕获Tool实际交给Application Service的Query，确认问题没有在Tool层被错误修改。
        ArgumentCaptor<InternalRuleKnowledgeSearchQuery> searchQueryCaptor = ArgumentCaptor.forClass(InternalRuleKnowledgeSearchQuery.class);
        verify(internalRuleKnowledgeSearchService).search(searchQueryCaptor.capture());

        InternalRuleKnowledgeSearchQuery actualSearchQuery = searchQueryCaptor.getValue();

        assertAll(
                () -> assertNotNull(actualSearchQuery),
                () -> assertEquals("我上午9点整打卡算迟到吗？", actualSearchQuery.getQuery()));

        // 验证Tool整体响应完整保留文档身份、版本以及实际知识Chunk数量。
        assertAll(
                () -> assertNotNull(response),
                () -> assertEquals("我上午9点整打卡算迟到吗？", response.getQuery()),
                () -> assertEquals(DOCUMENT_CODE, response.getDocumentCode()),
                () -> assertEquals(DOCUMENT_VERSION, response.getDocumentVersion()),
                () -> assertEquals(2, response.getResultCount()),
                () -> assertEquals(2, response.getResultItemList().size()));

        // 验证第一名知识Chunk的排名、Score、正文和Chunk位置全部正确。
        InternalRuleKnowledgeQueryToolItemResponse firstItemResponse = response.getResultItemList().getFirst();

        assertAll(
                () -> assertEquals(1, firstItemResponse.getRank()),
                () -> assertEquals(0.93D, firstItemResponse.getScore()),
                () -> assertEquals("员工每天应在上午9:00之前完成上班打卡，9:00整打卡视为迟到。", firstItemResponse.getContent()),
                () -> assertEquals(1, firstItemResponse.getChunkIndex()));

        // 验证第二名知识Chunk仍然保持原始检索顺序。
        InternalRuleKnowledgeQueryToolItemResponse secondItemResponse = response.getResultItemList().get(1);

        assertAll(
                () -> assertEquals(2, secondItemResponse.getRank()),
                () -> assertEquals(0.84D, secondItemResponse.getScore()),
                () -> assertEquals(2, secondItemResponse.getChunkIndex()));
    }

    /**
     * 验证Tool会通过Application Query统一去除用户问题首尾空白。
     */
    @Test
    void shouldTrimQuestionBeforeCallingApplicationService() {
        // Service返回正常空检索结果，本测试只关注Tool传进去的问题是否已经trim。
        InternalRuleKnowledgeSearchDTO searchDTO = InternalRuleKnowledgeSearchDTO.of(
                "我上午9点整打卡算迟到吗？",
                5,
                0.0D,
                0,
                List.of());

        when(internalRuleKnowledgeSearchService.search(any(InternalRuleKnowledgeSearchQuery.class))).thenReturn(searchDTO);

        // 故意在问题前后加入空格，模拟模型Tool参数存在多余空白。
        internalRuleKnowledgeQueryTool.queryInternalRules("  我上午9点整打卡算迟到吗？  ");

        // 捕获Application Service真正收到的Query。
        ArgumentCaptor<InternalRuleKnowledgeSearchQuery> searchQueryCaptor = ArgumentCaptor.forClass(InternalRuleKnowledgeSearchQuery.class);
        verify(internalRuleKnowledgeSearchService).search(searchQueryCaptor.capture());

        // InternalRuleKnowledgeSearchQuery.create()应该已经统一清理首尾空格。
        assertEquals("我上午9点整打卡算迟到吗？", searchQueryCaptor.getValue().getQuery());
    }

    /**
     * 验证null问题在进入Application Service之前立即失败。
     */
    @Test
    void shouldRejectNullQuestion() {
        // StringUtils.isBlank(null)会返回true，因此Tool能够安全阻断null参数。
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> internalRuleKnowledgeQueryTool.queryInternalRules(null));

        assertEquals("内部规范知识查询Tool问题不能为空", exception.getMessage());

        // 参数非法时绝不能继续调用Application Service或者PGVector链路。
        verifyNoInteractions(internalRuleKnowledgeSearchService);
    }

    /**
     * 验证纯空白问题在进入Application Service之前立即失败。
     */
    @Test
    void shouldRejectBlankQuestion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> internalRuleKnowledgeQueryTool.queryInternalRules("   "));

        assertEquals("内部规范知识查询Tool问题不能为空", exception.getMessage());

        // Tool边界已经发现参数问题，不允许继续执行任何知识检索。
        verifyNoInteractions(internalRuleKnowledgeSearchService);
    }

    /**
     * 验证Application Service异常返回null DTO时Tool Response转换能够明确失败，而不是产生裸NPE。
     */
    @Test
    void shouldRejectNullSearchDTO() {
        // 模拟未来某个错误Service实现违反接口约定返回null。
        when(internalRuleKnowledgeSearchService.search(any(InternalRuleKnowledgeSearchQuery.class))).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> internalRuleKnowledgeQueryTool.queryInternalRules("9点整打卡算迟到吗？"));

        assertEquals("内部规范知识查询Tool检索DTO不能为空", exception.getMessage());

        // 虽然返回数据异常，但Service本身应该已经被正常调用一次。
        verify(internalRuleKnowledgeSearchService).search(any(InternalRuleKnowledgeSearchQuery.class));
    }

    /**
     * 验证Application Service返回null结果列表时能够安全转换为空Tool响应。
     */
    @Test
    void shouldReturnEmptyToolResultWhenResultItemListIsNull() {
        /*
         * 主动构造resultItemList=null的异常DTO，
         * 用于验证Tool Response不能假设所有Application实现永远返回非空List。
         */
        InternalRuleKnowledgeSearchDTO searchDTO = InternalRuleKnowledgeSearchDTO.of(
                "公司食堂几点关门？",
                5,
                0.0D,
                0,
                null);

        when(internalRuleKnowledgeSearchService.search(any(InternalRuleKnowledgeSearchQuery.class))).thenReturn(searchDTO);

        InternalRuleKnowledgeQueryToolResponse response = internalRuleKnowledgeQueryTool.queryInternalRules("公司食堂几点关门？");

        assertAll(
                () -> assertNotNull(response),
                () -> assertEquals(0, response.getResultCount()),
                () -> assertNotNull(response.getResultItemList()),
                () -> assertTrue(response.getResultItemList().isEmpty()),
                () -> assertNull(response.getDocumentCode()),
                () -> assertNull(response.getDocumentVersion()));
    }

    /**
     * 验证检索结果列表包含null元素时明确失败，不能静默把脏数据丢掉。
     */
    @Test
    void shouldRejectNullItemInSearchResultList() {
        // 先构造一个完全合法的知识Chunk。
        InternalRuleKnowledgeSearchItemDTO validItemDTO = InternalRuleKnowledgeSearchItemDTO.of(
                1,
                0.91D,
                "禁止直接在master分支进行代码操作。",
                DOCUMENT_CODE,
                DOCUMENT_NAME,
                DOCUMENT_VERSION,
                1);

        // ArrayList允许放入null，用于模拟异常Application Service返回脏数据。
        List<InternalRuleKnowledgeSearchItemDTO> resultItemList = new ArrayList<>();
        resultItemList.add(validItemDTO);
        resultItemList.add(null);

        InternalRuleKnowledgeSearchDTO searchDTO = InternalRuleKnowledgeSearchDTO.of(
                "可以直接在master分支修改代码吗？",
                5,
                0.0D,
                2,
                resultItemList);

        when(internalRuleKnowledgeSearchService.search(any(InternalRuleKnowledgeSearchQuery.class))).thenReturn(searchDTO);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> internalRuleKnowledgeQueryTool.queryInternalRules("可以直接在master分支修改代码吗？"));

        assertTrue(exception.getMessage().contains("内部规范知识查询Tool结果元素不能为空"));
    }

    /**
     * 验证Application Service发生系统异常时Tool不会吞掉原始异常。
     *
     * <p>Tool层没有能力修复PGVector或者Embedding故障，
     * 因此应该保留原始异常继续交给Spring AI Tool Calling异常处理链。</p>
     */
    @Test
    void shouldPropagateExceptionWhenKnowledgeSearchServiceFails() {
        // 模拟底层Embedding或者PGVector检索失败。
        IllegalStateException searchException = new IllegalStateException("模拟RAG知识检索失败");

        when(internalRuleKnowledgeSearchService.search(any(InternalRuleKnowledgeSearchQuery.class))).thenThrow(searchException);

        // Tool不应该重新包装成一个丢失cause的新异常。
        IllegalStateException actualException = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeQueryTool.queryInternalRules("9点整打卡算迟到吗？"));

        // 必须仍然是Application Service原始抛出的同一个异常实例。
        assertSame(searchException, actualException);
    }
}
