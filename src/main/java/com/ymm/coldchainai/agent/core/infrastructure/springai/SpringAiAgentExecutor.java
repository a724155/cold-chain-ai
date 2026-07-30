package com.ymm.coldchainai.agent.core.infrastructure.springai;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import com.ymm.coldchainai.agent.core.application.executor.IAgentExecutor;
import com.ymm.coldchainai.agent.core.application.memory.model.AgentMemoryMessage;
import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.infrastructure.advisor.AgentAdvisorContextKeys;
import com.ymm.coldchainai.agent.core.infrastructure.springai.model.SpringAiAgentRuntime;
import com.ymm.coldchainai.agent.core.infrastructure.tool.AgentToolContextKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于 Spring AI 的 Agent 执行器。
 *
 * <p>该类属于 Infrastructure 层，负责把 Application 层定义的执行请求
 * 转换成具体的 Spring AI ChatClient 调用。</p>
 *
 * <p>当前阶段只执行普通同步模型问答，后续将在这里接入 Agent Registry、
 * Advisor、Tool Calling 和模型生命周期日志。</p>
 */
@Slf4j
@Component
public class SpringAiAgentExecutor implements IAgentExecutor {

    /**
     * requestId 为空时使用的系统异常信息。
     */
    private static final String REQUEST_ID_IS_BLANK_MESSAGE = "Agent请求标识不能为空";

    /**
     * Agent定义为空时使用的系统异常信息。
     */
    private static final String AGENT_DEFINITION_IS_NULL_MESSAGE = "Agent定义不能为空";

    /**
     * 用户问题为空时使用的系统异常信息。
     */
    private static final String QUESTION_IS_BLANK_MESSAGE = "Agent执行问题不能为空";

    /**
     * 模型没有返回有效内容时使用的系统异常信息。
     */
    private static final String AGENT_ANSWER_IS_BLANK_MESSAGE = "Agent模型未返回有效回答";

    /**
     * Agent编码与Spring AI运行配置之间的只读映射。
     */
    private final Map<String, SpringAiAgentRuntime> springAiAgentRuntimeMap;

    /**
     * Agent调用上下文为空时使用的系统异常信息。
     */
    private static final String AGENT_INVOCATION_CONTEXT_IS_NULL_MESSAGE = "Agent调用上下文不能为空";

