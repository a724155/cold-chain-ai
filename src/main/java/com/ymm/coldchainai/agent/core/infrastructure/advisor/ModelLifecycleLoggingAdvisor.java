package com.ymm.coldchainai.agent.core.infrastructure.advisor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型生命周期日志Advisor。和AgentLifecycleLoggingAdvisor相比，类似于矿场挖掘机仪表员，关心“每一次启动机器消耗多少能源、用了多久、机器
 *
 * <p>该Advisor放在调用链靠近ChatModel的位置，
 * 负责记录每一次真实模型调用的开始、成功、失败、耗时和Token用量。</p>
 *
 * <p>未来接入Tool Calling后，一次Agent请求可能先让模型决定调用Tool，
 * Tool执行完成后再调用模型生成最终答案。因此同一个requestId下，
 * ModelLifecycleLoggingAdvisor可能执行多次，这是正常现象。</p>
 *
 * <p>该Advisor不记录Prompt文本和Completion文本，只记录消息数量、
 * 模型名称、响应标识和Token统计，降低敏感数据泄露风险。</p>
 */
@Slf4j
@Component
public class ModelLifecycleLoggingAdvisor implements CallAdvisor {

    /**
     * 模型生命周期Advisor执行顺序。
     *
     * <p>使用接近LOWEST_PRECEDENCE的顺序，使它尽量靠近最终ChatModel调用，
     * 但仍然保留空间给Spring AI内部的终止Advisor。</p>
     */
    private static final int ADVISOR_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    /**
     * 无法读取模型或上下文信息时使用的占位内容。
     */
    private static final String UNKNOWN_VALUE = "unknown";

    /**
     * 返回当前Advisor稳定名称。
     *
     * @return Advisor名称
     */
    @Override
    public String getName() {
        return ModelLifecycleLoggingAdvisor.class.getSimpleName();
    }

    /**
     * 返回当前Advisor在调用链中的执行顺序。
     *
     * @return Advisor执行顺序
     */
    @Override
    public int getOrder() {
        return ADVISOR_ORDER;
    }

    /**
     * 记录一次真实模型调用的生命周期和响应元数据。
     *
     * @param chatClientRequest 当前ChatClient请求
     * @param callAdvisorChain 后续同步Advisor调用链
     * @return 模型调用返回的ChatClient响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        // requestId用于把同一次Agent请求中的多轮模型调用关联起来。
        String requestId = resolveContextValue(chatClientRequest, AgentAdvisorContextKeys.REQUEST_ID);

        // agentCode用于识别本次模型调用属于哪个Agent运行配置。
        String agentCode = resolveContextValue(chatClientRequest, AgentAdvisorContextKeys.AGENT_CODE);

        // messageCount表示本轮发送给模型的消息数量，不记录具体消息内容。
        int messageCount = resolveMessageCount(chatClientRequest);

        // startTimeMillis记录当前这一轮真实模型调用的开始时间。
        long startTimeMillis = System.currentTimeMillis();

        log.info("模型调用开始，requestId={}，agentCode={}，messageCount={}", requestId, agentCode, messageCount);

        try {
            /*
             * 调用后续链路，最终由Spring AI内部ChatModelCallAdvisor执行真实模型请求。
             * 当前Advisor只观察响应，不修改Prompt和模型返回结果。
             */
            ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

            // costMillis只统计当前这一轮模型调用以及其内部下游处理耗时。
            long costMillis = System.currentTimeMillis() - startTimeMillis;

            // ChatClientResponse可能只包含上下文，因此需要先安全取得ChatResponse。
            ChatResponse chatResponse = Objects.isNull(chatClientResponse) ? null : chatClientResponse.chatResponse();

            // 模型元数据通常包含模型名称、响应ID和Token用量，但兼容服务不一定全部返回。
            ChatResponseMetadata chatResponseMetadata = Objects.isNull(chatResponse) ? null : chatResponse.getMetadata();

            // 安全读取实际响应模型名称。
            String modelName = resolveModelName(chatResponseMetadata);

            // 安全读取模型服务返回的响应唯一标识。
            String modelResponseId = resolveModelResponseId(chatResponseMetadata);

