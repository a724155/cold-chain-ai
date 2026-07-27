package com.ymm.coldchainai.rag.knowledge.infrastructure.answer;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeAnswerDTO;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeAnswerQuery;
import com.ymm.coldchainai.rag.knowledge.infrastructure.config.InternalRuleRagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 满帮内部规范RAG问答服务单元测试。
 *
 * <p>本测试只验证InternalRuleKnowledgeAnswerServiceImpl自身的Application执行逻辑，
 * 不启动Spring容器，不连接PGVector，也不会真实调用百炼ChatModel。</p>
 *
 * <p>ChatClient以及其链式调用对象全部通过Mockito模拟，
 * 从而验证问题传递、答案转换、知识版本返回以及异常处理是否稳定。</p>
 */
@ExtendWith(MockitoExtension.class)
class InternalRuleKnowledgeAnswerServiceImplTest {

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
     * Mock内部规范专属RAG ChatClient，避免单元测试真实调用大模型。
     */
    @Mock
    private ChatClient internalRuleRagChatClient;

    /**
     * Mock ChatClient.prompt()返回的请求构建对象。
     *
     * <p>真实代码会继续在该对象上调用user()和call()。</p>
     */
    @Mock
    private ChatClient.ChatClientRequestSpec chatClientRequestSpec;

    /**
     * Mock ChatClient.call()返回的同步响应对象。
     *
     * <p>真实代码最终通过content()读取模型生成的字符串答案。</p>
     */
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    /**
     * 内部规范文档配置。
     */
    private InternalRuleRagProperties internalRuleRagProperties;

    /**
     * 当前需要测试的RAG问答服务。
     */
    private InternalRuleKnowledgeAnswerServiceImpl internalRuleKnowledgeAnswerService;

    /**
     * 每个测试执行前重新创建配置和被测Service，避免不同测试之间共享状态。
     */
    @BeforeEach
    void setUp() {
        // 创建内部规范配置，Answer Service会把documentCode和documentVersion写入最终DTO。
        internalRuleRagProperties = new InternalRuleRagProperties();
        internalRuleRagProperties.setDocumentCode(DOCUMENT_CODE);
        internalRuleRagProperties.setDocumentName(DOCUMENT_NAME);
        internalRuleRagProperties.setDocumentVersion(DOCUMENT_VERSION);

        // 使用Lombok生成的构造方法直接创建被测对象，不启动Spring容器。
        internalRuleKnowledgeAnswerService = new InternalRuleKnowledgeAnswerServiceImpl(
                internalRuleRagChatClient,
                internalRuleRagProperties);
    }

    /**
     * 验证正常RAG问答时能够把用户问题交给ChatClient，并正确生成最终DTO。
     */
    @Test
    void shouldAnswerInternalRuleQuestionSuccessfully() {
        String question = "我上午9点整打卡算迟到吗？";
        String expectedAnswer = "算迟到。内部规范要求员工在上午9:00之前完成打卡，9:00整已经视为迟到。";

        // 模拟ChatClient.prompt()开始一次新的同步模型请求。
        when(internalRuleRagChatClient.prompt()).thenReturn(chatClientRequestSpec);

        // 模拟把用户原始问题写入Prompt后继续返回同一个RequestSpec进行链式调用。
        when(chatClientRequestSpec.user(question)).thenReturn(chatClientRequestSpec);

        // 模拟call()发起同步模型调用并返回响应读取对象。
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);

        // 模拟QuestionAnswerAdvisor完成检索增强以后，ChatModel最终生成正确答案。
        when(callResponseSpec.content()).thenReturn(expectedAnswer);

        InternalRuleKnowledgeAnswerQuery answerQuery = InternalRuleKnowledgeAnswerQuery.create(question);

        // 执行真正需要测试的RAG Answer Service。
        InternalRuleKnowledgeAnswerDTO answerDTO = internalRuleKnowledgeAnswerService.answer(answerQuery);

