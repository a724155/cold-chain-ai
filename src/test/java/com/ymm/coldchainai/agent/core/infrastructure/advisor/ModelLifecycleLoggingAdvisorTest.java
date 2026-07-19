package com.ymm.coldchainai.agent.core.infrastructure.advisor;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ModelLifecycleLoggingAdvisor单元测试。
 *
 * <p>该测试不调用真实模型，重点验证模型日志Advisor能够正确执行后续链路，
 * 并兼容没有ChatResponse或模型元数据的返回结果。</p>
 */
class ModelLifecycleLoggingAdvisorTest {

    /**
     * 测试模型元数据为空时Advisor仍能正常返回响应。
     */
    @Test
    void shouldReturnResponseWhenChatResponseIsNull() {
        ChatClientRequest chatClientRequest = new ChatClientRequest(new Prompt("测试模型生命周期日志"), Map.of(
                AgentAdvisorContextKeys.REQUEST_ID, "request-001",
                AgentAdvisorContextKeys.AGENT_CODE, "cold-chain-general"
        ));

        /*
         * 某些测试场景或扩展Advisor可能只返回context。
         * ModelLifecycleLoggingAdvisor必须完成空指针防护，不能因为没有ChatResponse再次抛异常。
         */
        ChatClientResponse expectedChatClientResponse = new ChatClientResponse(null, Map.of());

        CallAdvisorChain callAdvisorChain = mock(CallAdvisorChain.class);
        when(callAdvisorChain.nextCall(chatClientRequest)).thenReturn(expectedChatClientResponse);

        ModelLifecycleLoggingAdvisor advisor = new ModelLifecycleLoggingAdvisor();

        ChatClientResponse actualChatClientResponse = advisor.adviseCall(chatClientRequest, callAdvisorChain);

        assertSame(expectedChatClientResponse, actualChatClientResponse);
        verify(callAdvisorChain).nextCall(chatClientRequest);
    }

    /**
     * 测试模型调用异常会原样向上传递。
     */
    @Test
    void shouldPropagateModelRuntimeException() {
        ChatClientRequest chatClientRequest = new ChatClientRequest(new Prompt("测试模型异常传播"), Map.of(
                AgentAdvisorContextKeys.REQUEST_ID, "request-error",
                AgentAdvisorContextKeys.AGENT_CODE, "cold-chain-general"
        ));

        RuntimeException expectedException = new IllegalStateException("模拟模型服务不可用");

        CallAdvisorChain callAdvisorChain = mock(CallAdvisorChain.class);
        when(callAdvisorChain.nextCall(chatClientRequest)).thenThrow(expectedException);

        ModelLifecycleLoggingAdvisor advisor = new ModelLifecycleLoggingAdvisor();

        RuntimeException actualException = assertThrows(RuntimeException.class, () -> advisor.adviseCall(chatClientRequest, callAdvisorChain));

        assertSame(expectedException, actualException);
    }
}
