package com.ymm.coldchainai.agent.audit.application.service.impl;

import com.ymm.coldchainai.agent.audit.application.command.StartToolExecutionAuditCommand;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionAuditDTO;
import com.ymm.coldchainai.agent.audit.application.service.IToolExecutionAuditApplicationService;
import com.ymm.coldchainai.agent.audit.domain.model.ToolExecution;
import com.ymm.coldchainai.agent.audit.domain.repository.IToolExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Agent Tool执行审计Application Service实现。
 *
 * <p>三个公开方法都使用REQUIRES_NEW开启独立事务，
 * 确保RUNNING、SUCCEEDED和FAILED分别立即提交，不依赖外层Tool或者Agent事务。</p>
 *
 * <p>{@link Propagation#REQUIRES_NEW}是Spring事务传播机制：</p>
 *
 * <p>1. 当前线程没有事务时，直接创建新事务；</p>
 * <p>2. 当前线程已经存在事务时，暂时挂起原事务，再创建独立新事务；</p>
 * <p>3. 当前方法结束后提交或回滚新事务，再恢复原事务。</p>
 *
 * <p>这不是为了把Tool业务包进事务，恰恰是为了让每一次审计写入快速、独立地提交。</p>
 *
 * <p>在挖矿流程中，该服务相当于设备审计登记员：
 * 每次只短暂打开账本登记一个状态，登记完成立即归还账本，
 * 不会拿着账本等待设备完成整个作业。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ToolExecutionAuditApplicationServiceImpl implements IToolExecutionAuditApplicationService {

    /**
     * Tool执行业务标识前缀。
     */
    private static final String TOOL_EXECUTION_ID_PREFIX = "tool_exec_";

    /**
     * Tool执行审计Repository。
     */
    private final IToolExecutionRepository toolExecutionRepository;

    /**
     * 开始记录一次Tool执行审计。
     *
     * <p>该方法使用REQUIRES_NEW独立事务，而不是加入Agent主执行事务。</p>
     *
     * <p>原因：
     * ToolExecution属于系统审计数据，不属于Agent业务结果。
     * 即使后续模型调用失败、Agent回答生成失败或者外层业务事务回滚，
     * 我们仍然需要保留“曾经调用过哪个Tool、什么时候开始执行、执行身份是谁”的记录，
     * 方便线上问题定位、调用链追踪和失败原因分析。</p>
     *
     * <p>如果使用默认Propagation.REQUIRED：
     * 当前方法会加入外层Agent事务。
     * 当Agent主流程最终异常回滚时，Tool开始记录也会一起回滚，
     * 导致线上出现“明明调用过Tool，但是数据库没有任何审计记录”的问题。</p>
     *
     * <p>这里的事务边界只保护RUNNING状态插入：
     * 开启事务 → 写入Tool执行记录 → 提交事务。
     * 后续真正的RPC调用、模型调用不放在该事务中，
     * 避免长事务占用数据库连接。</p>
     *
     * <p>与订单@Transactional设计区别：
     * 订单事务保护的是业务一致性，例如订单、支付单、库存必须同时成功或失败；
     * Tool审计事务保护的是系统可观测性，即业务失败后仍然需要留下失败现场。</p>
     *
     * 用REQUIRES_NEW相当于自己的事务不被别人的事务影响，不管你们事务回不回滚，我这边都有自己的事务记录
     *
     * @param command Tool执行开始命令
     * @return Tool执行审计凭证
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public ToolExecutionAuditDTO startExecution(StartToolExecutionAuditCommand command) {
        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Tool审计开始命令不能为空");
        }

        // 为每一次真实Tool调用生成独立业务标识，一次Agent请求可以对应多个该标识。
        String toolExecutionId = generateToolExecutionId();

        // 创建RUNNING领域任务单，领域对象统一生成开始时间并校验审计身份字段。
        ToolExecution toolExecution = ToolExecution.start(
                toolExecutionId,
                command.getRequestId(),
                command.getAgentCode(),
                command.getToolName(),
                command.getCurrentUserId(),
                command.getCurrentTenantId(),
                command.getInputSummary());

        /*
         * 在当前REQUIRES_NEW短事务中插入RUNNING记录。
         * 此时数据库提交后，即使外层Agent事务因为模型异常或者业务异常回滚，该Tool执行开始记录仍然存在，可以用于后续updateToSucceeded/updateToFailed更新最终结果。
         */
        toolExecutionRepository.saveRunning(toolExecution);

        log.info(
                "Tool审计开始，toolExecutionId={}，requestId={}，agentCode={}，toolName={}，currentUserId={}，currentTenantId={}",
                toolExecution.getToolExecutionId(),
                toolExecution.getRequestId(),
                toolExecution.getAgentCode(),
                toolExecution.getToolName(),
                toolExecution.getCurrentUserId(),
                toolExecution.getCurrentTenantId());

        // 返回只包含恢复RUNNING任务所需字段的Application凭证。
        return ToolExecutionAuditDTO.fromDomain(toolExecution);
    }

    /**
     * 在独立短事务中将Tool审计更新为SUCCEEDED。
     *
     * @param auditDTO RUNNING Tool审计凭证
     * @param outputSummary Tool输出安全摘要
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSucceeded(ToolExecutionAuditDTO auditDTO, String outputSummary) {
        // 根据开始阶段返回的凭证恢复原RUNNING领域任务。
        ToolExecution toolExecution = restoreRunningExecution(auditDTO);

        // 领域对象记录完成时间、计算耗时并推进为SUCCEEDED。
        toolExecution.succeed(outputSummary);

        // 使用带RUNNING状态条件的单条UPDATE原子写入成功终态。
        toolExecutionRepository.updateToSucceeded(toolExecution);

        log.info("Tool审计成功，toolExecutionId={}，requestId={}，toolName={}，costMillis={}",
                toolExecution.getToolExecutionId(),toolExecution.getRequestId(),
                toolExecution.getToolName(),toolExecution.getCostMillis());
    }

    /**
     * 在独立短事务中将Tool审计更新为FAILED。
     *
     * @param auditDTO RUNNING Tool审计凭证
     * @param errorCode Tool执行失败错误码
     * @param errorMessage Tool执行失败安全错误信息
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(ToolExecutionAuditDTO auditDTO, Integer errorCode, String errorMessage) {
        // 根据开始阶段返回的凭证恢复原RUNNING领域任务。
        ToolExecution toolExecution = restoreRunningExecution(auditDTO);

        // 领域对象清空成功摘要，保存安全错误信息并推进为FAILED。
        toolExecution.fail(errorCode, errorMessage);

        // 使用带RUNNING状态条件的单条UPDATE原子写入失败终态。
        toolExecutionRepository.updateToFailed(toolExecution);

        log.warn("Tool审计失败，toolExecutionId={}，requestId={}，toolName={}，errorCode={}，costMillis={}",
                toolExecution.getToolExecutionId(),toolExecution.getRequestId(),toolExecution.getToolName(),
                toolExecution.getErrorCode(),toolExecution.getCostMillis());
    }

    /**
     * 根据Application审计凭证恢复RUNNING Tool执行领域对象。
     *
     * @param auditDTO Tool审计开始阶段返回的执行凭证
     * @return 可继续推进到成功或失败的RUNNING领域对象
     */
    private ToolExecution restoreRunningExecution(ToolExecutionAuditDTO auditDTO) {
        if (Objects.isNull(auditDTO)) {
            throw new IllegalArgumentException("Tool审计执行凭证不能为空");
        }

        /*
         * 继续使用原始startTime，不能在终态事务中重新生成开始时间。
         * 否则costMillis只会记录数据库更新耗时，而不是真实Tool业务耗时。
         */
        return ToolExecution.restoreRunning(
                auditDTO.getToolExecutionId(),
                auditDTO.getRequestId(),
                auditDTO.getAgentCode(),
                auditDTO.getToolName(),
                auditDTO.getCurrentUserId(),
                auditDTO.getCurrentTenantId(),
                auditDTO.getInputSummary(),
                auditDTO.getStartTime());
    }

    /**
     * 生成Tool执行业务唯一标识。
     *
     * @return 带统一前缀且不包含横线的Tool执行标识
     */
    private String generateToolExecutionId() {
        // UUID去掉横线后固定为32个字符，加上前缀后仍小于数据库VARCHAR(64)限制。
        return TOOL_EXECUTION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
}
