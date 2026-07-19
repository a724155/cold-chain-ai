package com.ymm.coldchainai.agent.core.domain.model;

import com.ymm.coldchainai.agent.core.domain.enumtype.AgentExecutionStatusEnum;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 一次Agent执行任务的领域对象。
 *
 * <p>该对象负责维护Agent执行任务的状态、开始时间、结束时间、执行耗时、答案长度和失败信息等核心规则。</p>
 *
 * <p>在挖矿流程中，该对象相当于一张完整的矿场作业任务单：
 * requestId是任务单编号，agentCode是目标矿区编号，
 * status是当前作业状态，开始和结束时间用于记录真实作业过程。
 * 如果没有这张任务单，日志、数据库和审计模块就无法确认一次挖矿任务究竟执行到了哪里。</p>
 *
 * <p><strong>需求与安全确认提醒：</strong>
 * 后续将该对象持久化之前，需要与产品、安全和运维确认执行记录的查询用途、
 * 保存期限以及允许保存的字段。当前只保存问题和答案的长度，不保存原始文本，
 * 避免用户问题、订单信息和模型答案被默认长期写入数据库。</p>
 */
@Getter
public class AgentExecution {

    /**
     * requestId为空时使用的异常信息。
     */
    private static final String REQUEST_ID_IS_BLANK_MESSAGE = "Agent执行记录的requestId不能为空";

    /**
     * Agent定义为空时使用的异常信息。
     */
    private static final String AGENT_DEFINITION_IS_NULL_MESSAGE = "Agent执行记录的Agent定义不能为空";

    /**
     * Agent编码为空时使用的异常信息。
     */
    private static final String AGENT_CODE_IS_BLANK_MESSAGE = "Agent执行记录的agentCode不能为空";

    /**
     * Agent名称为空时使用的异常信息。
     */
    private static final String AGENT_NAME_IS_BLANK_MESSAGE = "Agent执行记录的agentName不能为空";

    /**
     * 用户问题为空时使用的异常信息。
     */
    private static final String QUESTION_IS_BLANK_MESSAGE = "Agent执行记录的用户问题不能为空";

    /**
     * 模型答案为空时使用的异常信息。
     */
    private static final String ANSWER_IS_BLANK_MESSAGE = "Agent执行成功时模型答案不能为空";

    /**
     * 失败错误码为空时使用的异常信息。
     */
    private static final String ERROR_CODE_IS_NULL_MESSAGE = "Agent执行失败时错误码不能为空";

    /**
     * 失败提示为空时使用的默认信息。
     */
    private static final String DEFAULT_ERROR_MESSAGE = "Agent执行失败";

    /**
     * 本次Agent请求唯一标识。
     */
    private final String requestId;

    /**
     * 本次实际执行的Agent稳定编码。
     */
    private final String agentCode;

    /**
     * 本次实际执行的Agent展示名称。
     */
    private final String agentName;

    /**
     * 用户问题字符长度。
     *
     * <p>当前不在执行记录中保存原始问题，只记录长度用于统计和问题分析。</p>
     */
    private final Integer questionLength;

    /**
     * 当前Agent执行状态。
     */
    private AgentExecutionStatusEnum status;

    /**
     * 模型最终答案字符长度。
     *
     * <p>只有执行成功后才会赋值。</p>
     */
    private Integer answerLength;

    /**
     * 执行失败时的错误编码。
     */
    private Integer errorCode;

    /**
     * 执行失败时的安全提示信息。
     *
     * <p>不能直接保存模型密钥、数据库密码或完整原始异常堆栈。</p>
     */
    private String errorMessage;

    /**
     * 执行记录创建时间。
     */
    private final LocalDateTime createTime;

    /**
     * Agent真正开始执行的时间。
     */
    private LocalDateTime startTime;

    /**
     * Agent进入成功或失败最终状态的时间。
     */
    private LocalDateTime finishTime;

    /**
     * Agent实际执行耗时，单位为毫秒。
     */
    private Long costMillis;

    /**
     * Agent开始执行时的单调时钟值。
     *
     * <p>该字段只用于准确计算耗时，不属于未来需要保存到数据库的业务字段。
     * 使用System.nanoTime可以降低系统时间被手动调整时对耗时统计的影响。</p>
     */
    private long startNanoTime;

    /**
     * 创建Agent执行领域对象。
     *
     * <p>构造方法保持私有，外部必须通过create工厂方法创建，避免绕过基础校验生成不合法的任务记录。</p>
     * <p>在挖矿流程中，这相当于由统一任务系统开具作业单，不允许矿工随手创建一张缺少任务编号或矿区编号的无效单据。</p>
     *
     * @param requestId 本次Agent请求唯一标识
     * @param agentDefinition 本次执行的Agent定义
     * @param questionLength 用户问题字符长度
     */
    private AgentExecution(String requestId, AgentDefinition agentDefinition, Integer questionLength) {
        this.requestId = requestId;
        this.agentCode = agentDefinition.getAgentCode();
        this.agentName = agentDefinition.getAgentName();
        this.questionLength = questionLength;
        this.status = AgentExecutionStatusEnum.CREATED;
        this.createTime = LocalDateTime.now();
    }

