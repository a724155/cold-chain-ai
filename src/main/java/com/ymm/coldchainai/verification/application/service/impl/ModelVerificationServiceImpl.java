package com.ymm.coldchainai.verification.application.service.impl;

import com.ymm.coldchainai.shared.exception.BusinessException;
import com.ymm.coldchainai.verification.application.service.IModelVerificationService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 普通模型调用验证服务实现。
 *
 * <p>该实现负责调用基础 ChatClient，验证项目能否通过
 * OpenAI Compatible 协议正常获得模型回答。</p>
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ModelVerificationServiceImpl implements IModelVerificationService {

    /**
     * 模型问题为空时使用的业务失败编码。
     */
    private static final Integer QUESTION_IS_BLANK_CODE = 40001;

    /**
     * 用户问题为空时使用的异常信息。
     */
    private static final String QUESTION_IS_BLANK_MESSAGE = "模型问题不能为空";

    /**
     * 模型未返回有效内容时使用的异常信息。
     */
    private static final String MODEL_ANSWER_IS_BLANK_MESSAGE = "模型未返回有效回答";

    /**
     * 基础 ChatClient，由 AiModelConfiguration 统一创建。
     */
    private final ChatClient basicChatClient;

    /**
     * 将用户问题发送给基础模型并返回完整回答。
     *
     * @param question 用户提交的问题
     * @return 模型完整回答
     */
    @Override
    public String chat(String question) {
        if (StringUtils.isBlank(question)) {
            // 用户问题为空属于可预期业务失败，交由全局异常处理器转换成统一返回结构。
            throw new BusinessException(QUESTION_IS_BLANK_CODE, QUESTION_IS_BLANK_MESSAGE);
        }

        /*
         * call() 会同步等待模型完成本次回答，
         * content() 会从模型响应中提取最终文本内容并返回 String。
         *
         * 当前没有使用 stream()，因此这里不是 Token 级实时流式输出。
         * 后续接入 Tool Calling 时仍然默认使用同步 call()，避免兼容模型在流式 Tool Calling 中出现分片问题。
         */
        String answer = basicChatClient.prompt().user(question).call().content();

        if (StringUtils.isBlank(answer)) {
            throw new IllegalStateException(MODEL_ANSWER_IS_BLANK_MESSAGE);
        }

        return answer;
    }
}
