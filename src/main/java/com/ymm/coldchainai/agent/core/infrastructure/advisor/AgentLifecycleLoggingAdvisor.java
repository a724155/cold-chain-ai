package com.ymm.coldchainai.agent.core.infrastructure.advisor;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.util.MapUtil;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import reactor.netty.internal.util.MapUtils;

import java.util.Map;
import java.util.Objects;

/**
 * Agent生命周期日志Advisor。相当于矿场安全审计员，矿场总监控，关心“一次挖矿任务有没有完成、用了多久”；
 *
 * <p>该Advisor包裹一次完整的同步ChatClient调用链，负责记录Agent执行链开始、成功、失败和总耗时。</p>
 *
 * <p>该Advisor不会记录完整问题和模型答案，避免用户输入、司机信息、订单信息、支付信息或公司内部规则泄露到普通日志中。</p>
 *
 * <p>当前项目默认使用同步call()，因此这里只实现CallAdvisor。后续如果产品明确要求Token级实时流式输出，再单独实现StreamAdvisor，
 * 不能误认为Flux.just(answer)会触发StreamAdvisor。</p>
 */
@Slf4j
@Component
public class AgentLifecycleLoggingAdvisor implements CallAdvisor {

    /**
     * Agent生命周期Advisor执行顺序。
     *
     * <p>该顺序早于Spring AI默认的ToolCallingAdvisor，
     * 因此未来发生多轮Tool Calling时，本Advisor仍然只包裹一次完整Agent请求。</p>
     */
    private static final int ADVISOR_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    /**
     * Advisor上下文不存在有效值时使用的占位内容。
     */
    private static final String UNKNOWN_CONTEXT_VALUE = "unknown";

    /**
     * 返回当前Advisor稳定名称。
     *
     * @return Advisor名称
     */
    @Override
    public String getName() {
        return AgentLifecycleLoggingAdvisor.class.getSimpleName();
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
     * 记录一次完整Agent调用链的生命周期。
     *
     * @param chatClientRequest 当前ChatClient请求
     * @param callAdvisorChain 后续同步Advisor调用链
     * @return 后续调用链返回的ChatClient响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        // 从Advisor上下文获取requestId，用于串联Application、Advisor、模型和异常日志。
        String requestId = resolveContextValue(chatClientRequest, AgentAdvisorContextKeys.REQUEST_ID);

        // 从Advisor上下文获取本次实际执行的Agent编码。
        String agentCode = resolveContextValue(chatClientRequest, AgentAdvisorContextKeys.AGENT_CODE);

        // Agent名称用于提高日志可读性，但不能替代稳定的agentCode。
        String agentName = resolveContextValue(chatClientRequest, AgentAdvisorContextKeys.AGENT_NAME);

        /*
         * 保存当前线程原有的MDC值。
         * Tomcat线程会被线程池复用，如果不在finally中恢复或删除，
         * 下一个请求可能错误继承上一个请求的requestId。
         */
        String previousRequestId = MDC.get(AgentAdvisorContextKeys.MDC_REQUEST_ID);
        String previousAgentCode = MDC.get(AgentAdvisorContextKeys.MDC_AGENT_CODE);

        // 将当前请求标识写入MDC，使下游模型、HTTP客户端和Tool日志能够自动携带requestId。
        putMdcValue(AgentAdvisorContextKeys.MDC_REQUEST_ID, requestId);

        // 将当前Agent编码写入MDC，使同一请求下的日志可以识别实际执行的Agent。
        putMdcValue(AgentAdvisorContextKeys.MDC_AGENT_CODE, agentCode);

        // startTimeMillis用于计算一次完整Agent调用链的执行耗时。
        long startTimeMillis = System.currentTimeMillis();

        log.info("Agent调用链开始，requestId={}，agentCode={}，agentName={}", requestId, agentCode, agentName);

        try {
            /*
             * nextCall()必须调用，否则后续Advisor、ToolCallingAdvisor和真实模型都不会执行。
             * 返回值必须原样向上传递，日志Advisor不能擅自修改正常模型响应。
             */
            ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

            // costMillis表示当前Advisor包裹的完整Agent调用链总耗时。
            long costMillis = System.currentTimeMillis() - startTimeMillis;

            log.info("Agent调用链成功，requestId={}，agentCode={}，costMillis={}", requestId, agentCode, costMillis);

            return chatClientResponse;
        } catch (RuntimeException exception) {
            // 即使调用失败，也要计算失败前已经消耗的时间，便于定位模型超时和Tool执行故障。
            long costMillis = System.currentTimeMillis() - startTimeMillis;

            /*
             * 此处只记录异常类型，不记录完整异常堆栈。
             * 完整堆栈最终由GlobalExceptionHandler统一记录，避免同一个异常被重复打印多次。
             */
            log.warn("Agent调用链失败，requestId={}，agentCode={}，costMillis={}，exceptionType={}", requestId, agentCode, costMillis, exception.getClass().getName());

            throw exception;
        } finally {
            /*
             * 无论模型成功还是失败，都必须恢复MDC。
             * 这是线程池环境下防止日志上下文串请求的关键兜底。
             */
            restoreMdcValue(AgentAdvisorContextKeys.MDC_REQUEST_ID, previousRequestId);
            restoreMdcValue(AgentAdvisorContextKeys.MDC_AGENT_CODE, previousAgentCode);
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
            return UNKNOWN_CONTEXT_VALUE;
        }

        // 对context整体进行判空，避免自定义测试或异常请求造成空指针。
        Map<String, Object> context = chatClientRequest.context();

        if (Objects.isNull(context) || context.isEmpty()) {
            return UNKNOWN_CONTEXT_VALUE;
        }

        Object contextValue = context.get(contextKey);

        if (Objects.isNull(contextValue)) {
            return UNKNOWN_CONTEXT_VALUE;
        }

        return StringUtils.defaultIfBlank(String.valueOf(contextValue), UNKNOWN_CONTEXT_VALUE);
    }

    /**
     * 向MDC写入有效字符串值。
     *
     * @param key MDC字段名称
     * @param value MDC字段值
     */
    private void putMdcValue(String key, String value) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(value) || UNKNOWN_CONTEXT_VALUE.equals(value)) {
            return;
        }

        MDC.put(key, value);
    }

    /**
     * 恢复当前线程进入Advisor前的MDC值。
     *
     * @param key MDC字段名称
     * @param previousValue 进入Advisor前的字段值
     */
    private void restoreMdcValue(String key, String previousValue) {
        if (StringUtils.isBlank(key)) {
            return;
        }

        if (StringUtils.isBlank(previousValue)) {
            // 原来没有该字段时必须删除，防止Tomcat线程复用后污染下一次请求。
            MDC.remove(key);
            return;
        }

        // 原来已经存在值时恢复原值，避免破坏外层链路追踪上下文。
        MDC.put(key, previousValue);
    }
}