package com.ymm.coldchainai.agent.permission.infrastructure.policy;

import com.ymm.coldchainai.agent.permission.domain.enumtype.ToolPermissionDecisionReasonEnum;
import com.ymm.coldchainai.agent.permission.domain.model.ToolPermissionDecision;
import com.ymm.coldchainai.agent.permission.domain.model.ToolPermissionRule;
import com.ymm.coldchainai.agent.permission.domain.model.ToolPermissionSubject;
import com.ymm.coldchainai.agent.permission.domain.service.IToolPermissionPolicy;
import com.ymm.coldchainai.agent.permission.infrastructure.config.AgentToolPermissionProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于配置文件的Agent Tool权限策略实现。
 *
 * <p>项目启动时，该组件完成以下工作：</p>
 *
 * <p>1. 读取全部权限配置；</p>
 * <p>2. 转换成ToolPermissionRule领域模型；</p>
 * <p>3. 校验重复Agent与Tool授权；</p>
 * <p>4. 构建不可修改权限索引。</p>
 *
 * <p>运行期间只需要根据agentCode和toolName执行Map查询，
 * 不需要每次重新遍历全部配置。</p>
 *
 * <p>这里必须手写构造方法，因为构造阶段存在配置转换、
 * 重复规则校验和不可修改Map构建逻辑，不能只依赖Lombok生成简单构造器。</p>
 *
 * <p>在挖矿流程中，该组件相当于设备门禁启动时，
 * 把纸质授权清单整理成可以快速查询的电子通行索引。</p>
 */
@Slf4j
@Component
public class ConfiguredToolPermissionPolicy implements IToolPermissionPolicy {

    /**
     * Agent与Tool授权规则索引。
     *
     * <p>Key由agentCode和toolName共同组成，
     * Value是已经完成完整领域校验的授权规则。</p>
     */
    private final Map<ToolPermissionRuleKey, ToolPermissionRule> permissionRuleMap;

    /**
     * 根据Tool权限配置初始化不可修改授权规则索引。
     *
     * @param agentToolPermissionProperties Tool权限配置
     */
    @Autowired
    public ConfiguredToolPermissionPolicy(AgentToolPermissionProperties agentToolPermissionProperties) {

        if (Objects.isNull(agentToolPermissionProperties)) {
            throw new IllegalArgumentException("Agent Tool权限配置不能为空");
        }
        // 对配置List进行空安全处理，空列表会在下面被明确阻断。
        List<AgentToolPermissionProperties.RuleProperties> rulePropertiesList =
                ListUtils.emptyIfNull(agentToolPermissionProperties.getRuleList());

        if (rulePropertiesList.isEmpty()) {
            throw new IllegalStateException("Agent Tool权限规则至少配置一条");
        }
        // LinkedHashMap保留配置顺序，出现重复规则时日志和异常定位更直观。
        Map<ToolPermissionRuleKey, ToolPermissionRule> mutablePermissionRuleMap = new LinkedHashMap<>();

        // 一次遍历完成配置元素判空、领域模型创建和重复规则校验。
        for (AgentToolPermissionProperties.RuleProperties ruleProperties : rulePropertiesList) {
            if (Objects.isNull(ruleProperties)) {
                throw new IllegalStateException("Agent Tool权限规则列表不能包含空元素");
            }
            // 将可变配置对象转换成运行期不可变领域规则。
            ToolPermissionRule permissionRule = ToolPermissionRule.create(
                    ruleProperties.getAgentCode(),
                    ruleProperties.getToolName(),
                    ruleProperties.getPermissionScope(),
                    ruleProperties.getEnabled(),
                    ruleProperties.getAllowedTenantIdList(),
                    ruleProperties.getAllowedUserIdList());
            // Agent编码与Tool名称共同组成一条授权规则的业务唯一键。
            ToolPermissionRuleKey permissionRuleKey = new ToolPermissionRuleKey(permissionRule.getAgentCode(), permissionRule.getToolName());
            // putIfAbsent()从JDK 8开始提供。Key已存在时不会覆盖旧规则，而是返回原规则，便于明确阻断重复授权配置。
            ToolPermissionRule existingPermissionRule = mutablePermissionRuleMap.putIfAbsent(permissionRuleKey, permissionRule);
            if (Objects.nonNull(existingPermissionRule)) {
                throw new IllegalStateException("Agent与Tool权限规则不能重复，agentCode=%s，toolName=%s"
                        .formatted(permissionRule.getAgentCode(), permissionRule.getToolName()));
            }
        }
        // Map.copyOf()是JDK 10新增API，会创建不可修改Map。权限索引在项目启动后不能被普通业务代码动态增删，避免运行期授权被意外篡改。
        this.permissionRuleMap = Map.copyOf(mutablePermissionRuleMap);
        log.info("Agent Tool权限规则初始化完成，ruleCount={}", permissionRuleMap.size());
    }

    /**
     * 判断当前Agent和Tool调用是否满足授权配置。
     *
     * @param permissionSubject Tool权限判断主体
     * @return 权限判断结果
     */
    @Override
    public ToolPermissionDecision evaluate(ToolPermissionSubject permissionSubject) {

        if (Objects.isNull(permissionSubject)) {
            throw new IllegalArgumentException("Tool权限判断主体不能为空");
        }
        // 根据Agent编码和Tool名称构造授权规则查询键。
        ToolPermissionRuleKey permissionRuleKey = new ToolPermissionRuleKey(permissionSubject.getAgentCode(), permissionSubject.getToolName());
        // 从启动阶段已经构建完成的不可修改Map中查询对应授权规则。
        ToolPermissionRule permissionRule = permissionRuleMap.get(permissionRuleKey);

        // 没有显式授权规则时按照默认拒绝策略返回RULE_NOT_FOUND。
        if (Objects.isNull(permissionRule)) {
            return ToolPermissionDecision.deny(null, ToolPermissionDecisionReasonEnum.RULE_NOT_FOUND);
        }
        // 命中规则后交由领域模型完成启用状态和用户租户范围判断。
        return permissionRule.evaluate(permissionSubject);
    }

    /**
     * Agent与Tool授权规则业务唯一键。
     *
     * <p>record是JDK 16正式提供的不可变数据载体语法。
     * 编译器会自动生成final字段、构造方法、访问器、equals()和hashCode()，
     * 非常适合用作Map Key。</p>
     *
     * <p>这里使用record只表达两个字段组成的权限索引键，
     * 不让它承担业务规则，因此比编写普通类更紧凑且不影响可读性。</p>
     *
     * @param agentCode Agent稳定编码
     * @param toolName Tool稳定名称
     */
    private record ToolPermissionRuleKey(String agentCode, String toolName) {
        /**
         * record紧凑规范构造方法。
         *
         * <p>这是JDK 16 record提供的语法：
         * 不需要重复声明参数列表，构造结束后编译器会自动把处理后的参数赋值给final字段。</p>
         */
        private ToolPermissionRuleKey {
            if (StringUtils.isBlank(agentCode)) {
                throw new IllegalArgumentException("Tool权限规则Key中的Agent编码不能为空");
            }
            if (StringUtils.isBlank(toolName)) {
                throw new IllegalArgumentException("Tool权限规则Key中的Tool名称不能为空");
            }
            // 在赋值给record字段前统一去除两端空白，确保Map查询键稳定一致。
            agentCode = StringUtils.trim(agentCode);
            toolName = StringUtils.trim(toolName);
        }
    }
}