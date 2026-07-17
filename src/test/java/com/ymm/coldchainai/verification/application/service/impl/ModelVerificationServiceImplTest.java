package com.ymm.coldchainai.verification.application.service.impl;

import com.ymm.coldchainai.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 普通模型调用验证服务单元测试。
 *
 * <p>该测试使用 Mockito 模拟 ChatClient，不发送真实模型请求，
 * 重点验证模型调用前的业务参数校验逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
class ModelVerificationServiceImplTest {

    /**
     * 模型问题为空时的预期业务编码。
     */
    private static final Integer EXPECTED_QUESTION_BLANK_CODE = 40001;

    /**
     * 模型问题为空时的预期提示信息。
     */
    private static final String EXPECTED_QUESTION_BLANK_MESSAGE = "模型问题不能为空";

    /**
     * 模拟基础 ChatClient，测试期间不会产生真实模型费用。
     */
    @Mock
    private ChatClient basicChatClient;

    /**
     * 将模拟 ChatClient 通过构造器注入被测试的 Application Service。
     */
    @InjectMocks
    private ModelVerificationServiceImpl modelVerificationService;

    /**
     * 测试问题为空白字符串时直接抛出业务异常。
     */
    @Test
    void shouldThrowBusinessExceptionWhenQuestionIsBlank() {
        // 使用空白字符串验证 StringUtils.isBlank 的参数拦截逻辑。
        String blankQuestion = " ";

        // 捕获业务异常，确保空问题不会继续调用外部模型。
        BusinessException businessException = assertThrows(BusinessException.class, () -> modelVerificationService.chat(blankQuestion));

        assertEquals(EXPECTED_QUESTION_BLANK_CODE, businessException.getCode());
        assertEquals(EXPECTED_QUESTION_BLANK_MESSAGE, businessException.getMessage());

        // 参数校验失败后不能调用 ChatClient，否则会产生无意义的外部请求和模型费用。
        verifyNoInteractions(basicChatClient);
    }
}
