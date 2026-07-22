package com.ymm.coldchainai.agent.core.application.context;

import lombok.Getter;

import java.util.Objects;

/**
 * 一次Agent调用所使用的受信任业务上下文。
 *
 * <p>该对象由Application层根据认证上下文创建，不能由Controller请求对象或模型Tool参数直接构造。</p>
 *
 * <p>在挖矿流程中，该对象相当于附在作业任务单上的客户身份证明和公司许可证。
 * 智能挖掘机可以提出查询要求，但必须使用项目经理随任务单下发的真实身份进行作业。</p>
 */
@Getter
public class AgentInvocationContext {

    /**
     * 当前已认证用户ID。
     */
    private final Long currentUserId;

    /**
     * 当前已认证租户ID。
     */
    private final Long currentTenantId;

    /**
     * 创建Agent调用上下文。
     *
     * @param currentUserId 当前已认证用户ID
     * @param currentTenantId 当前已认证租户ID
     */
    private AgentInvocationContext(Long currentUserId, Long currentTenantId) {
        this.currentUserId = currentUserId;
        this.currentTenantId = currentTenantId;
    }

    /**
     * 创建经过基础校验的Agent调用上下文。
     *
     * @param currentUserId 当前已认证用户ID
     * @param currentTenantId 当前已认证租户ID
     * @return Agent调用上下文
     */
    public static AgentInvocationContext create(Long currentUserId, Long currentTenantId) {
        if (Objects.isNull(currentUserId) || currentUserId <= 0L) {
            throw new IllegalArgumentException("Agent调用上下文中的当前用户ID必须大于0");
        }

        if (Objects.isNull(currentTenantId) || currentTenantId <= 0L) {
            throw new IllegalArgumentException("Agent调用上下文中的当前租户ID必须大于0");
        }

        return new AgentInvocationContext(currentUserId, currentTenantId);
    }
}