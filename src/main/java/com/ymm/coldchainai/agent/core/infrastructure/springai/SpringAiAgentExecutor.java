package com.ymm.coldchainai.agent.core.infrastructure.springai;

import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import com.ymm.coldchainai.agent.core.application.executor.IAgentExecutor;
import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.infrastructure.advisor.AgentAdvisorContextKeys;
import com.ymm.coldchainai.agent.core.infrastructure.springai.model.SpringAiAgentRuntime;
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
     * 创建Spring AI Agent执行器并校验运行配置。
     *
     * <p>这里需要完成运行配置集合转换、重复编码校验，
     * 以及已启用Agent与运行配置之间的一致性校验，因此手写构造方法。</p>
     *
     * @param agentRegistry            Agent业务注册中心
     * @param springAiAgentRuntimeList Spring容器中注册的所有Spring AI Agent运行配置
     */
    @Autowired
    public SpringAiAgentExecutor(IAgentRegistry agentRegistry, List<SpringAiAgentRuntime> springAiAgentRuntimeList) {
        if (Objects.isNull(agentRegistry)) {
            throw createRuntimeConfigurationException("Agent注册中心不能为空");
        }

        // 对Spring注入的运行配置列表进行空值兜底，避免初始化阶段产生空指针。
        List<SpringAiAgentRuntime> safeSpringAiAgentRuntimeList = Optional.ofNullable(springAiAgentRuntimeList).orElse(Collections.emptyList());

        if (safeSpringAiAgentRuntimeList.isEmpty()) {
            throw createRuntimeConfigurationException("系统中没有配置任何Agent运行环境");
        }

        // LinkedHashMap保留运行配置注册顺序，便于启动日志和后续问题排查。
        Map<String, SpringAiAgentRuntime> mutableSpringAiAgentRuntimeMap = new LinkedHashMap<>();

        for (SpringAiAgentRuntime springAiAgentRuntime : safeSpringAiAgentRuntimeList) {
            validateSpringAiAgentRuntime(springAiAgentRuntime);

            // Agent编码统一转换成小写键，与AgentRegistryImpl保持相同的查找规则。
            String normalizedAgentCode = normalizeAgentCode(springAiAgentRuntime.getAgentCode());

            if (mutableSpringAiAgentRuntimeMap.containsKey(normalizedAgentCode)) {
                throw createRuntimeConfigurationException("存在重复Agent运行配置，agentCode=%s".formatted(springAiAgentRuntime.getAgentCode()));
            }

            mutableSpringAiAgentRuntimeMap.put(normalizedAgentCode, springAiAgentRuntime);
        }

        // 查询所有已启用Agent，确保每个可被用户调用的Agent都有真实运行配置。
        List<AgentDefinition> enabledAgentDefinitionList = Optional.ofNullable(agentRegistry.listEnabledAgents()).orElse(Collections.emptyList());

        if (enabledAgentDefinitionList.isEmpty()) {
            throw createRuntimeConfigurationException("系统中没有可执行的已启用Agent");
        }

        for (AgentDefinition agentDefinition : enabledAgentDefinitionList) {
            if (Objects.isNull(agentDefinition) || StringUtils.isBlank(agentDefinition.getAgentCode())) {
                throw createRuntimeConfigurationException("已启用Agent定义或Agent编码不能为空");
            }

            String normalizedAgentCode = normalizeAgentCode(agentDefinition.getAgentCode());

            if (!mutableSpringAiAgentRuntimeMap.containsKey(normalizedAgentCode)) {
                throw createRuntimeConfigurationException("已启用Agent缺少运行配置，agentCode=%s".formatted(agentDefinition.getAgentCode()));
            }
        }

        // 转换成只读Map，避免执行器初始化完成后被其他代码意外修改。
        this.springAiAgentRuntimeMap = Collections.unmodifiableMap(mutableSpringAiAgentRuntimeMap);

        log.info("Spring AI Agent运行配置初始化完成，runtimeCount={}，enabledAgentCount={}", springAiAgentRuntimeMap.size(), enabledAgentDefinitionList.size());
    }

    /**
     * 执行一次正式 Agent 问答。
     *
     * @param requestId       本次 Agent 请求唯一标识
     * @param agentDefinition 本次需要执行的Agent定义
     * @param question        用户问题
     * @return 模型生成的完整答案
     */
    @Override
    public String execute(String requestId, AgentDefinition agentDefinition, String question) {

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
            /*
             * 启动阶段已经校验全部已启用Agent，因此正常运行时不应该进入这里。
             * 如果仍然找不到，说明运行期间配置发生了非预期变化或内部调用绕过了注册中心。
             */
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
                .user(question).call().content();

        if (StringUtils.isBlank(answer)) {
            throw new IllegalStateException(AGENT_ANSWER_IS_BLANK_MESSAGE);
        }

        return answer;
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
