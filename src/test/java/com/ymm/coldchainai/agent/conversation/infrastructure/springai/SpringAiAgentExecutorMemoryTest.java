package com.ymm.coldchainai.agent.core.infrastructure.springai;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.memory.enumtype.AgentMemoryMessageRoleEnum;
import com.ymm.coldchainai.agent.core.application.memory.model.AgentMemoryMessage;
import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.infrastructure.springai.model.SpringAiAgentRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SpringAiAgentExecutor Chat Memory专项单元测试。
 *
 * <p>该测试只验证Application层Memory进入Spring AI执行器后的框架转换和Prompt注入，
 * 不连接真实模型、不访问MySQL，也不执行真实Tool。</p>
 *
 * <p>主要覆盖：</p>
 *
 * <p>1. USER Memory是否转换为Spring AI USER消息；</p>
 * <p>2. ASSISTANT Memory是否转换为Spring AI ASSISTANT消息；</p>
 * <p>3. 历史消息顺序是否保持不变；</p>
 * <p>4. 当前question是否仍然通过user(question)单独加入；</p>
 * <p>5. 空Memory或者null Memory是否跳过messages()调用；</p>
 * <p>6. Memory List包含null元素时是否在模型调用前明确失败。</p>
 *
 * <p>在挖矿流程中，该测试相当于检查设备操作员：
 * 是否把客户历史要求和矿场历史报告贴上正确标签后装入设备，
 * 同时保证本轮新任务不会被错误放进历史资料中。</p>
 */
@ExtendWith(MockitoExtension.class)
class SpringAiAgentExecutorMemoryTest {

    /**
     * 测试Agent稳定编码。
     */
    private static final String AGENT_CODE = "cold-chain-general";

    /**
     * 测试Agent名称。
     */
    private static final String AGENT_NAME = "冷运综合业务助手";

    /**
     * 测试Agent能力说明。
     */
    private static final String AGENT_DESCRIPTION = "负责冷运综合业务问答";

    /**
     * 本轮Agent请求唯一标识。
     */
    private static final String REQUEST_ID = "req_memory_executor_001";

    /**
     * 当前认证用户ID。
     */
    private static final Long CURRENT_USER_ID = 10001L;

    /**
     * 当前认证租户ID。
     */
    private static final Long CURRENT_TENANT_ID = 20001L;

    /**
     * 第一条历史USER消息正文。
     */
    private static final String HISTORY_USER_MESSAGE = "本轮测试代号是蓝色矿灯";

    /**
     * 第一条历史ASSISTANT消息正文。
     */
    private static final String HISTORY_ASSISTANT_MESSAGE = "已记录";

    /**
     * 本轮最新用户问题。
     */
    private static final String CURRENT_QUESTION = "刚才的测试代号是什么？";

    /**
     * 模型Mock返回答案。
     */
    private static final String MODEL_ANSWER = "蓝色矿灯";

    /**
     * Agent注册中心Mock。
     *
     * <p>SpringAiAgentExecutor构造时会检查已启用Agent是否存在对应运行配置。</p>
     */
    @Mock
    private IAgentRegistry agentRegistry;

    /**
     * 当前Agent专属ChatClient Mock。
     */
    @Mock
    private ChatClient chatClient;

    /**
     * ChatClient链式请求规格Mock。
     *
     * <p>prompt()、messages()、advisors()、toolContext()和user()
     * 都会返回该对象继续执行链式调用。</p>
     */
    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    /**
     * ChatClient同步调用结果规格Mock。
     */
    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    /**
     * 捕获实际传给ChatClient.messages()的Spring AI消息列表。
     *
     * <p>{@link Captor}由Mockito创建类型安全的参数捕获器，
     * 避免手写List强制类型转换。</p>
     */
    @Captor
    private ArgumentCaptor<List<Message>> springAiMessageListCaptor;

    /**
     * 被测试的Spring AI Agent执行器。
     */
    private SpringAiAgentExecutor springAiAgentExecutor;

    /**
     * 测试使用的Agent定义。
     */
    private AgentDefinition agentDefinition;

    /**
     * 测试使用的受信任用户和租户上下文。
     */
    private AgentInvocationContext agentInvocationContext;

    /**
     * 每个测试执行前创建Agent定义、认证上下文和执行器。
     */
    @BeforeEach
    void setUp() {
        /*
         * AgentDefinition.of()由Lombok生成静态工厂方法。
         * 当前Agent处于启用状态，并且是默认Agent。
         */
        agentDefinition = AgentDefinition.of(
                AGENT_CODE,
                AGENT_NAME,
                AGENT_DESCRIPTION,
                Boolean.TRUE,
                Boolean.TRUE);

        // 创建来自后端认证链路的受信任用户和租户上下文。
        agentInvocationContext = AgentInvocationContext.create(
                CURRENT_USER_ID,
                CURRENT_TENANT_ID);

        /*
         * JDK 9新增List.of()，用于创建不可修改List。
         * 这里的已启用Agent集合和运行配置集合在测试过程中不应被修改，因此使用不可修改List更安全。
         *
         * JDK 8中通常需要使用Collections.singletonList()。
         */
        when(agentRegistry.listEnabledAgents())
                .thenReturn(List.of(agentDefinition));

        // 将测试Agent编码与当前ChatClient绑定成Spring AI运行配置。
        SpringAiAgentRuntime springAiAgentRuntime =
                SpringAiAgentRuntime.of(AGENT_CODE, chatClient);

        /*
         * SpringAiAgentExecutor构造时会执行完整运行配置校验：
         * Agent注册中心存在、运行配置非空、编码不重复，并且启用Agent具有对应ChatClient。
         */
        springAiAgentExecutor = new SpringAiAgentExecutor(
                agentRegistry,
                List.of(springAiAgentRuntime));
    }

