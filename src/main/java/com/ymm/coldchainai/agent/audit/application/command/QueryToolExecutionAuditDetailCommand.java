package com.ymm.coldchainai.agent.audit.application.command;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 根据toolExecutionId查询单次Tool执行审计详情的Application命令。
 *
 * <p>toolExecutionId来自接口请求参数，
 * currentUserId和currentTenantId必须来自后端认证链路创建的受信任上下文。</p>
 *
 * <p>前端不能提交用户ID或者租户ID决定查询范围，
 * 否则会产生水平越权读取其他用户Tool审计记录的风险。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 开发正式审计管理页面前，应与产品和前端明确：
 * RUNNING状态刷新方式、失败信息展示权限、时间格式、耗时单位，
 * 以及记录不存在与无权访问是否统一展示。</p>
 *
 * <p>在挖矿流程中，该命令相当于携带设备作业单号和客户身份，
 * 申请调阅一张具体的设备执行档案。</p>
 */
@Getter
@AllArgsConstructor
public class QueryToolExecutionAuditDetailCommand {

    /**
     * 需要查询的Tool执行业务唯一标识。
     */
    private final String toolExecutionId;

    /**
     * 后端认证链路创建的受信任用户和租户上下文。
     */
    private final AgentInvocationContext agentInvocationContext;

    /**
     * 创建Tool执行审计详情查询命令。
     *
     * @param toolExecutionId Tool执行业务唯一标识
     * @param agentInvocationContext 受信任用户和租户上下文
     * @return 已完成基础校验的详情查询命令
     */
    public static QueryToolExecutionAuditDetailCommand create(String toolExecutionId, AgentInvocationContext agentInvocationContext) {

        if (StringUtils.isBlank(toolExecutionId)) {
            throw new IllegalArgumentException("Tool审计详情toolExecutionId不能为空");
        }

        if (Objects.isNull(agentInvocationContext)) {
            throw new IllegalArgumentException("Tool审计详情调用上下文不能为空");
        }

        if (Objects.isNull(agentInvocationContext.getCurrentUserId()) || agentInvocationContext.getCurrentUserId() <= 0L) {
            throw new IllegalArgumentException("当前用户ID必须大于0");
        }

        if (Objects.isNull(agentInvocationContext.getCurrentTenantId()) || agentInvocationContext.getCurrentTenantId() <= 0L) {
            throw new IllegalArgumentException("当前租户ID必须大于0");
        }

        // 进入Application层前统一去除Tool执行标识两端空白字符。
        return new QueryToolExecutionAuditDetailCommand(StringUtils.trim(toolExecutionId), agentInvocationContext);
    }
}
