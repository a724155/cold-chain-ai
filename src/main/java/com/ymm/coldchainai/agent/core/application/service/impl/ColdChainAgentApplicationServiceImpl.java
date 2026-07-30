package com.ymm.coldchainai.agent.core.application.service.impl;

import com.ymm.coldchainai.agent.conversation.application.command.AppendAgentChatMessageCommand;
import com.ymm.coldchainai.agent.conversation.application.command.ResolveAgentConversationCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentConversationDTO;
import com.ymm.coldchainai.agent.conversation.application.service.IAgentChatHistoryApplicationService;
import com.ymm.coldchainai.agent.conversation.application.service.IAgentConversationApplicationService;
import com.ymm.coldchainai.agent.conversation.domain.enumtype.ChatMessageRoleEnum;
import com.ymm.coldchainai.agent.core.application.command.AgentChatCommand;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.dto.AgentAnswerDTO;
import com.ymm.coldchainai.agent.core.application.executor.IAgentExecutor;
import com.ymm.coldchainai.agent.core.application.memory.model.AgentMemoryMessage;
import com.ymm.coldchainai.agent.core.application.memory.provider.IAgentConversationMemoryProvider;
import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.application.service.IColdChainAgentApplicationService;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.agent.core.domain.model.AgentExecution;
import com.ymm.coldchainai.agent.core.domain.repository.IAgentExecutionRepository;
import com.ymm.coldchainai.shared.exception.AgentExecutionException;
import com.ymm.coldchainai.shared.exception.BusinessException;
import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import com.ymm.coldchainai.shared.security.context.ICurrentUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 冷运Agent应用服务实现。
 *
 * <p>该类承担一次正式Agent问答用例的业务编排职责，包括参数校验、Agent选择、
 * Conversation解析、USER和ASSISTANT消息归档、执行任务创建、状态持久化、
 * 执行器调用和结果返回。</p>
 *
 * <p><strong>事务边界说明：</strong>
 * 当前chat()方法故意不添加@Transactional。
 * USER消息和ASSISTANT消息分别通过IAgentChatHistoryApplicationService中的独立短事务保存，
 * 两个短事务之间执行远程ChatModel和Tool Calling。</p>
 *
 * <p>这样能够保证执行模型时不持有Conversation行锁。
 * 如果把整个chat()方法放入事务，SELECT ... FOR UPDATE获取的行锁会在模型调用期间一直不释放，
 * 同一个Conversation的后续请求会被长时间阻塞，同时占用数据库连接和事务资源。</p>
 *
 * <p><strong>与订单加锁的共同点：</strong>
 * 订单和Conversation都通过FOR UPDATE保护“读取旧状态后生成新状态”的读后写链路；
 * 都要求锁定查询和数据库写入位于同一个本地短事务；
 * 都禁止在持锁事务中调用RPC、支付渠道、HTTP接口或者大模型。</p>
 *
 * <p><strong>与订单加锁的差异：</strong>
 * 订单加锁通常保护支付状态、履约状态、重复扣减和资金安全；
 * Conversation加锁主要保护ChatMessage的sequenceNo唯一连续、
 * messageCount准确以及消息明细与会话统计一致。</p>
 *
 * <p><strong>失败语义：</strong>
 * USER消息会在模型调用前完成持久化。
 * 如果模型或者Tool调用失败，USER消息仍然保留，ASSISTANT消息不会伪造写入；
 * AgentExecution则会记录为FAILED，后续可以根据requestId排查失败原因。</p>
 *
 * <p><strong>产品需求提醒：</strong>
 * Agent选择方式、会话创建规则、失败问题展示、重试方式、
 * 执行历史和聊天历史保留期限，都需要在开发前与产品及前端确认。</p>
 *
 * <p>在挖矿流程中，该类相当于矿场项目总调度员：
 * 先确认矿区并建立项目档案，登记客户要求后释放档案锁，
 * 再安排设备进入矿区作业，最终把开采报告重新归档并完成任务登记。</p>
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
     * Conversation应用服务。
     *
     * <p>负责创建新Conversation或者解析已有Conversation，并校验用户、租户、会话状态和Agent绑定关系。</p>
     *
     * <p>在挖矿流程中，该服务相当于项目档案管理中心，负责创建或者找到同一张长期项目任务单。</p>
     */
    private final IAgentConversationApplicationService agentConversationApplicationService;

    /**
     * Chat History应用服务。
     *
     * <p>每次调用appendMessage()都会通过自身Spring代理开启一个独立短事务，在短事务内锁定Conversation、分配sequenceNo、保存消息并更新会话统计。</p>
     *
     * <p>在挖矿流程中，该服务相当于项目档案登记员，负责为客户要求和最终报告分配连续档案编号并完成归档。</p>
     */
    private final IAgentChatHistoryApplicationService agentChatHistoryApplicationService;

    /**
     * Agent Conversation Memory提供者。
     *
     * <p>负责从MySQL Chat History读取最近完整问答，并转换成与Spring AI无关的Application层Memory消息。</p>
     *
     * <p>在挖矿流程中，该组件相当于档案筛选员：在设备开工前，从完整项目档案中整理最近有效记录并放入随身资料袋。</p>
     */
    private final IAgentConversationMemoryProvider agentConversationMemoryProvider;

    /**
     * 当前登录用户上下文。
     *
     * <p>Application层通过该端口读取受信任用户和租户信息，
     * 不从AgentChatRequest或模型参数中接收tenantId。</p>
     */
    private final ICurrentUserContext currentUserContext;


    /**
     * 执行一次冷运Agent正式问答。
     *
     * <p>该方法不添加@Transactional，事务边界由Chat History服务中的appendMessage()承担。
     * 每次appendMessage()执行完成后，其事务已经提交或者回滚，
     * 对Conversation持有的FOR UPDATE行锁也已经释放。</p>
     *
     * <p>执行顺序：</p>
     *
     * <p>1. 校验Command和用户问题；</p>
     * <p>2. 选择实际执行的Agent；</p>
     * <p>3. 获取受信任用户与租户身份；</p>
     * <p>4. 创建或者解析Conversation；</p>
     * <p>5. 创建并持久化AgentExecution；</p>
     * <p>6. 短事务保存USER消息；</p>
     * <p>7. 不持锁调用模型和Tool；</p>
     * <p>8. 短事务保存ASSISTANT消息；</p>
     * <p>9. 持久化执行成功状态并返回结果。</p>
     *
     * @param command Agent问答用例命令
     * @return 包含Conversation、本轮请求和模型答案的执行结果
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

        // 当前用户和租户身份来自后端认证上下文，不能从Controller请求或模型Tool参数中获取。在挖矿流程中，相当于项目经理先核验客户身份并附上真实许可证，再创建正式挖矿任务。
        AgentInvocationContext agentInvocationContext = AgentInvocationContext.create(currentUserContext.getCurrentUserId(),
                currentUserContext.getCurrentTenantId());

        /*
         * conversationId为空时创建新Conversation，非空时继续已有Conversation。
         * 传入实际AgentDefinition的agentCode，而不是直接使用未经标准化的请求值，保证Conversation绑定的是注册中心认可的稳定Agent编码。
         */
        AgentConversationDTO conversationDTO = agentConversationApplicationService.resolveConversation(
                ResolveAgentConversationCommand.create(command.getConversationId(), agentDefinition.getAgentCode(), agentInvocationContext));


        // requestId 用于关联本次接口响应、Application日志、模型执行日志和异常日志。当前使用无横线UUID，便于复制、检索和后续存入数据库。
        String requestId = UUID.randomUUID().toString().replace("-", "");

        // 创建Agent执行任务单，但此时尚未真正启动模型。在挖矿流程中，相当于项目经理先根据客户需求和矿区档案开具正式作业单。
        AgentExecution agentExecution = AgentExecution.create(requestId, agentDefinition, command.getQuestion());

        // 先把CREATED任务单写入数据库，再推进并持久化RUNNING状态。在挖矿流程中，相当于先登记正式任务，再下达开工指令，不能设备已经开工却没有任何任务档案。
        initializeExecutionPersistence(agentExecution);

        String answer;

        try {

            /*
             * 在保存本轮USER问题之前读取历史Memory。
             * 这样Memory只包含此前已经完成的问答，本轮question不会同时出现在历史列表和user(question)中造成重复。
             * 新Conversation没有历史，直接返回空List；已有Conversation则通过用户和租户权限条件读取最近完整问答。
             */
            List<AgentMemoryMessage> memoryMessageList = loadConversationMemory(conversationDTO, agentInvocationContext);

            /*
             * 短事务1：保存USER问题。
             * appendMessage()位于另一个Spring Bean中，通过代理开启事务：
             * SELECT Conversation FOR UPDATE
             * → 分配sequenceNo → INSERT USER消息 → 更新messageCount和version → COMMIT并释放行锁。
             * 返回当前方法以后，Conversation行锁已经释放，后面的模型调用不会占用该数据库锁。
             */
            appendChatMessage(conversationDTO, requestId, ChatMessageRoleEnum.USER, command.getQuestion(), agentInvocationContext);

            log.info("正式Agent问答开始，conversationId={}，requestId={}，agentCode={}，status={}，questionLength={}",
                    conversationDTO.getConversationId(), agentExecution.getRequestId(), agentExecution.getAgentCode(),
                    agentExecution.getStatus(), agentExecution.getQuestionLength());

            /*
             * 远程模型和Tool Calling调用发生在两个ChatMessage短事务之间。当前线程此时没有持有Conversation的FOR UPDATE行锁。
             * 与订单场景相同，不能在订单行锁事务中等待支付渠道或者RPC；Conversation也不能在行锁事务中等待大模型返回。
             */
            answer = agentExecutor.execute(requestId, agentDefinition, agentInvocationContext, memoryMessageList, command.getQuestion());

            /*
             * 短事务2：保存ASSISTANT最终回答。
             * 该事务会重新锁定Conversation并读取最新messageCount，因此正常情况下会为ASSISTANT分配USER之后的下一个sequenceNo。
             */
            appendChatMessage(conversationDTO, requestId, ChatMessageRoleEnum.ASSISTANT, answer, agentInvocationContext);
        } catch (BusinessException exception) {
            // 可预期业务失败同样需要把任务单从RUNNING更新为FAILED。
            markExecutionFailed(agentExecution, exception.getCode(), exception.getMessage(), exception);
            throw exception;
        } catch (Exception exception) {
            /*
             * 模型、网络、Advisor、Tool或者Chat History持久化异常，统一记录为Agent执行失败。
             * USER消息如果已经提交不会被远程调用失败回滚；ASSISTANT消息只有在模型成功并且归档成功时才会存在。
             */
            markExecutionFailed(agentExecution, AgentErrorCodeEnum.AGENT_EXECUTION_ERROR.getCode(),
                    AgentErrorCodeEnum.AGENT_EXECUTION_ERROR.getMessage(), exception);

            throw new AgentExecutionException(requestId, AgentErrorCodeEnum.AGENT_EXECUTION_ERROR, exception);
        }

        // 模型回答和ASSISTANT消息均保存成功后，领域对象才进入SUCCEEDED状态。
        agentExecution.succeed(answer);

        // 只有成功状态准确写入数据库后，接口才向调用方返回成功。
        persistSucceededExecution(agentExecution);

        log.info("正式Agent问答成功，conversationId={}，requestId={}，agentCode={}，status={}，costMillis={}，answerLength={}",
                conversationDTO.getConversationId(), agentExecution.getRequestId(), agentExecution.getAgentCode(),
                agentExecution.getStatus(), agentExecution.getCostMillis(), agentExecution.getAnswerLength());

        return AgentAnswerDTO.of(agentExecution.getRequestId(), conversationDTO.getConversationId(), agentExecution.getAgentCode(),
                agentExecution.getAgentName(), answer, agentExecution.getCostMillis());
    }

    /**
     * 加载当前Conversation最近的有效模型上下文。
     *
     * <p>新Conversation尚未产生任何历史消息，直接返回空列表，
     * 避免额外执行一次Conversation和ChatMessage查询。</p>
     *
     * <p>已有Conversation通过IAgentConversationMemoryProvider加载最近完整问答。
     * Provider会使用conversationId、currentUserId和currentTenantId完成数据权限校验，
     * 并过滤执行失败留下的孤立USER消息。</p>
     *
     * <p>该读取发生在本轮USER消息保存之前，
     * 保证当前问题只通过Executor的user(question)进入一次Prompt。</p>
     *
     * <p>在挖矿流程中，新项目没有旧档案，不需要调阅；
     * 继续旧项目时，档案筛选员会在客户新要求归档前先整理此前完成的开采记录。</p>
     *
     * @param conversationDTO 当前问答所属Conversation
     * @param agentInvocationContext 当前受信任用户和租户上下文
     * @return 最近有效Memory消息，新Conversation返回空列表
     */
    private List<AgentMemoryMessage> loadConversationMemory(AgentConversationDTO conversationDTO, AgentInvocationContext agentInvocationContext) {

        if (Objects.isNull(conversationDTO)) {
            throw new IllegalArgumentException("加载Chat Memory时Conversation DTO不能为空");
        }

        if (Objects.isNull(agentInvocationContext)) {
            throw new IllegalArgumentException("加载Chat Memory时Agent调用上下文不能为空");
        }

        // 新建Conversation不存在历史消息，避免执行没有意义的数据库查询。
        if (Boolean.TRUE.equals(conversationDTO.getNewlyCreated())) {
            return List.of();
        }

        return agentConversationMemoryProvider.loadRecentMemory(conversationDTO.getConversationId(), agentInvocationContext);
    }

    /**
     * 在独立短事务中向Conversation追加一条聊天消息。
     *
     * <p>该方法本身没有@Transactional，
     * 它调用的是由Spring管理的IAgentChatHistoryApplicationService代理对象，
     * 实际事务由AgentChatHistoryApplicationServiceImpl.appendMessage()开启。</p>
     *
     * <p>每次调用只处理一条消息：</p>
     *
     * <p>1. USER问题和ASSISTANT回答分别提交；</p>
     * <p>2. 两次消息保存之间不共享数据库事务；</p>
     * <p>3. 模型调用期间不持有Conversation行锁；</p>
     * <p>4. 同一轮两条消息使用相同requestId，便于审计关联。</p>
     *
     * <p>在挖矿流程中，该方法相当于把一条已经准备好的作业记录交给档案员：
     * 档案员锁定任务单、分配编号、归档并立即释放任务单，
     * 不会在矿工整个开采期间持续占用档案。</p>
     *
     * @param conversationDTO 当前问答所属Conversation
     * @param requestId 本轮Agent请求唯一标识
     * @param messageRole 消息角色
     * @param messageContent 消息完整正文
     * @param agentInvocationContext 当前受信任用户和租户上下文
     */
    private void appendChatMessage(AgentConversationDTO conversationDTO, String requestId, ChatMessageRoleEnum messageRole,
                                   String messageContent, AgentInvocationContext agentInvocationContext) {

        if (Objects.isNull(conversationDTO)) {
            throw new IllegalArgumentException("Agent会话DTO不能为空");
        }

        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("Agent请求标识不能为空");
        }

        if (Objects.isNull(messageRole)) {
            throw new IllegalArgumentException("聊天消息角色不能为空");
        }

        if (StringUtils.isBlank(messageContent)) {
            throw new IllegalArgumentException("聊天消息正文不能为空");
        }

        if (Objects.isNull(agentInvocationContext)) {
            throw new IllegalArgumentException("Agent调用上下文不能为空");
        }

        AppendAgentChatMessageCommand appendMessageCommand = AppendAgentChatMessageCommand.create(
                        conversationDTO.getConversationId(), requestId, messageRole, messageContent, agentInvocationContext);

        // 调用独立Bean的事务方法，当前调用返回时消息事务已经结束并释放Conversation行锁。
        agentChatHistoryApplicationService.appendMessage(appendMessageCommand);
    }

    /**
     * 创建并启动Agent执行记录。
     *
     * <p>两个数据库操作都保持短小，不包裹远程模型调用。</p>
     *
     * <p>在挖矿流程中，相当于先建立正式开工任务档案，
     * 再将任务状态更新为正在作业。</p>
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
     * @param agentExecution    当前执行中的Agent任务
     * @param errorCode         本次失败错误编码
     * @param errorMessage      本次失败安全提示
     * @param originalException 最初导致任务失败的异常
     */
    private void markExecutionFailed(AgentExecution agentExecution, Integer errorCode, String errorMessage, Throwable originalException) {
        try {
            agentExecution.fail(errorCode, errorMessage);
            agentExecutionRepository.updateToFailed(agentExecution);

            log.warn("正式Agent问答失败，requestId={}，agentCode={}，status={}，costMillis={}，errorCode={}",
                    agentExecution.getRequestId(), agentExecution.getAgentCode(), agentExecution.getStatus(),
                    agentExecution.getCostMillis(), agentExecution.getErrorCode());
        } catch (Exception persistenceException) {

            // originalException仍然是本次失败的主要原因。addSuppressed用于保留“记录失败状态时又发生数据库异常”这一附加信息。
            if (Objects.nonNull(originalException)) {
                originalException.addSuppressed(persistenceException);
            }

            log.error("Agent失败状态持久化异常，requestId={}，agentCode={}", agentExecution.getRequestId(), agentExecution.getAgentCode(), persistenceException);
        }
    }
}
