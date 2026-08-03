package com.ymm.coldchainai.agent.audit.application.service.impl;

import com.ymm.coldchainai.agent.audit.application.command.QueryToolExecutionAuditDetailCommand;
import com.ymm.coldchainai.agent.audit.application.command.QueryToolExecutionAuditListCommand;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionRecordDTO;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionRecordListDTO;
import com.ymm.coldchainai.agent.audit.application.enumtype.ToolAuditErrorCodeEnum;
import com.ymm.coldchainai.agent.audit.application.service.IToolExecutionAuditQueryApplicationService;
import com.ymm.coldchainai.agent.audit.domain.model.ToolExecution;
import com.ymm.coldchainai.agent.audit.domain.repository.IToolExecutionRepository;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Tool执行审计查询Application Service实现。
 *
 * <p>该类通过requestId、currentUserId和currentTenantId完成数据权限查询，
 * 并将领域对象转换成只包含安全摘要的Application DTO。</p>
 *
 * <p>当前方法使用只读事务，不执行FOR UPDATE。
 * 查询审计记录不会修改状态，也不需要阻塞正在执行的Tool终态更新。</p>
 *
 * <p>在挖矿流程中，该服务相当于审计档案调阅员：根据项目编号和客户身份取出本轮使用过的全部外协设备记录。</p>
 */
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ToolExecutionAuditQueryApplicationServiceImpl implements IToolExecutionAuditQueryApplicationService {

    /**
     * Tool执行审计Repository。
     */
    private final IToolExecutionRepository toolExecutionRepository;

    /**
     * 根据requestId查询Tool执行审计列表。
     *
     * @param command Tool审计列表查询命令
     * @return 当前用户和租户有权读取的Tool执行记录
     */
    @Override
    @Transactional(readOnly = true)
    public ToolExecutionRecordListDTO listByRequestId(QueryToolExecutionAuditListCommand command) {

        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Tool审计列表查询命令不能为空");
        }

        // 从Command中读取后端认证链路创建的受信任用户和租户上下文。
        AgentInvocationContext invocationContext = command.getAgentInvocationContext();

        if (Objects.isNull(invocationContext)) {
            throw new IllegalArgumentException("Tool审计查询调用上下文不能为空");
        }

        /*
         * Repository查询同时包含requestId、currentUserId和currentTenantId。
         * 查询不到时返回空列表，不区分“没有调用Tool”“requestId不存在”和“无权访问”，避免泄露其他用户的Agent请求是否存在。
         */
        List<ToolExecution> toolExecutionList = toolExecutionRepository.listByRequestIdAndOwner(
                        command.getRequestId(), invocationContext.getCurrentUserId(), invocationContext.getCurrentTenantId());

        // 将领域记录转换成Application DTO，列表为空时正常返回returnedExecutionCount=0。
        return ToolExecutionRecordListDTO.fromDomainList(command.getRequestId(), toolExecutionList);
    }

    /**
     * 根据toolExecutionId查询单次Tool执行审计详情。
     *
     * <p>Repository查询条件同时包含toolExecutionId、用户ID和租户ID。
     * 查询结果为空时，Application层不会继续区分：</p>
     *
     * <p>1. toolExecutionId确实不存在；</p>
     * <p>2. toolExecutionId存在，但属于其他用户；</p>
     * <p>3. toolExecutionId存在，但属于其他租户。</p>
     *
     * <p>三种情况统一返回“记录不存在或无权访问”，
     * 避免调用者枚举toolExecutionId探测其他用户的数据。</p>
     *
     * @param command Tool审计详情查询命令
     * @return 单次Tool执行审计DTO
     */
    @Override
    @Transactional(readOnly = true)
    public ToolExecutionRecordDTO getByToolExecutionId(QueryToolExecutionAuditDetailCommand command) {

        if (Objects.isNull(command)) {
            throw new IllegalArgumentException("Tool审计详情查询命令不能为空");
        }

        // 获取由后端认证链路创建的受信任用户和租户上下文。
        AgentInvocationContext invocationContext = command.getAgentInvocationContext();

        if (Objects.isNull(invocationContext)) {
            throw new IllegalArgumentException("Tool审计详情调用上下文不能为空");
        }

        // Repository不会区分记录不存在和无权访问，只返回当前用户和租户是否能够查询到对应领域对象。
        Optional<ToolExecution> toolExecutionOptional = toolExecutionRepository.findByToolExecutionIdAndOwner(
                command.getToolExecutionId(), invocationContext.getCurrentUserId(), invocationContext.getCurrentTenantId());

        // Optional.orElseThrow()从JDK 8开始提供。查询为空时延迟创建业务异常，查询成功时不会执行异常Supplier。
        ToolExecution toolExecution = toolExecutionOptional.orElseThrow(
                () -> new BusinessException(ToolAuditErrorCodeEnum.TOOL_AUDIT_RECORD_NOT_FOUND_OR_FORBIDDEN,
                        ToolAuditErrorCodeEnum.TOOL_AUDIT_RECORD_NOT_FOUND_OR_FORBIDDEN.getMessage()));

        // 将已经通过领域状态一致性校验的ToolExecution转换成安全查询DTO。
        return ToolExecutionRecordDTO.fromDomain(toolExecution);
    }
}
