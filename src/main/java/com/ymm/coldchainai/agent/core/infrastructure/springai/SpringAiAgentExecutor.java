package com.ymm.coldchainai.agent.core.infrastructure.springai;

import com.ymm.coldchainai.agent.core.application.executor.IAgentExecutor;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

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
@RequiredArgsConstructor(onConstructor_ = @Autowired)
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
     * 正式冷运 Agent 使用的 ChatClient。
     *
     * <p>字段名称与 AgentCoreConfiguration 中的 Bean 名称保持一致，
     * 用于在存在多个 ChatClient Bean 时准确完成依赖注入。</p>
     */
    private final ChatClient coldChainAgentChatClient;

    /**
     * 执行一次正式 Agent 问答。
     *
     * @param requestId 本次 Agent 请求唯一标识
     * @param question 用户问题
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

        // 记录实际执行的Agent编码，方便后续一个请求涉及多个Agent时定位路由结果。
        log.info("开始调用正式Agent模型，requestId={}，agentCode={}，questionLength={}", requestId, agentDefinition.getAgentCode(), question.length());

        /*
         * call()会同步等待模型完成本次回答，
         * content()会从模型响应中提取最终文本并返回String。
         *
         * 当前没有使用stream()，因此这里不是Token级实时流式输出。
         */
        String answer = coldChainAgentChatClient.prompt().user(question).call().content();

        if (StringUtils.isBlank(answer)) {
            throw new IllegalStateException(AGENT_ANSWER_IS_BLANK_MESSAGE);
        }

        log.info("正式Agent模型调用完成，requestId={}，agentCode={}，answerLength={}", requestId, agentDefinition.getAgentCode(), answer.length());

        return answer;
    }
}
