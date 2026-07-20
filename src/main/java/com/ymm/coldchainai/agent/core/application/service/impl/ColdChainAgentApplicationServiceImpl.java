package com.ymm.coldchainai.agent.core.application.service.impl;

import com.ymm.coldchainai.agent.core.application.command.AgentChatCommand;
import com.ymm.coldchainai.agent.core.application.dto.AgentAnswerDTO;
import com.ymm.coldchainai.agent.core.application.executor.IAgentExecutor;
import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.application.service.IColdChainAgentApplicationService;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.domain.model.AgentExecution;
import com.ymm.coldchainai.agent.core.domain.repository.IAgentExecutionRepository;
import com.ymm.coldchainai.shared.exception.AgentExecutionException;
import com.ymm.coldchainai.shared.exception.BusinessException;
import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
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
 * <p>该类承担一次Agent问答用例的业务编排职责，包括参数校验、Agent选择、执行任务创建、状态持久化、执行器调用和结果返回。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * Agent选择方式、执行记录保存范围、失败信息展示方式和执行历史保留期限，都需要在开发前与产品确认。当前实现不保存问题和答案原文，只保存执行元数据。</p>
 *
 * <p>在挖矿流程中，该类相当于矿场项目总调度员：先选择合法矿区、开具任务单并登记开工，再安排设备作业，最后根据结果把任务登记为成功或失败。</p>
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
     * Agent执行记录仓储。
     */
    private final IAgentExecutionRepository agentExecutionRepository;


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

        // 注册中心根据agentCode选择Agent。agentCode为空时返回默认Agent；不存在或已停用时直接抛出业务异常，不调用模型。
        AgentDefinition agentDefinition = agentRegistry.getRequiredAgent(command.getAgentCode());

        // requestId 用于关联本次接口响应、Application日志、模型执行日志和异常日志。当前使用无横线UUID，便于复制、检索和后续存入数据库。
        String requestId = UUID.randomUUID().toString().replace("-", "");

        // 创建Agent执行任务单，但此时尚未真正启动模型。在挖矿流程中，相当于项目经理先根据客户需求和矿区档案开具正式作业单。
        AgentExecution agentExecution = AgentExecution.create(requestId, agentDefinition, command.getQuestion());

        // 先把CREATED任务单写入数据库，再推进并持久化RUNNING状态。在挖矿流程中，相当于先登记正式任务，再下达开工指令，不能设备已经开工却没有任何任务档案。
        initializeExecutionPersistence(agentExecution);

        log.info("正式Agent问答开始，requestId={}，agentCode={}，status={}，questionLength={}",
                agentExecution.getRequestId(), agentExecution.getAgentCode(), agentExecution.getStatus(), agentExecution.getQuestionLength());

        String answer;

        try {
            // Agent执行器相当于矿场设备操作员，根据任务单上的矿区编号找到专属设备并真正开始作业。Application层只负责编排，不直接操作ChatClient。
            answer = agentExecutor.execute(requestId, agentDefinition, command.getQuestion());
        } catch (BusinessException exception) {
            // 可预期业务失败同样需要把任务单从RUNNING更新为FAILED。
            markExecutionFailed(agentExecution, exception.getCode(), exception.getMessage(), exception);

            throw exception;
        } catch (Exception exception) {
            // 模型、网络、Advisor或Tool异常统一记录为Agent执行失败。
            markExecutionFailed(agentExecution, AgentErrorCodeEnum.AGENT_EXECUTION_ERROR.getCode(), AgentErrorCodeEnum.AGENT_EXECUTION_ERROR.getMessage(), exception);

            throw new AgentExecutionException(requestId, AgentErrorCodeEnum.AGENT_EXECUTION_ERROR, exception);
        }

        // 模型返回有效答案后，先让领域对象进入SUCCEEDED状态。
        agentExecution.succeed(answer);

        // 只有成功状态准确写入数据库后，接口才向调用方返回成功。
        persistSucceededExecution(agentExecution);

        log.info("正式Agent问答成功，requestId={}，agentCode={}，status={}，costMillis={}，answerLength={}",
                agentExecution.getRequestId(), agentExecution.getAgentCode(), agentExecution.getStatus(), agentExecution.getCostMillis(), agentExecution.getAnswerLength());

        return AgentAnswerDTO.of(agentExecution.getRequestId(), agentExecution.getAgentCode(), agentExecution.getAgentName(), answer, agentExecution.getCostMillis());
    }

    /**
     * 创建并启动Agent执行记录。
     *
     * <p>两个数据库操作都保持短小，不包裹远程模型调用。</p>
     *
     * @param agentExecution Agent执行领域对象
     */
    private void initializeExecutionPersistence(AgentExecution agentExecution) {
        try {
            // 保存最初的CREATED任务记录。
            agentExecutionRepository.saveCreated(agentExecution);

            // 领域对象确认开工后进入RUNNING状态。
            agentExecution.start();

            // 数据库通过CREATED状态条件更新为RUNNING，防止重复开工。
            agentExecutionRepository.updateToRunning(agentExecution);
        } catch (Exception exception) {
            throw new AgentExecutionException(agentExecution.getRequestId(), AgentErrorCodeEnum.AGENT_EXECUTION_PERSISTENCE_ERROR, exception);
        }
    }

    /**
     * 持久化Agent执行成功状态。
     *
     * @param agentExecution 已进入SUCCEEDED状态的Agent执行领域对象
     */
    private void persistSucceededExecution(AgentExecution agentExecution) {
        try {
            agentExecutionRepository.updateToSucceeded(agentExecution);
        } catch (Exception exception) {
            // 模型虽然已经返回答案，但执行记录未能更新成功。为保证审计数据可信，本次接口不能继续假装成功。
            throw new AgentExecutionException(agentExecution.getRequestId(), AgentErrorCodeEnum.AGENT_EXECUTION_PERSISTENCE_ERROR, exception);
        }
    }

    /**
     * 将Agent任务标记并持久化为失败状态。
     *
     * <p>如果失败状态持久化本身再次失败，不覆盖最初的业务或模型异常，而是把持久化异常添加为suppressed异常并记录完整日志。</p>
     *
     * @param agentExecution 当前执行中的Agent任务
     * @param errorCode 本次失败错误编码
     * @param errorMessage 本次失败安全提示
     * @param originalException 最初导致任务失败的异常
     */
    private void markExecutionFailed(AgentExecution agentExecution, Integer errorCode, String errorMessage, Throwable originalException) {
        try {
            agentExecution.fail(errorCode, errorMessage);
            agentExecutionRepository.updateToFailed(agentExecution);

            log.warn("正式Agent问答失败，requestId={}，agentCode={}，status={}，costMillis={}，errorCode={}",
                    agentExecution.getRequestId(), agentExecution.getAgentCode(), agentExecution.getStatus(), agentExecution.getCostMillis(), agentExecution.getErrorCode());
        } catch (Exception persistenceException) {

            // originalException仍然是本次失败的主要原因。addSuppressed用于保留“记录失败状态时又发生数据库异常”这一附加信息。
            if (Objects.nonNull(originalException)) {
                originalException.addSuppressed(persistenceException);
            }

            log.error("Agent失败状态持久化异常，requestId={}，agentCode={}", agentExecution.getRequestId(), agentExecution.getAgentCode(), persistenceException);
        }
    }
}
