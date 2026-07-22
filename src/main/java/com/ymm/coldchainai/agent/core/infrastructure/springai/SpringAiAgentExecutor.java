package com.ymm.coldchainai.agent.core.infrastructure.springai;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import com.ymm.coldchainai.agent.core.application.executor.IAgentExecutor;
import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.infrastructure.advisor.AgentAdvisorContextKeys;
import com.ymm.coldchainai.agent.core.infrastructure.springai.model.SpringAiAgentRuntime;
import com.ymm.coldchainai.agent.core.infrastructure.tool.AgentToolContextKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
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
     * 执行一次正式Agent问答。
     *
     * @param requestId 本次Agent请求唯一标识
     * @param agentDefinition 本次需要执行的Agent定义
     * @param agentInvocationContext 本次调用使用的受信任用户和租户上下文
     * @param question 用户问题
     * @return 模型生成的完整答案
     */
    @Override
    public String execute(String requestId, AgentDefinition agentDefinition, AgentInvocationContext agentInvocationContext, String question) {

        if (StringUtils.isBlank(requestId)) {
            // requestId 由 Application Service 生成，为空说明内部调用链出现程序错误。
            throw new IllegalArgumentException(REQUEST_ID_IS_BLANK_MESSAGE);
        }

        if (Objects.isNull(agentDefinition)) {
            throw new IllegalArgumentException(AGENT_DEFINITION_IS_NULL_MESSAGE);
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
        ChatClient chatClient = springAiAgentRuntime.getChatClient();

        /*
         * advisorSpec.param()写入的是ChatClient Advisor上下文，不是模型Prompt。
         * AgentLifecycleLoggingAdvisor和ModelLifecycleLoggingAdvisor会从context中读取这些字段。
         */
        String answer = chatClient.prompt()
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
