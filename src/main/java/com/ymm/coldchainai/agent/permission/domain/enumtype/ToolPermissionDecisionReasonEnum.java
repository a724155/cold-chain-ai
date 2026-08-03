package com.ymm.coldchainai.agent.permission.domain.enumtype;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tool权限判断原因枚举。
 *
 * <p>该枚举描述权限判断为什么允许或者拒绝，
 * 便于后续日志、拒绝审计以及问题排查。</p>
 *
 * <p>对最终用户返回时不一定直接暴露详细原因。
 * 例如“规则不存在”和“用户不在白名单”可以统一返回无权限，
 * 避免泄露内部授权配置。</p>
 */
@Getter
@AllArgsConstructor
public enum ToolPermissionDecisionReasonEnum {

    /**
     * 当前Agent、Tool、用户和租户满足授权规则。
     */
    ALLOWED("ALLOWED", "允许调用"),

    /**
     * 没有配置当前Agent与Tool之间的授权规则。
     */
    RULE_NOT_FOUND("RULE_NOT_FOUND", "未配置Agent与Tool授权规则"),

    /**
     * 当前授权规则已经被关闭。
     */
    RULE_DISABLED("RULE_DISABLED", "Agent与Tool授权规则已禁用"),

    /**
     * 当前租户不在授权规则允许范围内。
     */
    TENANT_NOT_ALLOWED("TENANT_NOT_ALLOWED", "当前租户无权调用该Tool"),

    /**
     * 当前用户不在授权规则允许范围内。
     */
    USER_NOT_ALLOWED("USER_NOT_ALLOWED", "当前用户无权调用该Tool");

    /**
     * 权限判断原因稳定编码。
     */
    private final String code;

    /**
     * 权限判断原因中文说明。
     */
    private final String message;
}