    /**
     * 创建Spring AI Agent执行器并校验运行配置。
     *
     * <p>Spring AI Agent执行器相当于挖矿系统中的“设备调度中心”：AgentRegistry负责登记有哪些矿区可以开采，
     * SpringAiAgentRuntime负责记录每个矿区实际绑定的开采设备（ChatClient、模型参数、提示词）。
     * 这里需要确保每个可用矿区都有对应设备，否则运行阶段才发现配置缺失会导致用户请求失败。</p>
     *
     * <p>这里需要完成运行配置集合转换、重复编码校验，以及已启用Agent与运行配置之间的一致性校验，因此手写构造方法。</p>
     *
     * @param agentRegistry            Agent业务注册中心
     * @param springAiAgentRuntimeList Spring容器中注册的所有Spring AI Agent运行配置
     */
    @Autowired
    public SpringAiAgentExecutor(IAgentRegistry agentRegistry, List<SpringAiAgentRuntime> springAiAgentRuntimeList) {

        // 校验Agent注册中心是否正常注入，避免执行器初始化后无法获取业务Agent定义。
        if (Objects.isNull(agentRegistry)) {
            throw createRuntimeConfigurationException("Agent注册中心不能为空");
        }

        // 对Spring注入的运行配置列表进行空值兜底，避免Spring容器初始化阶段因为配置缺失导致空指针。
        List<SpringAiAgentRuntime> safeSpringAiAgentRuntimeList = Optional.ofNullable(springAiAgentRuntimeList).orElse(Collections.emptyList());

        // 没有任何运行配置时，说明系统没有可执行的模型运行环境，提前失败避免运行期间才报错。
        if (safeSpringAiAgentRuntimeList.isEmpty()) {
            throw createRuntimeConfigurationException("系统中没有配置任何Agent运行环境");
        }

        /* 创建临时Map保存Agent编码与运行配置关系，后续通过agentCode快速定位对应ChatClient执行环境。
        LinkedHashMap保留配置注册顺序，方便启动日志查看以及线上问题排查。*/
        Map<String, SpringAiAgentRuntime> mutableSpringAiAgentRuntimeMap = new LinkedHashMap<>();

        // 遍历所有Spring AI运行配置，将每个Agent的运行环境注册到执行器内部。
        for (SpringAiAgentRuntime springAiAgentRuntime : safeSpringAiAgentRuntimeList) {

            // 校验当前运行配置是否完整，例如agentCode、ChatClient、提示词等关键配置是否存在。
            validateSpringAiAgentRuntime(springAiAgentRuntime);

            // 将Agent编码统一转换成标准格式，保证大小写不同的编码不会导致查询失败。例如Driver-Order-Agent和driver-order-agent最终都会使用同一个Map Key。
            String normalizedAgentCode = normalizeAgentCode(springAiAgentRuntime.getAgentCode());

            // 校验是否存在重复Agent编码，避免后注册的配置覆盖之前的运行环境。
            if (mutableSpringAiAgentRuntimeMap.containsKey(normalizedAgentCode)) {
                throw createRuntimeConfigurationException("存在重复Agent运行配置，agentCode=%s".formatted(springAiAgentRuntime.getAgentCode()));
            }

            // 保存Agent编码与运行环境绑定关系，后续用户请求根据agentCode找到对应ChatClient执行。
            mutableSpringAiAgentRuntimeMap.put(normalizedAgentCode, springAiAgentRuntime);
        }

        // 从Agent注册中心获取所有已启用Agent定义，确认业务层允许调用的Agent是否都有实际运行环境。
        List<AgentDefinition> enabledAgentDefinitionList = Optional.ofNullable(agentRegistry.listEnabledAgents()).orElse(Collections.emptyList());

        // 没有启用Agent说明系统没有任何可提供服务的能力，启动阶段直接失败。
        if (enabledAgentDefinitionList.isEmpty()) {
            throw createRuntimeConfigurationException("系统中没有可执行的已启用Agent");
        }

        // 遍历所有启用Agent，校验业务注册信息和实际运行配置是否保持一致。
        for (AgentDefinition agentDefinition : enabledAgentDefinitionList) {

            // 校验Agent定义本身是否完整，避免后续根据空编码查找运行配置。
            if (Objects.isNull(agentDefinition) || StringUtils.isBlank(agentDefinition.getAgentCode())) {
                throw createRuntimeConfigurationException("已启用Agent定义或Agent编码不能为空");
            }

            // 使用和运行配置相同的编码标准化规则，保证两个Map查询逻辑完全一致。
            String normalizedAgentCode = normalizeAgentCode(agentDefinition.getAgentCode());

            // 检查业务层启用的Agent是否存在对应Spring AI运行环境。如果缺少绑定关系，用户调用该Agent时无法找到模型和Tool执行能力。
            if (!mutableSpringAiAgentRuntimeMap.containsKey(normalizedAgentCode)) {
                throw createRuntimeConfigurationException("已启用Agent缺少运行配置，agentCode=%s".formatted(agentDefinition.getAgentCode()));
            }
        }

        // 将可变Map转换成不可修改Map，防止执行器创建完成后其他代码动态修改Agent运行配置。执行器初始化完成后，Agent运行环境应该保持稳定，避免运行过程中出现请求结果不一致。
        this.springAiAgentRuntimeMap = Collections.unmodifiableMap(mutableSpringAiAgentRuntimeMap);
        // 输出初始化结果，方便启动阶段确认当前加载了多少运行配置和启用了多少Agent。
        log.info("Spring AI Agent运行配置初始化完成，runtimeCount={}，enabledAgentCount={}", springAiAgentRuntimeMap.size(), enabledAgentDefinitionList.size());
    }

