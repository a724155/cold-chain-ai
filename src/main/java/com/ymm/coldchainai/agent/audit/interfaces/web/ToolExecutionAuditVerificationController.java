package com.ymm.coldchainai.agent.audit.interfaces.web;

import com.ymm.coldchainai.agent.audit.application.command.QueryToolExecutionAuditListCommand;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionRecordListDTO;
import com.ymm.coldchainai.agent.audit.application.service.IToolExecutionAuditQueryApplicationService;
import com.ymm.coldchainai.agent.audit.interfaces.web.request.QueryToolExecutionAuditListRequest;
import com.ymm.coldchainai.agent.audit.interfaces.web.response.ToolExecutionRecordListResponse;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.shared.response.YmmResult;
import com.ymm.coldchainai.shared.security.context.ICurrentUserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tool执行审计local验证接口。
 *
 * <p>该Controller用于验证Tool审计数据是否能够按照requestId、
 * currentUserId和currentTenantId安全查询。</p>
 *
 * <p>Controller不直接调用Mapper，也不允许Postman提交用户和租户身份。</p>
 *
 * <p>在挖矿流程中，该接口相当于研发环境中的设备审计档案窗口，
 * 研发人员提交项目任务编号后，窗口根据受信任身份返回本轮设备使用记录。</p>
 */
@RestController
@Profile("local")
@RequestMapping("/api/verification/agent/tool-audit")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ToolExecutionAuditVerificationController {

    /**
     * Tool执行审计查询Application Service。
     */
    private final IToolExecutionAuditQueryApplicationService toolExecutionAuditQueryApplicationService;

    /**
     * 当前受信任用户上下文。
     */
    private final ICurrentUserContext currentUserContext;

    /**
     * 根据requestId查询本轮全部Tool执行审计记录。
     *
     * @param request Tool审计列表查询请求
     * @return 按审计记录插入顺序排列的Tool执行列表
     */
    @GetMapping("/executions")
    public YmmResult<ToolExecutionRecordListResponse> listExecutions(@Valid @ModelAttribute QueryToolExecutionAuditListRequest request) {

        // 用户和租户身份从后端认证上下文读取，不能通过URL查询参数伪造。
        AgentInvocationContext invocationContext = AgentInvocationContext.create(
                currentUserContext.getCurrentUserId(), currentUserContext.getCurrentTenantId());

        // 将HTTP请求转换成Application查询命令，避免Request对象穿透到业务层。
        QueryToolExecutionAuditListCommand command = QueryToolExecutionAuditListCommand.create(request.getRequestId(), invocationContext);

        // Application Service执行数据权限过滤、Repository查询和DTO转换。
        ToolExecutionRecordListDTO recordListDTO = toolExecutionAuditQueryApplicationService.listByRequestId(command);

        // 将Application DTO转换成稳定HTTP响应结构。
        ToolExecutionRecordListResponse response = ToolExecutionRecordListResponse.fromDTO(recordListDTO);

        return YmmResult.success(response);
    }
}
