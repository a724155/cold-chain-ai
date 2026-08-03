package com.ymm.coldchainai.agent.permission.domain.model;

import com.ymm.coldchainai.agent.permission.domain.enumtype.ToolPermissionDecisionReasonEnum;
import com.ymm.coldchainai.agent.permission.domain.enumtype.ToolPermissionScopeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * Tool权限判断结果。
 *
 * <p>该对象明确表达允许或者拒绝，并保留命中的权限范围和判断原因。</p>
 *
 * <p>权限规则不存在时permissionScope为空，因为本次判断没有命中任何授权规则。</p>
 *
 * <p>在挖矿流程中，该对象相当于设备门禁给出的检查回执：
 * 是否允许进入、依据哪种通行范围以及拒绝原因。</p>
 */
@Getter
@AllArgsConstructor
public class ToolPermissionDecision {

    /**
     * 是否允许执行当前Tool。
     */
    private final boolean allowed;

    /**
     * 本次命中的权限范围。
     *
     * <p>没有找到授权规则时该字段为空。</p>
     */
    private final ToolPermissionScopeEnum permissionScope;

    /**
     * 允许或者拒绝的内部判断原因。
     */
    private final ToolPermissionDecisionReasonEnum decisionReason;

    /**
     * 创建允许调用的权限判断结果。
     *
     * @param permissionScope 当前命中的权限范围
     * @return 允许调用结果
     */
    public static ToolPermissionDecision allow(ToolPermissionScopeEnum permissionScope) {
        if (Objects.isNull(permissionScope)) {
            throw new IllegalArgumentException("允许Tool调用时权限范围不能为空");
        }
        // 允许结果统一使用ALLOWED原因，避免调用方自行拼装互相矛盾的状态。
        return new ToolPermissionDecision(true, permissionScope, ToolPermissionDecisionReasonEnum.ALLOWED);
    }

    /**
     * 创建拒绝调用的权限判断结果。
     *
     * @param permissionScope 当前命中的权限范围，规则不存在时可以为空
     * @param decisionReason 拒绝原因
     * @return 拒绝调用结果
     */
    public static ToolPermissionDecision deny(ToolPermissionScopeEnum permissionScope, ToolPermissionDecisionReasonEnum decisionReason) {

        if (Objects.isNull(decisionReason)) {
            throw new IllegalArgumentException("拒绝Tool调用时判断原因不能为空");
        }

        if (Objects.equals(ToolPermissionDecisionReasonEnum.ALLOWED, decisionReason)) {
            throw new IllegalArgumentException("拒绝Tool调用时不能使用ALLOWED原因");
        }

        return new ToolPermissionDecision(false, permissionScope, decisionReason);
    }
}
