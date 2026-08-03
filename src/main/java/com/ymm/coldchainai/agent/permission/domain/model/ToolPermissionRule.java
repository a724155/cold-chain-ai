package com.ymm.coldchainai.agent.permission.domain.model;

import com.ymm.coldchainai.agent.permission.domain.enumtype.ToolPermissionDecisionReasonEnum;
import com.ymm.coldchainai.agent.permission.domain.enumtype.ToolPermissionScopeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Agent与Tool授权规则领域模型。
 *
 * <p>一条规则唯一描述某个Agent是否允许在指定用户和租户范围内调用某个Tool。</p>
 *
 * <p>规则采用默认拒绝思想：</p>
 *
 * <p>1. 规则不存在时拒绝；</p>
 * <p>2. 规则被禁用时拒绝；</p>
 * <p>3. 白名单不匹配时拒绝；</p>
 * <p>4. 只有全部要求满足时才允许。</p>
 *
 * <p>在挖矿流程中，该规则相当于一份“调度中心—设备”授权文件，
 * 同时规定允许哪些矿场或者操作员使用该设备。</p>
 */
@Getter
@AllArgsConstructor
public class ToolPermissionRule {

    /**
     * 被授权的Agent稳定编码。
     */
    private final String agentCode;

    /**
     * 被授权调用的Tool稳定名称。
     */
    private final String toolName;

    /**
     * 当前规则的权限校验范围。
     */
    private final ToolPermissionScopeEnum permissionScope;

    /**
     * 当前规则是否启用。
     *
     * <p>禁用规则仍然保留在配置中，但权限判断结果为拒绝。</p>
     */
    private final boolean enabled;

    /**
     * 允许调用Tool的租户ID集合。
     *
     * <p>只有TENANT_ALLOWLIST和TENANT_AND_USER_ALLOWLIST会使用该集合。</p>
     */
    private final Set<Long> allowedTenantIdSet;

    /**
     * 允许调用Tool的用户ID集合。
     *
     * <p>只有USER_ALLOWLIST和TENANT_AND_USER_ALLOWLIST会使用该集合。</p>
     */
    private final Set<Long> allowedUserIdSet;

    /**
     * 创建并校验一条Agent与Tool授权规则。
     *
     * @param agentCode Agent稳定编码
     * @param toolName Tool稳定名称
     * @param permissionScope 权限范围
     * @param enabled 规则是否启用
     * @param allowedTenantIdList 允许租户ID列表
     * @param allowedUserIdList 允许用户ID列表
     * @return 不可变Tool授权规则
     */
    public static ToolPermissionRule create(String agentCode, String toolName, ToolPermissionScopeEnum permissionScope,
                                            Boolean enabled, List<Long> allowedTenantIdList, List<Long> allowedUserIdList) {

        if (StringUtils.isBlank(agentCode)) {
            throw new IllegalArgumentException("Tool权限规则Agent编码不能为空");
        }

        if (StringUtils.isBlank(toolName)) {
            throw new IllegalArgumentException("Tool权限规则Tool名称不能为空");
        }

        if (Objects.isNull(permissionScope)) {
            throw new IllegalArgumentException("Tool权限规则权限范围不能为空");
        }

        if (Objects.isNull(enabled)) {
            throw new IllegalArgumentException("Tool权限规则enabled不能为空");
        }

        // 将配置中的租户List转换成经过正数、空元素和重复值校验的不可修改Set。
        Set<Long> allowedTenantIdSet = convertIdListToSet(allowedTenantIdList, "允许租户ID");

        // 将配置中的用户List转换成经过正数、空元素和重复值校验的不可修改Set。
        Set<Long> allowedUserIdSet = convertIdListToSet(allowedUserIdList, "允许用户ID");

        // 校验当前权限范围与两个白名单集合的组合是否一致。
        validateScopeConfiguration(permissionScope, allowedTenantIdSet, allowedUserIdSet);

        return new ToolPermissionRule(
                StringUtils.trim(agentCode),
                StringUtils.trim(toolName),
                permissionScope,
                Boolean.TRUE.equals(enabled),
                allowedTenantIdSet,
                allowedUserIdSet);
    }

    /**
     * 判断当前用户和租户是否满足本授权规则。
     *
     * @param permissionSubject Tool权限判断主体
     * @return 允许或者拒绝结果
     */
    public ToolPermissionDecision evaluate(ToolPermissionSubject permissionSubject) {

        if (Objects.isNull(permissionSubject)) {
            throw new IllegalArgumentException("Tool权限判断主体不能为空");
        }

        // 当前规则必须与判断主体中的Agent和Tool完全匹配。Policy正常情况下会先按二者找到规则，这里再次校验是为了保护领域对象边界。
        if (!StringUtils.equals(agentCode, permissionSubject.getAgentCode()) || !StringUtils.equals(toolName, permissionSubject.getToolName())) {
            throw new IllegalArgumentException("Tool权限规则与判断主体不匹配，ruleAgentCode=%s，subjectAgentCode=%s，ruleToolName=%s，subjectToolName=%s"
                            .formatted(agentCode, permissionSubject.getAgentCode(), toolName, permissionSubject.getToolName()));
        }
        // 被禁用的规则不能继续执行白名单判断，直接返回明确拒绝原因。
        if (!enabled) {
            return ToolPermissionDecision.deny(permissionScope, ToolPermissionDecisionReasonEnum.RULE_DISABLED);
        }

        // JDK 14正式提供switch表达式，可以直接根据权限范围返回判断结果。箭头case不会发生传统switch忘写break造成的分支穿透。
        return switch (permissionScope) {
            case AUTHENTICATED -> ToolPermissionDecision.allow(permissionScope);

            case TENANT_ALLOWLIST -> allowedTenantIdSet.contains(permissionSubject.getCurrentTenantId()) ?
                    ToolPermissionDecision.allow(permissionScope) :
                    ToolPermissionDecision.deny(permissionScope, ToolPermissionDecisionReasonEnum.TENANT_NOT_ALLOWED);

            case USER_ALLOWLIST -> allowedUserIdSet.contains(permissionSubject.getCurrentUserId()) ?
                    ToolPermissionDecision.allow(permissionScope) :
                    ToolPermissionDecision.deny(permissionScope, ToolPermissionDecisionReasonEnum.USER_NOT_ALLOWED);

            case TENANT_AND_USER_ALLOWLIST -> evaluateTenantAndUserAllowlist(permissionSubject);
        };
    }