    /**
     * 验证非空Memory能够按照USER、ASSISTANT角色和原有顺序注入Spring AI Prompt。
     */
    @Test
    void execute_shouldInjectMemoryMessages_whenMemoryIsNotEmpty() {
        // 配置完整的ChatClient同步链式调用，并固定模型返回结果。
        mockSuccessfulChatClientChain(MODEL_ANSWER);

        /*
         * messages()是Memory非空时才会调用的可选步骤，
         * 因此只在当前需要历史消息的测试中配置该Stub，避免其他测试出现无用Stub。
         */
        when(requestSpec.messages(anyMemoryMessageList()))
                .thenReturn(requestSpec);

        // 构造此前已经完成的一轮USER和ASSISTANT历史问答。
        List<AgentMemoryMessage> memoryMessageList = List.of(
                AgentMemoryMessage.create(
                        "req_history_001",
                        AgentMemoryMessageRoleEnum.USER,
                        HISTORY_USER_MESSAGE,
                        1),
                AgentMemoryMessage.create(
                        "req_history_001",
                        AgentMemoryMessageRoleEnum.ASSISTANT,
                        HISTORY_ASSISTANT_MESSAGE,
                        2));

        // 执行携带历史Memory的正式同步模型调用。
        String answer = springAiAgentExecutor.execute(
                REQUEST_ID,
                agentDefinition,
                agentInvocationContext,
                memoryMessageList,
                CURRENT_QUESTION);

        // 捕获执行器实际传给Spring AI的历史Message列表。
        verify(requestSpec)
                .messages(springAiMessageListCaptor.capture());

        List<Message> capturedMessageList =
                springAiMessageListCaptor.getValue();

        /*
         * JDK 21为List所属的SequencedCollection体系增加getFirst()和getLast()。
         * 相比get(0)和get(size - 1)，该API更直接表达“第一条”和“最后一条”的业务语义。
         */
        Message firstMessage = capturedMessageList.getFirst();
        Message lastMessage = capturedMessageList.getLast();

        assertAll(
                // 执行器最终应返回ChatClient同步生成的模型答案。
                () -> assertEquals(MODEL_ANSWER, answer),

                // 一轮完整历史问答应转换成两条Spring AI Message。
                () -> assertEquals(2, capturedMessageList.size()),

                // 第一条历史消息必须保持USER角色。
                () -> assertEquals(MessageType.USER, firstMessage.getMessageType()),

                // 第一条历史消息正文必须保持原值。
                () -> assertEquals(HISTORY_USER_MESSAGE, firstMessage.getText()),

                // 第二条历史消息必须保持ASSISTANT角色。
                () -> assertEquals(MessageType.ASSISTANT, lastMessage.getMessageType()),

                // 第二条历史消息正文必须保持原值。
                () -> assertEquals(HISTORY_ASSISTANT_MESSAGE, lastMessage.getText()));

        /*
         * 历史消息通过messages()加入，本轮问题仍然通过user()单独加入。
         * 这项验证可以防止当前question被错误混入Memory后重复发送给模型。
         */
        verify(requestSpec).user(CURRENT_QUESTION);
    }

    /**
     * 验证空Memory不会调用ChatClient.messages()。
     */
    @Test
    void execute_shouldSkipMessages_whenMemoryIsEmpty() {
        // 配置不包含messages()步骤的正常ChatClient调用链。
        mockSuccessfulChatClientChain(MODEL_ANSWER);

        /*
         * JDK 9的List.of()无参形式创建不可修改空List，
         * 用于表达当前Conversation不存在历史Memory。
         */
        String answer = springAiAgentExecutor.execute(
                REQUEST_ID,
                agentDefinition,
                agentInvocationContext,
                List.of(),
                CURRENT_QUESTION);

        // 新Conversation没有历史消息时，不应向ChatClient传递无意义的空Message集合。
        verify(requestSpec, never())
                .messages(anyMemoryMessageList());

        // 即使没有历史Memory，本轮最新问题仍然必须正常发送给模型。
        verify(requestSpec).user(CURRENT_QUESTION);

        // 空Memory不能影响正常模型答案返回。
        assertEquals(MODEL_ANSWER, answer);
    }

