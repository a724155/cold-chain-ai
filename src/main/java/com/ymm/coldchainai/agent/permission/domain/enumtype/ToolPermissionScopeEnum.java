package com.ymm.coldchainai.agent.permission.domain.enumtype;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent Tool权限范围枚举。
 *
 * <p>权限范围决定一条Agent与Tool授权规则如何校验当前用户和租户。</p>
 *
 * <p>在挖矿流程中，该枚举相当于外协设备的通行证范围：
 * 有的设备允许所有合法矿工使用，有的只允许指定矿场、
 * 指定操作员，或者要求矿场和操作员同时满足条件。</p>
 */
@Getter
@AllArgsConstructor
public enum ToolPermissionScopeEnum {

    /**
     * 所有已经通过后端认证的用户均可调用。
     *
     * <p>该范围仍要求currentUserId和currentTenantId合法，
     * 并不代表匿名用户可以调用。</p>
     */
    AUTHENTICATED("AUTHENTICATED", "全部已认证用户"),

    /**
     * 只有租户ID存在于允许租户集合中时才可调用。
     */
    TENANT_ALLOWLIST("TENANT_ALLOWLIST", "租户白名单"),

    /**
     * 只有用户ID存在于允许用户集合中时才可调用。
     */
    USER_ALLOWLIST("USER_ALLOWLIST", "用户白名单"),

    /**
     * 当前租户和当前用户必须同时存在于各自白名单中。
     */
    TENANT_AND_USER_ALLOWLIST("TENANT_AND_USER_ALLOWLIST", "租户与用户双重白名单");

    /**
     * 权限范围稳定编码。
     *
     * <p>该编码可以用于日志、审计以及后续管理端接口展示。</p>
     */
    private final String code;

    /**
     * 权限范围中文说明。
     */
    private final String message;
}