    /**
     * 校验租户和用户双重白名单。
     *
     * <p>先校验租户，再校验用户。
     * 两项全部满足后才允许调用Tool。</p>
     *
     * @param permissionSubject Tool权限判断主体
     * @return 双重白名单判断结果
     */
    private ToolPermissionDecision evaluateTenantAndUserAllowlist(ToolPermissionSubject permissionSubject) {

        // 当前租户未命中白名单时，无需继续执行用户白名单判断。
        if (!allowedTenantIdSet.contains(permissionSubject.getCurrentTenantId())) {
            return ToolPermissionDecision.deny(permissionScope, ToolPermissionDecisionReasonEnum.TENANT_NOT_ALLOWED);
        }
        // 租户合法但用户未命中白名单时，仍然拒绝执行Tool。
        if (!allowedUserIdSet.contains(permissionSubject.getCurrentUserId())) {
            return ToolPermissionDecision.deny(permissionScope, ToolPermissionDecisionReasonEnum.USER_NOT_ALLOWED);
        }

        return ToolPermissionDecision.allow(permissionScope);
    }

    /**
     * 将配置ID列表转换成不可修改Set。
     *
     * <p>Set可以在权限判断时提供接近O(1)的contains查询，
     * 比每次在List中顺序扫描更适合白名单匹配。</p>
     *
     * @param idList 配置ID列表
     * @param fieldName 异常提示字段名称
     * @return 已完成校验的不可修改ID集合
     */
    private static Set<Long> convertIdListToSet(List<Long> idList, String fieldName) {

        // 配置未提供白名单时按空列表处理，后续由权限范围校验是否允许为空。
        List<Long> safeIdList = ListUtils.emptyIfNull(idList);

        // LinkedHashSet既能去重，也能保留配置顺序，方便异常排查。
        Set<Long> idSet = new LinkedHashSet<>();

        // 一次遍历完成空元素、正数和重复ID校验。
        for (Long id : safeIdList) {
            if (Objects.isNull(id) || id <= 0L) {
                throw new IllegalArgumentException("%s必须全部大于0".formatted(fieldName));
            }
            // add()返回false表示该ID此前已经出现，重复配置应在项目启动时明确失败。
            if (!idSet.add(id)) {
                throw new IllegalArgumentException("%s不能包含重复值，id=%s".formatted(fieldName, id));
            }
        }

        // Set.copyOf()是JDK 10新增API，会创建不可修改Set副本。权限规则初始化完成后不允许运行期间被其他代码增删白名单。
        return Set.copyOf(idSet);
    }

    /**
     * 校验权限范围与白名单配置是否匹配。
     *
     * @param permissionScope 权限范围
     * @param allowedTenantIdSet 允许租户集合
     * @param allowedUserIdSet 允许用户集合
     */
    private static void validateScopeConfiguration(ToolPermissionScopeEnum permissionScope,
                                                   Set<Long> allowedTenantIdSet, Set<Long> allowedUserIdSet) {

        /*
         * 这里使用JDK 14增强switch箭头语法。每个分支执行后自动结束，不需要手写break。
         */
        switch (permissionScope) {
            case AUTHENTICATED -> {
                // 全部认证用户范围不应再配置白名单，避免配置人员误以为白名单会生效。
                if (CollectionUtils.isNotEmpty(allowedTenantIdSet) || CollectionUtils.isNotEmpty(allowedUserIdSet)) {
                    throw new IllegalArgumentException("AUTHENTICATED权限范围不能配置租户或用户白名单");
                }
            }
            case TENANT_ALLOWLIST -> {
                if (CollectionUtils.isEmpty(allowedTenantIdSet)) {
                    throw new IllegalArgumentException("TENANT_ALLOWLIST权限范围必须配置允许租户ID");
                }
                if (CollectionUtils.isNotEmpty(allowedUserIdSet)) {
                    throw new IllegalArgumentException("TENANT_ALLOWLIST权限范围不能配置用户白名单");
                }
            }
            case USER_ALLOWLIST -> {
                if (CollectionUtils.isEmpty(allowedUserIdSet)) {
                    throw new IllegalArgumentException("USER_ALLOWLIST权限范围必须配置允许用户ID");
                }
                if (CollectionUtils.isNotEmpty(allowedTenantIdSet)) {
                    throw new IllegalArgumentException("USER_ALLOWLIST权限范围不能配置租户白名单");
                }
            }
            case TENANT_AND_USER_ALLOWLIST -> {
                if (CollectionUtils.isEmpty(allowedTenantIdSet) || CollectionUtils.isEmpty(allowedUserIdSet)) {
                    throw new IllegalArgumentException("TENANT_AND_USER_ALLOWLIST必须同时配置租户和用户白名单");
                }
            }
        }
    }
}