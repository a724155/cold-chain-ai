package com.ymm.coldchainai.agent.core.infrastructure.springai;

import com.ymm.coldchainai.agent.core.application.executor.IAgentExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
    public String execute(String requestId, String question) {
        if (StringUtils.isBlank(requestId)) {
            // requestId 由 Application Service 生成，为空说明内部调用链出现程序错误。
            throw new IllegalArgumentException(REQUEST_ID_IS_BLANK_MESSAGE);
        }

        if (StringUtils.isBlank(question)) {
            // Application Service 正常情况下已经完成校验，此处再次防御内部错误调用。
            throw new IllegalArgumentException(QUESTION_IS_BLANK_MESSAGE);
        }

        // 只记录问题长度，不直接记录完整问题，降低日志中泄露用户业务信息的风险。
        log.info("开始调用正式Agent模型，requestId={}，questionLength={}", requestId, question.length());

        /*
         * call() 会同步等待模型完成本次回答，
         * content() 会从模型响应中提取最终文本并返回 String。
         *
         * 当前没有使用 stream()，因此这里不是 Token 级实时流式输出。
         * 后续接入 Tool Calling 时仍然默认使用同步 call()。
         */
        String answer = coldChainAgentChatClient.prompt().user(question).call().content();

        if (StringUtils.isBlank(answer)) {
            // 模型没有返回内容属于系统异常，不能伪造一个成功答案返回调用方。
            throw new IllegalStateException(AGENT_ANSWER_IS_BLANK_MESSAGE);
        }

        // 只记录答案长度，不把完整模型答案重复写入普通日志。
        log.info("正式Agent模型调用完成，requestId={}，answerLength={}", requestId, answer.length());

        return answer;
    }
}
