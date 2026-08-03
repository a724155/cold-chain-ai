package com.ymm.coldchainai.agent.audit.application.command;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 根据requestId查询Tool执行审计列表的Application命令。
 *
 * <p>requestId来自接口查询参数，用户和租户身份来自后端认证上下文。
 * Application层禁止接受前端直接提交currentUserId或者currentTenantId。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 开发前应与产品和前端明确无Tool调用时返回空列表，
 * RUNNING状态的展示方式、错误信息展示范围以及审计数据保留期限。</p>
 *
 * <p>在挖矿流程中，该命令相当于申请调阅某一次项目任务下的全部外协设备作业记录。</p>
 */
@Getter
@AllArgsConstructor
public class QueryToolExecutionAuditListCommand {

    /**
     * 需要查询的Agent请求唯一标识。
     */
    private final String requestId;

    /**
     * 受信任用户和租户调用上下文。
     */
    private final AgentInvocationContext agentInvocationContext;

    /**
     * 创建Tool审计列表查询命令。
     *
     * @param requestId Agent请求唯一标识
     * @param agentInvocationContext 受信任用户和租户上下文
     * @return 已完成基础参数校验的查询命令
     */
    public static QueryToolExecutionAuditListCommand create(String requestId, AgentInvocationContext agentInvocationContext) {

        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("Tool审计查询requestId不能为空");
        }

        if (Objects.isNull(agentInvocationContext)) {
            throw new IllegalArgumentException("Tool审计查询调用上下文不能为空");
        }

        if (Objects.isNull(agentInvocationContext.getCurrentUserId()) || agentInvocationContext.getCurrentUserId() <= 0L) {
            throw new IllegalArgumentException("当前用户ID必须大于0");
        }

        if (Objects.isNull(agentInvocationContext.getCurrentTenantId()) || agentInvocationContext.getCurrentTenantId() <= 0L) {
            throw new IllegalArgumentException("当前租户ID必须大于0");
        }

        return new QueryToolExecutionAuditListCommand(StringUtils.trim(requestId), agentInvocationContext);
    }
}
