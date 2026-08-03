package com.ymm.coldchainai.agent.permission.infrastructure.config;

import com.ymm.coldchainai.agent.permission.domain.enumtype.ToolPermissionScopeEnum;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Agent Tool权限配置。
 *
 * <p>该配置负责把application.yml中的Agent与Tool授权矩阵绑定成Java对象。</p>
 *
 * <p>配置对象只负责承接外部配置；
 * 重复规则、白名单组合和权限范围一致性由ToolPermissionRule领域模型统一校验。</p>
 *
 * <p>在挖矿流程中，该配置相当于门禁系统读取的设备授权清单，
 * ConfiguredToolPermissionPolicy会把清单转换成运行期不可修改的权限索引。</p>
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "cold-chain-ai.agent.tool-permission")
public class AgentToolPermissionProperties {

    /**
     * Agent与Tool授权规则列表。
     *
     * <p>系统采用默认拒绝，因此至少需要配置一条规则。</p>
     */
    @Valid
    @NotNull(message = "Tool权限规则列表不能为空")
    @Size(min = 1, message = "Tool权限规则至少配置一条")
    private List<RuleProperties> ruleList = List.of();

    /**
     * 单条Agent与Tool授权规则配置。
     */
    @Data
    public static class RuleProperties {

        /**
         * 被授权的Agent稳定编码。
         */
        @NotBlank(message = "Tool权限规则agentCode不能为空")
        private String agentCode;

        /**
         * 被授权调用的Tool稳定名称。
         */
        @NotBlank(message = "Tool权限规则toolName不能为空")
        private String toolName;

        /**
         * 当前规则使用的权限范围。
         */
        @NotNull(message = "Tool权限规则permissionScope不能为空")
        private ToolPermissionScopeEnum permissionScope;

        /**
         * 当前授权规则是否启用。
         *
         * <p>默认启用；设置为false后，即使白名单匹配也会拒绝调用。</p>
         */
        @NotNull(message = "Tool权限规则enabled不能为空")
        private Boolean enabled = Boolean.TRUE;

        /**
         * 允许调用Tool的租户ID列表。
         *
         * <p>仅租户白名单相关权限范围需要配置。</p>
         */
        private List<Long> allowedTenantIdList = List.of();

        /**
         * 允许调用Tool的用户ID列表。
         *
         * <p>仅用户白名单相关权限范围需要配置。</p>
         */
        private List<Long> allowedUserIdList = List.of();
    }
}