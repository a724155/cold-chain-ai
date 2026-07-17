package com.ymm.coldchainai.agent.core.application.service.impl;

import com.ymm.coldchainai.agent.core.application.command.AgentChatCommand;
import com.ymm.coldchainai.agent.core.application.dto.AgentAnswerDTO;
import com.ymm.coldchainai.agent.core.application.executor.IAgentExecutor;
import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.application.service.IColdChainAgentApplicationService;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.shared.exception.AgentExecutionException;
import com.ymm.coldchainai.shared.exception.BusinessException;
import com.ymm.coldchainai.shared.exception.code.AgentErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * 冷运 Agent 应用服务实现。
 *
 * <p>该类承担一次Agent问答用例的业务编排职责，包括参数校验、
 * Agent选择、requestId生成、执行器调用、耗时统计和异常转换。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * Agent选择规则属于业务能力。开发前必须根据PRD确认是由用户选择、
 * 页面场景自动决定，还是后端智能路由。当前实现采用“前端可选传agentCode，
 * 未传时使用默认Agent”的临时约定；真实上线前必须由产品确认，避免后端自行设计后返工。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ColdChainAgentApplicationServiceImpl implements IColdChainAgentApplicationService {

    /**
     * Agent命令为空时使用的业务提示信息。
     */
    private static final String AGENT_COMMAND_IS_NULL_MESSAGE = "Agent请求命令不能为空";

    /**
     * Agent问题为空时使用的业务提示信息。
     */
    private static final String AGENT_QUESTION_IS_BLANK_MESSAGE = "Agent问题不能为空";

    /**
     * Agent执行器。
     *
     * <p>Application Service 只依赖接口，具体 Spring AI 调用由 Infrastructure 实现。</p>
     */
    private final IAgentExecutor agentExecutor;

    /**
     * Agent注册中心。
     */
    private final IAgentRegistry agentRegistry;


    /**
     * 执行一次冷运 Agent 问答。
     *
     * @param command Agent 问答用例命令
     * @return Agent 执行结果
     */
    @Override
    public AgentAnswerDTO chat(AgentChatCommand command) {
        if (Objects.isNull(command)) {
            // 使用Agent模块统一参数错误码，具体message说明当前是Command对象为空。
            throw new BusinessException(AgentErrorCodeEnum.AGENT_PARAMETER_ERROR, AGENT_COMMAND_IS_NULL_MESSAGE);
        }

        if (StringUtils.isBlank(command.getQuestion())) {
            // 使用Agent模块统一参数错误码，具体message说明当前是用户问题为空。
            throw new BusinessException(AgentErrorCodeEnum.AGENT_PARAMETER_ERROR, AGENT_QUESTION_IS_BLANK_MESSAGE);
        }

        /*
         * 注册中心根据agentCode选择Agent。
         * agentCode为空时返回默认Agent；不存在或已停用时直接抛出业务异常，不调用模型。
         */
        AgentDefinition agentDefinition = agentRegistry.getRequiredAgent(command.getAgentCode());

        /*
         * requestId 用于关联本次接口响应、Application日志、模型执行日志和异常日志。
         * 当前使用无横线UUID，便于复制、检索和后续存入数据库。
         */
        String requestId = UUID.randomUUID().toString().replace("-", "");

        // startTimeMillis 记录Agent开始执行时间，用于计算完整调用耗时。
        long startTimeMillis = System.currentTimeMillis();

        // 只记录问题长度，避免将完整用户问题直接写入普通业务日志。
        log.info("正式Agent问答开始，requestId={}，agentCode={}，questionLength={}", requestId, agentDefinition.getAgentCode(), command.getQuestion().length());

        try {
            // 调用Agent执行器，Application层不直接操作Spring AI ChatClient。
            String answer = agentExecutor.execute(requestId, agentDefinition, command.getQuestion());

            // costMillis 表示从Application开始调用到获得完整模型答案的总耗时。
            long costMillis = System.currentTimeMillis() - startTimeMillis;

            log.info("正式Agent问答成功，requestId={}，costMillis={}，answerLength={}", requestId, costMillis, answer.length());

            return AgentAnswerDTO.of(requestId, agentDefinition.getAgentCode(), agentDefinition.getAgentName(), answer, costMillis);
        } catch (BusinessException exception) {
            // 可预期业务异常保持原样向上传递，由全局业务异常处理方法统一处理。
            throw exception;
        } catch (Exception exception) {
            // 计算失败前已经消耗的时间，便于定位模型超时、网络异常或执行器故障。
            long costMillis = System.currentTimeMillis() - startTimeMillis;

            log.warn("正式Agent问答执行失败，requestId={}，costMillis={}，exceptionType={}", requestId, costMillis, exception.getClass().getName());

            // 使用Agent模块统一执行错误码包装原始异常，同时保留本次请求的requestId。
            throw new AgentExecutionException(requestId, AgentErrorCodeEnum.AGENT_EXECUTION_ERROR, exception);
        }
    }
}