    /**
     * 创建一条处于已创建状态的Agent执行记录。
     *
     * @param requestId 本次Agent请求唯一标识
     * @param agentDefinition 本次实际执行的Agent定义
     * @param question 用户提交的问题
     * @return 已完成基础校验的Agent执行领域对象
     */
    public static AgentExecution create(String requestId, AgentDefinition agentDefinition, String question) {
        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException(REQUEST_ID_IS_BLANK_MESSAGE);
        }

        if (Objects.isNull(agentDefinition)) {
            throw new IllegalArgumentException(AGENT_DEFINITION_IS_NULL_MESSAGE);
        }

        if (StringUtils.isBlank(agentDefinition.getAgentCode())) {
            throw new IllegalArgumentException(AGENT_CODE_IS_BLANK_MESSAGE);
        }

        if (StringUtils.isBlank(agentDefinition.getAgentName())) {
            throw new IllegalArgumentException(AGENT_NAME_IS_BLANK_MESSAGE);
        }

        if (StringUtils.isBlank(question)) {
            throw new IllegalArgumentException(QUESTION_IS_BLANK_MESSAGE);
        }

        // 只记录问题长度，不把用户原始问题默认写入执行记录。
        return new AgentExecution(requestId, agentDefinition, question.length());
    }

    /**
     * 将Agent任务从已创建状态推进到执行中状态。
     *
     * <p>在挖矿流程中，这一步相当于项目经理正式下达开工指令。
     * 如果任务单还没有创建，或者设备已经完成作业，就不能再次开工。</p>
     */
    public void start() {
        validateCurrentStatus(AgentExecutionStatusEnum.CREATED);

        // 记录人类可读的开始时间，后续可用于数据库审计和页面展示。
        this.startTime = LocalDateTime.now();

        // 单调时钟只用于耗时计算，不受系统日期时间回拨影响。
        this.startNanoTime = System.nanoTime();

        this.status = AgentExecutionStatusEnum.RUNNING;
    }

    /**
     * 将执行中的Agent任务标记为成功。
     *
     * @param answer 模型生成的最终答案
     */
    public void succeed(String answer) {
        validateCurrentStatus(AgentExecutionStatusEnum.RUNNING);

        if (StringUtils.isBlank(answer)) {
            throw new IllegalArgumentException(ANSWER_IS_BLANK_MESSAGE);
        }

        // 只记录答案长度，不在执行记录中默认保存完整模型答案。
        this.answerLength = answer.length();
        this.status = AgentExecutionStatusEnum.SUCCEEDED;

        finishExecution();
    }

    /**
     * 将执行中的Agent任务标记为失败。
     *
     * @param errorCode 业务错误或系统错误编码
     * @param errorMessage 可以安全记录的失败提示
     */
    public void fail(Integer errorCode, String errorMessage) {
        validateCurrentStatus(AgentExecutionStatusEnum.RUNNING);

        if (Objects.isNull(errorCode)) {
            throw new IllegalArgumentException(ERROR_CODE_IS_NULL_MESSAGE);
        }

        this.errorCode = errorCode;
        this.errorMessage = StringUtils.defaultIfBlank(errorMessage, DEFAULT_ERROR_MESSAGE);
        this.status = AgentExecutionStatusEnum.FAILED;

        finishExecution();
    }

    /**
     * 校验执行记录当前状态是否符合下一步流转要求。
     *
     * @param expectedStatus 当前方法要求的前置状态
     */
    private void validateCurrentStatus(AgentExecutionStatusEnum expectedStatus) {
        if (Objects.equals(expectedStatus, status)) {
            return;
        }

        /*
         * 状态不符合预期通常代表内部代码调用顺序错误。
         * 例如任务尚未start就直接succeed，或者成功以后又被标记为失败。
         */
        String errorMessage = "Agent执行状态流转错误，requestId=%s，expectedStatus=%s，actualStatus=%s".formatted(requestId, expectedStatus, status);
        throw new IllegalStateException(errorMessage);
    }

    /**
     * 记录执行结束时间并计算实际耗时。
     *
     * <p>该方法只允许由成功和失败状态方法调用，
     * 避免外部代码只填写结束时间却没有设置明确结果状态。</p>
     */
    private void finishExecution() {
        // finishTime用于记录任务实际结束的日期时间。
        this.finishTime = LocalDateTime.now();

        // 使用单调时钟差值计算耗时，并转换成毫秒用于日志和接口响应。
        long costNanos = System.nanoTime() - startNanoTime;
        this.costMillis = Math.max(0L, TimeUnit.NANOSECONDS.toMillis(costNanos));
    }
}