        // 捕获真正传递给ChatClient的用户问题，确认Service没有私自修改问题内容。
        ArgumentCaptor<String> questionCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatClientRequestSpec).user(questionCaptor.capture());

        assertEquals(question, questionCaptor.getValue());

        // 验证同步ChatClient调用链每一步都实际发生。
        verify(internalRuleRagChatClient).prompt();
        verify(chatClientRequestSpec).call();
        verify(callResponseSpec).content();

        // 验证最终DTO正确保留问题、模型答案以及知识文档身份和版本。
        assertAll(
                () -> assertNotNull(answerDTO),
                () -> assertEquals(question, answerDTO.getQuestion()),
                () -> assertEquals(expectedAnswer, answerDTO.getAnswer()),
                () -> assertEquals(DOCUMENT_CODE, answerDTO.getDocumentCode()),
                () -> assertEquals(DOCUMENT_VERSION, answerDTO.getDocumentVersion()));
    }

    /**
     * 验证AnswerQuery创建阶段清理首尾空白后，ChatClient收到的是规范化问题。
     */
    @Test
    void shouldUseTrimmedQuestionWhenCallingChatClient() {
        String trimmedQuestion = "每个月最后一个周六需要上班吗？";

        when(internalRuleRagChatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(trimmedQuestion)).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("需要。内部规范规定每个月最后一个周六正常上班。");

        // 故意在原始问题前后加入空白，验证Query对象负责统一标准化。
        InternalRuleKnowledgeAnswerQuery answerQuery = InternalRuleKnowledgeAnswerQuery.create("  每个月最后一个周六需要上班吗？  ");

        internalRuleKnowledgeAnswerService.answer(answerQuery);

        // Service最终交给模型的必须是已经trim后的问题。
        verify(chatClientRequestSpec).user(trimmedQuestion);
    }

    /**
     * 验证AnswerQuery本身为空时立即阻断，不允许继续调用ChatClient。
     */
    @Test
    void shouldRejectNullAnswerQuery() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> internalRuleKnowledgeAnswerService.answer(null));

        assertEquals("内部规范RAG问答查询对象不能为空", exception.getMessage());

        // 参数已经非法时绝不能继续调用模型。
        verifyNoInteractions(internalRuleRagChatClient);
    }

    /**
     * 验证ChatModel返回null内容时明确按照RAG问答异常处理，而不是继续返回非法DTO。
     */
    @Test
    void shouldRejectNullModelAnswer() {
        String question = "我下午6点30分整打卡下班符合规定吗？";

        when(internalRuleRagChatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(question)).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);

        // 模拟模型调用完成但content异常返回null。
        when(callResponseSpec.content()).thenReturn(null);

        InternalRuleKnowledgeAnswerQuery answerQuery = InternalRuleKnowledgeAnswerQuery.create(question);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeAnswerService.answer(answerQuery));

        /*
         * Answer Service会把内部“模型返回内容为空”异常包装成统一RAG问答异常，
         * 因此既要检查上层业务语义，也要保留底层具体失败原因。
         */
        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识问答失败")),
                () -> assertNotNull(exception.getCause()),
                () -> assertEquals("内部规范RAG模型返回内容为空", exception.getCause().getMessage()));
    }

    /**
     * 验证ChatModel返回纯空白内容时同样不能认为问答成功。
     */
    @Test
    void shouldRejectBlankModelAnswer() {
        String question = "可以直接在master分支修改代码吗？";

        when(internalRuleRagChatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(question)).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);

        // StringUtils.isBlank可以安全识别纯空白模型结果。
        when(callResponseSpec.content()).thenReturn("   ");

        InternalRuleKnowledgeAnswerQuery answerQuery = InternalRuleKnowledgeAnswerQuery.create(question);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeAnswerService.answer(answerQuery));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识问答失败")),
                () -> assertNotNull(exception.getCause()),
                () -> assertEquals("内部规范RAG模型返回内容为空", exception.getCause().getMessage()));
    }

    /**
     * 验证ChatClient调用大模型发生异常时统一包装成RAG问答异常，并保留真正的原始cause。
     */
    @Test
    void shouldWrapExceptionWhenChatClientFails() {
        String question = "系统问题造成8000元资金损失属于几级事故？";

        // 模拟百炼模型超时、HTTP失败或者Advisor链路执行失败。
        RuntimeException chatClientException = new RuntimeException("模拟ChatClient调用失败");

        when(internalRuleRagChatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(question)).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenThrow(chatClientException);

        InternalRuleKnowledgeAnswerQuery answerQuery = InternalRuleKnowledgeAnswerQuery.create(question);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeAnswerService.answer(answerQuery));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识问答失败")),
                () -> assertSame(chatClientException, exception.getCause()));
    }

    /**
     * 验证模型响应读取阶段发生异常时同样能够保留原始异常。
     *
     * <p>call()成功并不代表content()读取永远成功，
     * 因此模型响应转换阶段也必须包含在系统异常保护范围内。</p>
     */
    @Test
    void shouldWrapExceptionWhenReadingModelContentFails() {
        String question = "离职时公司电脑损坏了需要赔偿吗？";

        RuntimeException contentException = new RuntimeException("模拟模型响应内容读取失败");

        when(internalRuleRagChatClient.prompt()).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.user(question)).thenReturn(chatClientRequestSpec);
        when(chatClientRequestSpec.call()).thenReturn(callResponseSpec);

        // 模拟同步模型调用完成，但读取最终content时发生框架异常。
        when(callResponseSpec.content()).thenThrow(contentException);

        InternalRuleKnowledgeAnswerQuery answerQuery = InternalRuleKnowledgeAnswerQuery.create(question);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> internalRuleKnowledgeAnswerService.answer(answerQuery));

        assertAll(
                () -> assertTrue(exception.getMessage().contains("RAG知识问答失败")),
                () -> assertSame(contentException, exception.getCause()));
    }
}