    /**
     * 验证null Memory List按照空Memory处理。
     *
     * <p>正常Application链路会返回空List而不是null，
     * Executor仍然需要防御其他内部调用者错误传入null。</p>
     */
    @Test
    void execute_shouldTreatNullMemoryAsEmpty() {
        // 配置不包含messages()步骤的正常ChatClient调用链。
        mockSuccessfulChatClientChain(MODEL_ANSWER);

        // 故意传入null，验证ListUtils.emptyIfNull()的框架边界兜底。
        String answer = springAiAgentExecutor.execute(
                REQUEST_ID,
                agentDefinition,
                agentInvocationContext,
                null,
                CURRENT_QUESTION);

        // null Memory应被当作空历史，不应调用messages()。
        verify(requestSpec, never())
                .messages(anyMemoryMessageList());

        // 当前问题仍然正常通过user()加入Prompt。
        verify(requestSpec).user(CURRENT_QUESTION);

        assertEquals(MODEL_ANSWER, answer);
    }

    /**
     * 验证Memory List包含null元素时，在调用ChatClient之前明确失败。
     */
    @Test
    void execute_shouldThrowException_whenMemoryContainsNullElement() {
        /*
         * JDK 9的List.of()禁止包含null，因此这里使用可变ArrayList构造异常测试数据。
         * 该List只用于模拟上游错误，不代表正式业务代码应该允许null元素。
         */
        List<AgentMemoryMessage> invalidMemoryMessageList =
                new ArrayList<>();

        // 第一条历史消息合法。
        invalidMemoryMessageList.add(
                AgentMemoryMessage.create(
                        "req_history_001",
                        AgentMemoryMessageRoleEnum.USER,
                        HISTORY_USER_MESSAGE,
                        1));

        // 第二条故意加入null，模拟上游Memory整理链路异常。
        invalidMemoryMessageList.add(null);

        // 空元素必须在跨越Spring AI框架边界前被明确阻断。
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> springAiAgentExecutor.execute(
                        REQUEST_ID,
                        agentDefinition,
                        agentInvocationContext,
                        invalidMemoryMessageList,
                        CURRENT_QUESTION));

        assertEquals(
                "Agent Memory消息列表不能包含空元素",
                exception.getMessage());

        /*
         * Memory转换发生在chatClient.prompt()之前。
         * 验证ChatClient完全没有被调用，避免非法历史已经部分进入模型调用链。
         */
        verifyNoInteractions(chatClient);
    }

    /**
     * 配置一次成功的ChatClient同步调用链。
     *
     * <p>该方法只在确实会进入模型调用的测试中执行，
     * 避免把所有Stub放进@BeforeEach后造成Mockito UnnecessaryStubbingException。</p>
     *
     * <p>在挖矿流程中，这相当于为测试设备预先设置：
     * 接收任务、装入Advisor上下文、装入Tool身份档案、接收本轮问题并返回固定矿石结果。</p>
     *
     * @param modelAnswer ChatClient.content()需要返回的模拟答案
     */
    private void mockSuccessfulChatClientChain(String modelAnswer) {
        // chatClient.prompt()创建本轮模型请求规格。
        when(chatClient.prompt())
                .thenReturn(requestSpec);

        /*
         * advisors()接收Consumer配置requestId、agentCode和agentName，
         * 执行后继续返回同一个RequestSpec完成链式调用。
         */
        when(requestSpec.advisors(
                anyAdvisorSpecConsumer()))
                .thenReturn(requestSpec);

        // toolContext()接收受信任用户、租户和请求信息，并继续返回RequestSpec。
        when(requestSpec.toolContext(anyMap()))
                .thenReturn(requestSpec);

        // user()加入本轮最新问题，并继续返回RequestSpec。
        when(requestSpec.user(anyString()))
                .thenReturn(requestSpec);

        // call()执行同步模型请求并返回CallResponseSpec。
        when(requestSpec.call())
                .thenReturn(callResponseSpec);

        // content()返回本次测试设定的完整模型答案。
        when(callResponseSpec.content())
                .thenReturn(modelAnswer);
    }

    /**
     * 创建匹配Advisor Consumer参数的Mockito匹配器。
     *
     * <p>ChatClientRequestSpec存在多个advisors()重载方法，
     * 显式指定Consumer泛型可以帮助编译器选择正确重载，避免any()产生方法调用歧义。</p>
     *
     * @return Mockito Advisor Consumer参数匹配值
     */
    private Consumer<ChatClient.AdvisorSpec> anyAdvisorSpecConsumer() {
        // any()只在Mockito Stub或verify参数位置使用，实际运行时返回null占位值。
        return any();
    }

    /**
     * 创建匹配Spring AI Message List参数的Mockito匹配器。
     *
     * <p>ChatClientRequestSpec同时存在messages(Message...)和messages(List&lt;Message&gt;)重载，
     * 通过独立泛型方法明确选择List版本。</p>
     *
     * @return Mockito历史Message列表参数匹配值
     */
    private List<Message> anyMemoryMessageList() {
        // 显式泛型帮助Java编译器选择messages(List<Message>)重载。
        return any();
    }
}