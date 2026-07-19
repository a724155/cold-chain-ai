package com.ymm.coldchainai.agent.core.infrastructure.advisor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentLifecycleLoggingAdvisor单元测试。
 *
 * <p>该测试不调用真实模型，只验证Advisor能够继续执行后续链路、
 * 原样返回响应、继续抛出异常并正确清理MDC。</p>
 */
class AgentLifecycleLoggingAdvisorTest {

    /**
     * 每个测试结束后清理MDC，防止测试线程复用造成测试之间相互影响。
     */
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /**
     * 测试Advisor会执行后续调用链并原样返回响应。
     */
    @Test
    void shouldContinueAdvisorChainAndReturnResponse() {
        // 创建包含requestId和agentCode的真实ChatClientRequest。
        ChatClientRequest chatClientRequest = new ChatClientRequest(new Prompt("测试Agent生命周期日志"), Map.of(
                AgentAdvisorContextKeys.REQUEST_ID, "request-001",
                AgentAdvisorContextKeys.AGENT_CODE, "cold-chain-general",
                AgentAdvisorContextKeys.AGENT_NAME, "冷运综合业务助手"
        ));

        // 当前测试不关心模型正文，因此创建不包含ChatResponse的响应对象。
        ChatClientResponse expectedChatClientResponse = new ChatClientResponse(null, Map.of());

        // 模拟后续Advisor调用链，避免产生真实模型费用。
        CallAdvisorChain callAdvisorChain = mock(CallAdvisorChain.class);
        when(callAdvisorChain.nextCall(chatClientRequest)).thenReturn(expectedChatClientResponse);

        AgentLifecycleLoggingAdvisor advisor = new AgentLifecycleLoggingAdvisor();

        ChatClientResponse actualChatClientResponse = advisor.adviseCall(chatClientRequest, callAdvisorChain);

        assertSame(expectedChatClientResponse, actualChatClientResponse);

        // 验证日志Advisor没有截断后续模型调用链。
        verify(callAdvisorChain).nextCall(chatClientRequest);

        // Advisor执行完成后必须删除自己写入的MDC值。
        assertNull(MDC.get(AgentAdvisorContextKeys.MDC_REQUEST_ID));
        assertNull(MDC.get(AgentAdvisorContextKeys.MDC_AGENT_CODE));
    }

    /**
     * 测试Advisor会恢复进入调用链前已经存在的MDC值。
     */
    @Test
    void shouldRestorePreviousMdcValues() {
        // 模拟更外层链路已经设置requestId和agentCode。
        MDC.put(AgentAdvisorContextKeys.MDC_REQUEST_ID, "previous-request");
        MDC.put(AgentAdvisorContextKeys.MDC_AGENT_CODE, "previous-agent");

        ChatClientRequest chatClientRequest = new ChatClientRequest(new Prompt("测试MDC恢复"), Map.of(
                AgentAdvisorContextKeys.REQUEST_ID, "current-request",
                AgentAdvisorContextKeys.AGENT_CODE, "cold-chain-general",
                AgentAdvisorContextKeys.AGENT_NAME, "冷运综合业务助手"
        ));

        ChatClientResponse chatClientResponse = new ChatClientResponse(null, Map.of());

        CallAdvisorChain callAdvisorChain = mock(CallAdvisorChain.class);
        when(callAdvisorChain.nextCall(chatClientRequest)).thenReturn(chatClientResponse);

        AgentLifecycleLoggingAdvisor advisor = new AgentLifecycleLoggingAdvisor();

        advisor.adviseCall(chatClientRequest, callAdvisorChain);

        assertEquals("previous-request", MDC.get(AgentAdvisorContextKeys.MDC_REQUEST_ID));
        assertEquals("previous-agent", MDC.get(AgentAdvisorContextKeys.MDC_AGENT_CODE));
    }

    /**
     * 测试后续调用链发生异常时Advisor会原样继续抛出。
     */
    @Test
    void shouldPropagateRuntimeExceptionAndClearMdc() {
        ChatClientRequest chatClientRequest = new ChatClientRequest(new Prompt("测试异常传播"), Map.of(
                AgentAdvisorContextKeys.REQUEST_ID, "request-error",
                AgentAdvisorContextKeys.AGENT_CODE, "cold-chain-general"
        ));

        // 创建预期异常，用于验证Advisor不会吞掉模型异常或替换原始异常。
        RuntimeException expectedException = new IllegalStateException("模拟模型调用失败");

        CallAdvisorChain callAdvisorChain = mock(CallAdvisorChain.class);
        when(callAdvisorChain.nextCall(chatClientRequest)).thenThrow(expectedException);

        AgentLifecycleLoggingAdvisor advisor = new AgentLifecycleLoggingAdvisor();

        RuntimeException actualException = assertThrows(RuntimeException.class, () -> advisor.adviseCall(chatClientRequest, callAdvisorChain));

        assertSame(expectedException, actualException);
        assertNull(MDC.get(AgentAdvisorContextKeys.MDC_REQUEST_ID));
        assertNull(MDC.get(AgentAdvisorContextKeys.MDC_AGENT_CODE));
    }
}
