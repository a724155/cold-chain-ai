package com.ymm.coldchainai.agent.conversation.infrastructure.memory;

import com.ymm.coldchainai.agent.conversation.application.command.QueryAgentChatHistoryCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatHistoryDTO;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatMessageDTO;
import com.ymm.coldchainai.agent.conversation.application.service.IAgentChatHistoryApplicationService;
import com.ymm.coldchainai.agent.conversation.domain.enumtype.ChatMessageRoleEnum;
import com.ymm.coldchainai.agent.conversation.infrastructure.config.AgentChatMemoryProperties;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.memory.enumtype.AgentMemoryMessageRoleEnum;
import com.ymm.coldchainai.agent.core.application.memory.model.AgentMemoryMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent Conversation Memory提供者单元测试。
 *
 * <p>该测试重点验证Chat History到Chat Memory的整理规则，
 * 不连接真实MySQL，也不调用真实模型。</p>
 *
 * <p>主要覆盖：</p>
 *
 * <p>1. 正常串行问答；</p>
 * <p>2. 并发请求导致的回答交叉；</p>
 * <p>3. 模型失败留下的孤立USER；</p>
 * <p>4. 查询窗口截断产生的孤立ASSISTANT；</p>
 * <p>5. 重复USER或ASSISTANT异常数据；</p>
 * <p>6. USER和ASSISTANT原始顺序错误；</p>
 * <p>7. 用户、租户和Memory窗口参数是否正确传递。</p>
 *
 * <p>在挖矿流程中，该测试相当于模拟各种正常和异常档案，
 * 检查档案筛选员是否只把正确配对的任务资料交给智能设备。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentConversationMemoryProviderImplTest {

    /**
     * 测试Conversation业务唯一标识。
     */
    private static final String CONVERSATION_ID = "conv_memory_test";

    /**
     * 测试用户ID。
     */
    private static final Long CURRENT_USER_ID = 10001L;

    /**
     * 测试租户ID。
     */
    private static final Long CURRENT_TENANT_ID = 20001L;

    /**
     * 默认Memory查询消息数量。
     */
    private static final Integer MAX_MEMORY_MESSAGES = 20;

    /**
     * 测试消息统一基础时间。
     *
     * <p>每条消息会根据sequenceNo增加对应秒数，
     * 使测试数据时间顺序直观且稳定。</p>
     */
    private static final LocalDateTime BASE_CREATE_TIME =
            LocalDateTime.of(2026, 7, 31, 3, 0, 0);

    /**
     * Chat History应用服务Mock。
     *
     * <p>测试只关注Memory整理逻辑，因此不访问真实数据库。</p>
     */
    @Mock
    private IAgentChatHistoryApplicationService agentChatHistoryApplicationService;

    /**
     * Chat Memory窗口配置。
     */
    private AgentChatMemoryProperties agentChatMemoryProperties;

    /**
     * 被测试的Memory Provider。
     */
    private AgentConversationMemoryProviderImpl agentConversationMemoryProvider;

    /**
     * 受信任用户和租户上下文。
     */
    private AgentInvocationContext agentInvocationContext;

    /**
     * 每个测试执行前创建独立配置和被测试对象。
     */
    @BeforeEach
    void setUp() {
        // 创建真实配置对象，避免把简单配置Bean也Mock掉而降低测试可读性。
        agentChatMemoryProperties = new AgentChatMemoryProperties();

        // 固定Memory查询窗口，后续测试会验证该值是否正确进入Query Command。
        agentChatMemoryProperties.setMaxMessages(MAX_MEMORY_MESSAGES);

        // 使用Mock服务和真实配置构造被测试Provider。
        agentConversationMemoryProvider = new AgentConversationMemoryProviderImpl(
                agentChatHistoryApplicationService,
                agentChatMemoryProperties);

        // 创建来自后端认证链路的受信任用户和租户上下文。
        agentInvocationContext = AgentInvocationContext.create(
                CURRENT_USER_ID,
                CURRENT_TENANT_ID);
    }

    /**
     * 验证没有历史消息时返回空Memory。
     */
    @Test
    void loadRecentMemory_shouldReturnEmptyList_whenChatHistoryIsEmpty() {
        // 构造存在Conversation但还没有任何聊天消息的历史查询结果。
        AgentChatHistoryDTO chatHistoryDTO =
                AgentChatHistoryDTO.of(CONVERSATION_ID, 0, List.of());

        // Mock Chat History服务返回空消息列表。
        when(agentChatHistoryApplicationService.listRecentMessages(
                any(QueryAgentChatHistoryCommand.class)))
                .thenReturn(chatHistoryDTO);

        // 执行Memory加载。
        List<AgentMemoryMessage> memoryMessageList =
                agentConversationMemoryProvider.loadRecentMemory(
                        CONVERSATION_ID,
                        agentInvocationContext);

        // 没有历史消息时不应向模型注入任何Memory。
        assertTrue(memoryMessageList.isEmpty());
    }

    /**
     * 验证正常串行问答能够按照USER、ASSISTANT顺序进入Memory。
     */
    @Test
    void loadRecentMemory_shouldBuildMemory_whenHistoryContainsCompletedRounds() {
        // 第一轮完整问答。
        AgentChatMessageDTO firstUserMessageDTO = createChatMessageDTO(
                "msg_user_001",
                "req_001",
                ChatMessageRoleEnum.USER,
                "第一轮用户问题",
                1);

        AgentChatMessageDTO firstAssistantMessageDTO = createChatMessageDTO(
                "msg_assistant_001",
                "req_001",
                ChatMessageRoleEnum.ASSISTANT,
                "第一轮模型回答",
                2);

        // 第二轮完整问答。
        AgentChatMessageDTO secondUserMessageDTO = createChatMessageDTO(
                "msg_user_002",
                "req_002",
                ChatMessageRoleEnum.USER,
                "第二轮用户问题",
                3);

        AgentChatMessageDTO secondAssistantMessageDTO = createChatMessageDTO(
                "msg_assistant_002",
                "req_002",
                ChatMessageRoleEnum.ASSISTANT,
                "第二轮模型回答",
                4);

        mockChatHistory(List.of(
                firstUserMessageDTO,
                firstAssistantMessageDTO,
                secondUserMessageDTO,
                secondAssistantMessageDTO));

        // 加载最近完整问答Memory。
        List<AgentMemoryMessage> memoryMessageList =
                agentConversationMemoryProvider.loadRecentMemory(
                        CONVERSATION_ID,
                        agentInvocationContext);

        // 使用assertAll一次展示多个关联断言，任意断言失败时都能看到完整失败信息。
        assertAll(
                () -> assertEquals(4, memoryMessageList.size()),
                () -> assertMemoryMessage(
                        memoryMessageList.get(0),
                        "req_001",
                        AgentMemoryMessageRoleEnum.USER,
                        "第一轮用户问题",
                        1),
                () -> assertMemoryMessage(
                        memoryMessageList.get(1),
                        "req_001",
                        AgentMemoryMessageRoleEnum.ASSISTANT,
                        "第一轮模型回答",
                        2),
                () -> assertMemoryMessage(
                        memoryMessageList.get(2),
                        "req_002",
                        AgentMemoryMessageRoleEnum.USER,
                        "第二轮用户问题",
                        3),
                () -> assertMemoryMessage(
                        memoryMessageList.get(3),
                        "req_002",
                        AgentMemoryMessageRoleEnum.ASSISTANT,
                        "第二轮模型回答",
                        4));
    }

    /**
     * 验证并发请求回答顺序交叉时，仍然根据requestId恢复正确问答关系。
     *
     * <p>数据库真实顺序为：</p>
     *
     * <p>USER-A → USER-B → ASSISTANT-B → ASSISTANT-A。</p>
     *
     * <p>模型Memory逻辑顺序应为：</p>
     *
     * <p>USER-A → ASSISTANT-A → USER-B → ASSISTANT-B。</p>
     */
    @Test
    void loadRecentMemory_shouldPairByRequestId_whenConcurrentAnswersAreInterleaved() {
        // 请求A先保存USER问题。
        AgentChatMessageDTO userMessageADTO = createChatMessageDTO(
                "msg_user_a",
                "req_a",
                ChatMessageRoleEnum.USER,
                "用户问题A",
                1);

        // 请求B随后保存USER问题。
        AgentChatMessageDTO userMessageBDTO = createChatMessageDTO(
                "msg_user_b",
                "req_b",
                ChatMessageRoleEnum.USER,
                "用户问题B",
                2);

        // 请求B先执行完成，因此ASSISTANT-B先落库。
        AgentChatMessageDTO assistantMessageBDTO = createChatMessageDTO(
                "msg_assistant_b",
                "req_b",
                ChatMessageRoleEnum.ASSISTANT,
                "模型回答B",
                3);

        // 请求A后执行完成，因此ASSISTANT-A最后落库。
        AgentChatMessageDTO assistantMessageADTO = createChatMessageDTO(
                "msg_assistant_a",
                "req_a",
                ChatMessageRoleEnum.ASSISTANT,
                "模型回答A",
                4);

        mockChatHistory(List.of(
                userMessageADTO,
                userMessageBDTO,
                assistantMessageBDTO,
                assistantMessageADTO));

        // 加载Memory时应根据requestId恢复回答归属，而不是机械按照消息相邻位置配对。
        List<AgentMemoryMessage> memoryMessageList =
                agentConversationMemoryProvider.loadRecentMemory(
                        CONVERSATION_ID,
                        agentInvocationContext);

        assertAll(
                () -> assertEquals(4, memoryMessageList.size()),
                () -> assertMemoryMessage(
                        memoryMessageList.get(0),
                        "req_a",
                        AgentMemoryMessageRoleEnum.USER,
                        "用户问题A",
                        1),
                () -> assertMemoryMessage(
                        memoryMessageList.get(1),
                        "req_a",
                        AgentMemoryMessageRoleEnum.ASSISTANT,
                        "模型回答A",
                        4),
                () -> assertMemoryMessage(
                        memoryMessageList.get(2),
                        "req_b",
                        AgentMemoryMessageRoleEnum.USER,
                        "用户问题B",
                        2),
                () -> assertMemoryMessage(
                        memoryMessageList.get(3),
                        "req_b",
                        AgentMemoryMessageRoleEnum.ASSISTANT,
                        "模型回答B",
                        3));
    }

    /**
     * 验证孤立USER和孤立ASSISTANT不会进入模型Memory。
     */
    @Test
    void loadRecentMemory_shouldIgnoreOrphanMessages_whenRoundIsIncomplete() {
        /*
         * ASSISTANT-X对应的USER可能已经被最近N条窗口截断，
         * USER-Y则可能因为模型调用失败而没有产生ASSISTANT。
         */
        AgentChatMessageDTO orphanAssistantMessageDTO = createChatMessageDTO(
                "msg_assistant_x",
                "req_x",
                ChatMessageRoleEnum.ASSISTANT,
                "缺少USER的问题回答",
                1);

        AgentChatMessageDTO orphanUserMessageDTO = createChatMessageDTO(
                "msg_user_y",
                "req_y",
                ChatMessageRoleEnum.USER,
                "模型执行失败的问题",
                2);

        mockChatHistory(List.of(
                orphanAssistantMessageDTO,
                orphanUserMessageDTO));

        // 不完整问答保留在Chat History用于审计，但不能污染模型Memory。
        List<AgentMemoryMessage> memoryMessageList =
                agentConversationMemoryProvider.loadRecentMemory(
                        CONVERSATION_ID,
                        agentInvocationContext);

        assertTrue(memoryMessageList.isEmpty());
    }

    /**
     * 验证同一requestId出现重复USER时明确抛出异常。
     */
    @Test
    void loadRecentMemory_shouldThrowException_whenRequestContainsDuplicateUserMessages() {
        AgentChatMessageDTO firstUserMessageDTO = createChatMessageDTO(
                "msg_user_001",
                "req_duplicate_user",
                ChatMessageRoleEnum.USER,
                "第一个USER问题",
                1);

        AgentChatMessageDTO duplicateUserMessageDTO = createChatMessageDTO(
                "msg_user_002",
                "req_duplicate_user",
                ChatMessageRoleEnum.USER,
                "重复USER问题",
                2);

        mockChatHistory(List.of(
                firstUserMessageDTO,
                duplicateUserMessageDTO));

        // 同一次requestId正常情况下只能有一个USER，重复数据不能静默覆盖。
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> agentConversationMemoryProvider.loadRecentMemory(
                        CONVERSATION_ID,
                        agentInvocationContext));

        assertTrue(exception.getMessage().contains("重复USER消息"));
    }

    /**
     * 验证同一requestId出现重复ASSISTANT时明确抛出异常。
     */
    @Test
    void loadRecentMemory_shouldThrowException_whenRequestContainsDuplicateAssistantMessages() {
        AgentChatMessageDTO userMessageDTO = createChatMessageDTO(
                "msg_user_001",
                "req_duplicate_assistant",
                ChatMessageRoleEnum.USER,
                "用户问题",
                1);

        AgentChatMessageDTO firstAssistantMessageDTO = createChatMessageDTO(
                "msg_assistant_001",
                "req_duplicate_assistant",
                ChatMessageRoleEnum.ASSISTANT,
                "第一个模型回答",
                2);

        AgentChatMessageDTO duplicateAssistantMessageDTO = createChatMessageDTO(
                "msg_assistant_002",
                "req_duplicate_assistant",
                ChatMessageRoleEnum.ASSISTANT,
                "重复模型回答",
                3);

        mockChatHistory(List.of(
                userMessageDTO,
                firstAssistantMessageDTO,
                duplicateAssistantMessageDTO));

        // 同一次requestId只能保存一个最终ASSISTANT回答，重复记录属于异常数据。
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> agentConversationMemoryProvider.loadRecentMemory(
                        CONVERSATION_ID,
                        agentInvocationContext));

        assertTrue(exception.getMessage().contains("重复ASSISTANT消息"));
    }

    /**
     * 验证ASSISTANT原始顺序早于对应USER时明确抛出异常。
     */
    @Test
    void loadRecentMemory_shouldThrowException_whenAssistantSequenceIsBeforeUser() {
        // 构造同一requestId下ASSISTANT先于USER的非法历史数据。
        AgentChatMessageDTO assistantMessageDTO = createChatMessageDTO(
                "msg_assistant_001",
                "req_invalid_sequence",
                ChatMessageRoleEnum.ASSISTANT,
                "顺序错误的模型回答",
                1);

        AgentChatMessageDTO userMessageDTO = createChatMessageDTO(
                "msg_user_001",
                "req_invalid_sequence",
                ChatMessageRoleEnum.USER,
                "顺序错误的用户问题",
                2);

        mockChatHistory(List.of(
                assistantMessageDTO,
                userMessageDTO));

        // 模型回答不能在对应用户问题之前产生。
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> agentConversationMemoryProvider.loadRecentMemory(
                        CONVERSATION_ID,
                        agentInvocationContext));

        assertTrue(exception.getMessage().contains("ASSISTANT消息顺序不能早于对应USER消息"));
    }

    /**
     * 验证查询窗口和受信任身份被正确传入Chat History查询命令。
     */
    @Test
    void loadRecentMemory_shouldUseConfiguredLimitAndTrustedContext() {
        // 本测试只关注Command参数，因此返回空历史即可。
        when(agentChatHistoryApplicationService.listRecentMessages(
                any(QueryAgentChatHistoryCommand.class)))
                .thenReturn(AgentChatHistoryDTO.of(
                        CONVERSATION_ID,
                        0,
                        List.of()));

        agentConversationMemoryProvider.loadRecentMemory(
                CONVERSATION_ID,
                agentInvocationContext);

        // 捕获Provider实际提交给Chat History服务的查询命令。
        ArgumentCaptor<QueryAgentChatHistoryCommand> commandCaptor =
                ArgumentCaptor.forClass(QueryAgentChatHistoryCommand.class);

        verify(agentChatHistoryApplicationService)
                .listRecentMessages(commandCaptor.capture());

        QueryAgentChatHistoryCommand capturedCommand =
                commandCaptor.getValue();

        assertAll(
                () -> assertEquals(
                        CONVERSATION_ID,
                        capturedCommand.getConversationId()),
                () -> assertEquals(
                        MAX_MEMORY_MESSAGES,
                        capturedCommand.getLimit()),
                () -> assertEquals(
                        CURRENT_USER_ID,
                        capturedCommand.getAgentInvocationContext().getCurrentUserId()),
                () -> assertEquals(
                        CURRENT_TENANT_ID,
                        capturedCommand.getAgentInvocationContext().getCurrentTenantId()));
    }

    /**
     * Mock Chat History服务返回指定消息列表。
     *
     * @param chatMessageDTOList 按数据库sequenceNo顺序构造的测试消息
     */
    private void mockChatHistory(List<AgentChatMessageDTO> chatMessageDTOList) {
        // DTO中的returnedMessageCount必须与测试消息实际数量保持一致。
        AgentChatHistoryDTO chatHistoryDTO = AgentChatHistoryDTO.of(
                CONVERSATION_ID,
                chatMessageDTOList.size(),
                chatMessageDTOList);

        // Provider无论传入哪个合法Query Command，都返回当前测试准备的历史数据。
        when(agentChatHistoryApplicationService.listRecentMessages(
                any(QueryAgentChatHistoryCommand.class)))
                .thenReturn(chatHistoryDTO);
    }

    /**
     * 创建一条Chat History测试消息。
     *
     * @param messageId 消息业务唯一标识
     * @param requestId 消息所属Agent请求标识
     * @param messageRole 消息角色
     * @param messageContent 消息正文
     * @param sequenceNo Conversation内原始顺序号
     * @return 聊天消息Application DTO
     */
    private AgentChatMessageDTO createChatMessageDTO(
            String messageId,
            String requestId,
            ChatMessageRoleEnum messageRole,
            String messageContent,
            Integer sequenceNo) {

        /*
         * 创建时间根据sequenceNo递增。
         * 该时间当前不参与Memory配对，但保留完整DTO数据更接近真实查询结果。
         */
        LocalDateTime createTime =
                BASE_CREATE_TIME.plusSeconds(sequenceNo);

        return AgentChatMessageDTO.of(
                messageId,
                CONVERSATION_ID,
                requestId,
                messageRole.getCode(),
                messageRole.getMessage(),
                messageContent,
                sequenceNo,
                createTime);
    }

    /**
     * 统一校验单条Memory消息。
     *
     * @param memoryMessage 实际Memory消息
     * @param expectedRequestId 预期requestId
     * @param expectedMessageRole 预期消息角色
     * @param expectedMessageContent 预期消息正文
     * @param expectedSequenceNo 预期原始数据库顺序号
     */
    private void assertMemoryMessage(
            AgentMemoryMessage memoryMessage,
            String expectedRequestId,
            AgentMemoryMessageRoleEnum expectedMessageRole,
            String expectedMessageContent,
            Integer expectedSequenceNo) {

        assertAll(
                () -> assertEquals(
                        expectedRequestId,
                        memoryMessage.getRequestId()),
                () -> assertEquals(
                        expectedMessageRole,
                        memoryMessage.getMessageRole()),
                () -> assertEquals(
                        expectedMessageContent,
                        memoryMessage.getMessageContent()),
                () -> assertEquals(
                        expectedSequenceNo,
                        memoryMessage.getSequenceNo()));
    }
}