    /**
     * 执行一次携带Conversation Memory的正式Agent问答。
     *
     * <p>memoryMessageList只包含此前已经完成的问答轮次，
     * 当前question仍然通过user(question)作为本轮新消息加入Prompt，
     * 避免当前问题在历史Memory和本轮User Message中重复出现。</p>
     *
     * <p>该方法只负责模型调用，不查询MySQL，也不保存Chat History。
     * 历史读取和数据权限校验已经由Application层的Memory Provider完成。</p>
     *
     * <p>在挖矿流程中，Memory消息相当于矿工随身携带的最近项目档案，
     * question相当于客户本轮新下达的开采任务；
     * 设备需要同时看到历史档案和当前任务，才能理解“刚才那个”“那一单”等上下文指代。</p>
     *
     * @param requestId 本次Agent请求唯一标识
     * @param agentDefinition 本次需要执行的Agent定义
     * @param agentInvocationContext 本次调用使用的受信任用户和租户上下文
     * @param memoryMessageList 当前Conversation最近的有效上下文消息
     * @param question 本轮用户问题
     * @return 模型生成的完整答案
     */
    @Override
    public String execute(String requestId, AgentDefinition agentDefinition, AgentInvocationContext agentInvocationContext,
                          List<AgentMemoryMessage> memoryMessageList, String question) {

        if (StringUtils.isBlank(requestId)) {
            // requestId 由 Application Service 生成，为空说明内部调用链出现程序错误。
            throw new IllegalArgumentException(REQUEST_ID_IS_BLANK_MESSAGE);
        }

        if (Objects.isNull(agentDefinition)) {
            throw new IllegalArgumentException(AGENT_DEFINITION_IS_NULL_MESSAGE);
        }

        if (Objects.isNull(agentInvocationContext)) {
            // 调用上下文承载受信任用户和租户信息，为空时Tool无法安全获得调用者身份。
            throw new IllegalArgumentException(AGENT_INVOCATION_CONTEXT_IS_NULL_MESSAGE);
        }

        if (StringUtils.isBlank(question)) {
            // Application Service 正常情况下已经完成校验，此处再次防御内部错误调用。
            throw new IllegalArgumentException(QUESTION_IS_BLANK_MESSAGE);
        }

        // 根据注册中心返回的Agent编码查找该Agent专属的Spring AI运行配置。
        String normalizedAgentCode = normalizeAgentCode(agentDefinition.getAgentCode());

        SpringAiAgentRuntime springAiAgentRuntime = springAiAgentRuntimeMap.get(normalizedAgentCode);

        if (Objects.isNull(springAiAgentRuntime)) {
            // 启动阶段已经校验全部已启用Agent，因此正常运行时不应该进入这里。如果仍然找不到，说明运行期间配置发生了非预期变化或内部调用绕过了注册中心。
            throw createRuntimeConfigurationException("执行时未找到Agent运行配置，agentCode=%s".formatted(agentDefinition.getAgentCode()));
        }

        // 从运行配置中获取当前Agent专属ChatClient，禁止使用全局通用ChatClient。
        // 不同Agent可能绑定不同System Prompt、模型参数、Advisor和Tool集合，必须通过Runtime选择对应运行实例。
        ChatClient chatClient = springAiAgentRuntime.getChatClient();

        // Application层Memory消息不依赖Spring AI框架。只有进入Infrastructure执行器后才根据历史角色转换成Spring AI能够识别的UserMessage和AssistantMessage。
        List<Message> springAiMemoryMessageList = convertToSpringAiMessageList(memoryMessageList);

        /*
         * 创建一次Chat请求构造器。
         * ChatClient本身类似一个已经配置好的AI客户端模板：
         * 包含模型、默认Prompt、Tool等固定能力。
         * prompt()返回的是当前这一次调用的请求构建对象，
         * 后续可以继续追加：
         * 1. 历史消息；
         * 2. Advisor上下文；
         * 3. Tool调用上下文；
         * 4. 当前用户问题。
         * 单独保存requestSpec，是为了后续根据业务条件动态修改请求内容，
         * 避免大量if判断导致链式调用嵌套过深。
         */
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();

        // 只有历史Memory非空时才调用messages()。新Conversation没有历史，不增加无意义的空消息集合。
        if (CollectionUtils.isNotEmpty(springAiMemoryMessageList)) {

            /*
             * messages()添加的是历史上下文。
             * 例如：
             * USER：司机123今天成交几个订单？
             * ASSISTANT：司机123今天成交5个订单。
             * 这些属于历史Memory，用于帮助模型理解当前Conversation背景。
             */
            requestSpec = requestSpec.messages(springAiMemoryMessageList);
        }
        /*
         * 完成当前请求剩余上下文组装并调用模型。
         * messages():加入历史Conversation上下文。
         * advisors():传递Agent执行链路需要的requestId、agentCode等监控信息。
         * toolContext():给Tool提供受信任业务上下文，例如当前用户ID、租户ID，避免Tool参数完全依赖模型输入造成权限风险。
         * user(question):设置本轮用户最新问题。
         * call():同步调用ChatModel执行一次完整Agent流程。
         * content():获取最终文本回答。
         */
        String answer = requestSpec
                .advisors(advisorSpec -> advisorSpec.param(AgentAdvisorContextKeys.REQUEST_ID, requestId)
                        .param(AgentAdvisorContextKeys.AGENT_CODE, agentDefinition.getAgentCode())
                        .param(AgentAdvisorContextKeys.AGENT_NAME, agentDefinition.getAgentName()))
                .toolContext(createToolContextMap(requestId, agentDefinition, agentInvocationContext))
                .user(question).call().content();

        if (StringUtils.isBlank(answer)) {
            throw new IllegalStateException(AGENT_ANSWER_IS_BLANK_MESSAGE);
        }

        return answer;
    }