            // 安全读取Token统计；兼容模型不返回Token时允许显示null，不能因此判定调用失败。
            Usage usage = Objects.isNull(chatResponseMetadata) ? null : chatResponseMetadata.getUsage();
            Integer promptTokens = Objects.isNull(usage) ? null : usage.getPromptTokens();
            Integer completionTokens = Objects.isNull(usage) ? null : usage.getCompletionTokens();
            Integer totalTokens = Objects.isNull(usage) ? null : usage.getTotalTokens();

            // generationCount表示模型本轮返回了多少个候选结果。
            int generationCount = resolveGenerationCount(chatResponse);

            // hasToolCalls用于识别本轮模型响应是否要求继续执行Tool。
            boolean hasToolCalls = Objects.nonNull(chatResponse) && chatResponse.hasToolCalls();

            log.info("模型调用成功，requestId={}，agentCode={}，modelName={}，modelResponseId={}，costMillis={}，promptTokens={}，completionTokens={}，totalTokens={}，generationCount={}，hasToolCalls={}",
                    requestId, agentCode, modelName, modelResponseId, costMillis, promptTokens, completionTokens, totalTokens, generationCount, hasToolCalls);

            return chatClientResponse;
        } catch (RuntimeException exception) {
            // 模型失败时仍然记录已经消耗的时间，便于识别快速参数失败和长时间网络超时。
            long costMillis = System.currentTimeMillis() - startTimeMillis;

            // 这里只记录异常类型，完整异常堆栈继续交给全局异常处理器统一输出。
            log.warn("模型调用失败，requestId={}，agentCode={}，costMillis={}，exceptionType={}", requestId, agentCode, costMillis, exception.getClass().getName());

            throw exception;
        }
    }

    /**
     * 安全读取Advisor上下文中的字符串值。
     *
     * @param chatClientRequest ChatClient请求
     * @param contextKey 上下文字段名称
     * @return 有效上下文值，无法读取时返回unknown
     */
    private String resolveContextValue(ChatClientRequest chatClientRequest, String contextKey) {
        if (Objects.isNull(chatClientRequest) || StringUtils.isBlank(contextKey)) {
            return UNKNOWN_VALUE;
        }

        Map<String, Object> context = chatClientRequest.context();

        if (Objects.isNull(context) || context.isEmpty()) {
            return UNKNOWN_VALUE;
        }

        Object contextValue = context.get(contextKey);

        if (Objects.isNull(contextValue)) {
            return UNKNOWN_VALUE;
        }

        return StringUtils.defaultIfBlank(String.valueOf(contextValue), UNKNOWN_VALUE);
    }

    /**
     * 安全计算当前Prompt中的消息数量。
     *
     * @param chatClientRequest ChatClient请求
     * @return Prompt消息数量
     */
    private int resolveMessageCount(ChatClientRequest chatClientRequest) {
        if (Objects.isNull(chatClientRequest) || Objects.isNull(chatClientRequest.prompt())) {
            return 0;
        }

        List<?> messageList = chatClientRequest.prompt().getInstructions();

        if (Objects.isNull(messageList)) {
            return 0;
        }

        return messageList.size();
    }

    /**
     * 安全获取模型名称。
     *
     * @param chatResponseMetadata 模型响应元数据
     * @return 模型名称
     */
    private String resolveModelName(ChatResponseMetadata chatResponseMetadata) {
        if (Objects.isNull(chatResponseMetadata)) {
            return UNKNOWN_VALUE;
        }

        return StringUtils.defaultIfBlank(chatResponseMetadata.getModel(), UNKNOWN_VALUE);
    }

    /**
     * 安全获取模型响应标识。
     *
     * @param chatResponseMetadata 模型响应元数据
     * @return 模型响应标识
     */
    private String resolveModelResponseId(ChatResponseMetadata chatResponseMetadata) {
        if (Objects.isNull(chatResponseMetadata)) {
            return UNKNOWN_VALUE;
        }

        return StringUtils.defaultIfBlank(chatResponseMetadata.getId(), UNKNOWN_VALUE);
    }

    /**
     * 安全计算模型生成结果数量。
     *
     * @param chatResponse 模型响应
     * @return 模型生成结果数量
     */
    private int resolveGenerationCount(ChatResponse chatResponse) {
        if (Objects.isNull(chatResponse) || Objects.isNull(chatResponse.getResults())) {
            return 0;
        }

        return chatResponse.getResults().size();
    }
}