    /**
     * 将Application层Memory消息转换成Spring AI Message列表。
     *
     * <p>Application层维护的是自己的领域消息模型，
     * 但Spring AI调用ChatModel时需要的是Spring AI定义的Message对象。
     * 因此这里承担领域模型到AI框架模型之间的转换职责。</p>
     *
     * <p>这里使用一次Stream遍历完成：
     * 1. 集合空安全处理；
     * 2. 元素非空校验；
     * 3. 单条消息类型转换。
     *
     * 不提前单独for循环校验，再stream转换，避免对同一个List进行重复扫描。</p>
     *
     * @param memoryMessageList Application层上下文消息列表
     * @return Spring AI能够识别的Message列表
     */
    private List<Message> convertToSpringAiMessageList(List<AgentMemoryMessage> memoryMessageList) {
        // ListUtils.emptyIfNull保证即使上游传入null，也转换为空列表，避免stream()直接触发空指针异常。
        List<AgentMemoryMessage> safeMemoryMessageList = ListUtils.emptyIfNull(memoryMessageList);

        // Stream遍历每条Memory消息，将Application领域对象转换成Spring AI框架Message对象。
        return safeMemoryMessageList.stream()
                .map(memoryMessage -> {
                    // 防止非法null元素进入转换逻辑，避免后续调用getMessageRole()产生空指针。
                    if (Objects.isNull(memoryMessage)) {
                        throw new IllegalArgumentException("Agent Memory消息列表不能包含空元素");
                    }
                    // 将当前领域消息转换成Spring AI支持的UserMessage或AssistantMessage。
                    return convertToSpringAiMessage(memoryMessage);
                })
                .toList();
    }

    /**
     * 将单条Agent Memory消息转换成Spring AI消息。
     *
     * <p>Spring AI不能直接理解项目内部定义的AgentMemoryMessage，
     * 必须转换成框架提供的Message实现。</p>
     *
     * <p>转换关系：
     * USER       → UserMessage
     * ASSISTANT  → AssistantMessage
     *
     * 这样模型调用时仍然知道历史消息分别是谁发送的，
     * 不需要把历史拼接成一大段普通文本。</p>
     *
     * @param memoryMessage Application层Memory消息
     * @return Spring AI消息
     */
    private Message convertToSpringAiMessage(AgentMemoryMessage memoryMessage) {

        // 角色决定转换成哪一种Spring AI Message，角色缺失无法判断消息身份。
        if (Objects.isNull(memoryMessage.getMessageRole())) {
            throw new IllegalArgumentException("Agent Memory消息角色不能为空");
        }
        // 消息正文为空时没有实际上下文意义，禁止进入模型调用。
        if (StringUtils.isBlank(memoryMessage.getMessageContent())) {
            throw new IllegalArgumentException("Agent Memory消息正文不能为空");
        }
        /*
         * Java 14以后支持的switch表达式。
         * 与传统switch不同：
         * 1. switch本身可以直接返回结果；
         * 2. 使用 -> 后不需要break；
         * 3. 每个case必须产生一个明确结果。
         * 这里根据消息角色选择对应Spring AI Message实现。
         */
        return switch (memoryMessage.getMessageRole()) {
            // 用户历史消息转换成Spring AI UserMessage。
            case USER -> new UserMessage(memoryMessage.getMessageContent());
            // AI历史回答转换成Spring AI AssistantMessage。
            case ASSISTANT -> new AssistantMessage(memoryMessage.getMessageContent());
        };
    }

    /**
     * 创建当前请求传递给Tool的受信任上下文。
     *
     * <p>在挖矿流程中，这相当于设备操作员启动挖掘机前，把任务编号、矿区编号和客户许可证放入随车密封档案袋。</p>
     *
     * @param requestId 本次Agent请求唯一标识
     * @param agentDefinition 当前Agent定义
     * @param agentInvocationContext 当前认证上下文
     * @return Tool执行上下文Map
     */
    private Map<String, Object> createToolContextMap(String requestId, AgentDefinition agentDefinition, AgentInvocationContext agentInvocationContext) {
        return Map.of(
                AgentToolContextKeys.REQUEST_ID, requestId,
                AgentToolContextKeys.AGENT_CODE, agentDefinition.getAgentCode(),
                AgentToolContextKeys.CURRENT_USER_ID, agentInvocationContext.getCurrentUserId(),
                AgentToolContextKeys.CURRENT_TENANT_ID, agentInvocationContext.getCurrentTenantId());
    }

    /**
     * 校验单个Spring AI Agent运行配置。
     *
     * @param springAiAgentRuntime 待校验运行配置
     */
    private void validateSpringAiAgentRuntime(SpringAiAgentRuntime springAiAgentRuntime) {
        if (Objects.isNull(springAiAgentRuntime)) {
            throw createRuntimeConfigurationException("Agent运行配置不能为空");
        }

        if (StringUtils.isBlank(springAiAgentRuntime.getAgentCode())) {
            throw createRuntimeConfigurationException("Agent运行配置中的agentCode不能为空");
        }

        if (Objects.isNull(springAiAgentRuntime.getChatClient())) {
            throw createRuntimeConfigurationException("Agent运行配置中的ChatClient不能为空，agentCode=%s".formatted(springAiAgentRuntime.getAgentCode()));
        }
    }

    /**
     * 将Agent编码转换成运行配置映射统一使用的标准格式。
     *
     * @param agentCode 原始Agent编码
     * @return 去除首尾空格并转成小写的Agent编码
     */
    private String normalizeAgentCode(String agentCode) {
        return StringUtils.trim(agentCode).toLowerCase(Locale.ROOT);
    }

    /**
     * 创建Agent运行配置异常。
     *
     * @param detailMessage 具体配置错误信息
     * @return Agent运行配置异常
     */
    private IllegalStateException createRuntimeConfigurationException(String detailMessage) {
        String errorMessage = "%s：%s".formatted(AgentErrorCodeEnum.AGENT_RUNTIME_CONFIGURATION_ERROR.getMessage(), detailMessage);
        return new IllegalStateException(errorMessage);
    }
}
