package com.ymm.coldchainai.agent.core.infrastructure.registry;

import com.ymm.coldchainai.agent.core.application.registry.IAgentRegistry;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;
import com.ymm.coldchainai.shared.exception.BusinessException;
import com.ymm.coldchainai.shared.exception.code.AgentErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于Spring Bean的Agent注册中心实现。
 *
 * <p>Spring启动时会收集所有AgentDefinition Bean，
 * 校验Agent编码、启用状态、默认Agent和重复定义，然后构建只读注册表。</p>
 *
 * <p>当前使用内存注册表。未来如果Agent定义改为数据库或配置中心加载，
 * Application层的IAgentRegistry接口不需要修改。</p>
 */
@Slf4j
@Component
public class AgentRegistryImpl implements IAgentRegistry {

    /**
     * Agent编码与Agent定义之间的只读映射。
     */
    private final Map<String, AgentDefinition> agentDefinitionMap;

    /**
     * 未指定agentCode时使用的默认Agent。
     */
    private final AgentDefinition defaultAgentDefinition;

    /**
     * 创建Agent注册中心并校验所有Agent定义。
     *
     * <p>这里需要在构造过程中完成集合转换、重复校验和默认Agent选择，
     * 存在明确的构造逻辑，因此不使用Lombok自动生成构造方法。</p>
     *
     * @param agentDefinitionList Spring容器中注册的所有Agent定义
     */
    @Autowired
    public AgentRegistryImpl(List<AgentDefinition> agentDefinitionList) {
        // 对Spring注入的Agent列表进行空值兜底，避免初始化阶段产生空指针。
        List<AgentDefinition> safeAgentDefinitionList = Optional.ofNullable(agentDefinitionList).orElse(Collections.emptyList());

        if (safeAgentDefinitionList.isEmpty()) {
            throw createRegistryConfigurationException("系统中没有配置任何Agent定义");
        }

        // LinkedHashMap保留Agent注册顺序，后续查询Agent列表时返回顺序更稳定。
        Map<String, AgentDefinition> mutableAgentDefinitionMap = new LinkedHashMap<>();

        // 临时变量记录默认Agent，构造完成后赋值给final字段。
        AgentDefinition resolvedDefaultAgentDefinition = null;

        for (AgentDefinition agentDefinition : safeAgentDefinitionList) {
            validateAgentDefinition(agentDefinition);

            // Agent编码统一转换成小写键，使注册中心查找时不受调用方大小写影响。
            String normalizedAgentCode = normalizeAgentCode(agentDefinition.getAgentCode());

            if (mutableAgentDefinitionMap.containsKey(normalizedAgentCode)) {
                throw createRegistryConfigurationException("存在重复Agent编码，agentCode=%s".formatted(agentDefinition.getAgentCode()));
            }

            mutableAgentDefinitionMap.put(normalizedAgentCode, agentDefinition);

            if (Boolean.TRUE.equals(agentDefinition.getDefaultAgent())) {
                if (Objects.nonNull(resolvedDefaultAgentDefinition)) {
                    throw createRegistryConfigurationException("系统只能配置一个默认Agent");
                }

                if (!Boolean.TRUE.equals(agentDefinition.getEnabled())) {
                    throw createRegistryConfigurationException("默认Agent必须处于启用状态");
                }

                resolvedDefaultAgentDefinition = agentDefinition;
            }
        }

        if (Objects.isNull(resolvedDefaultAgentDefinition)) {
            throw createRegistryConfigurationException("系统必须配置一个默认Agent");
        }

        // 转换成只读Map，避免注册中心初始化完成后被其他代码意外修改。
        this.agentDefinitionMap = Collections.unmodifiableMap(mutableAgentDefinitionMap);

        // 保存已经校验通过的默认Agent。
        this.defaultAgentDefinition = resolvedDefaultAgentDefinition;

        log.info("Agent注册中心初始化完成，agentCount={}，defaultAgentCode={}", agentDefinitionMap.size(), defaultAgentDefinition.getAgentCode());
    }

    /**
     * 根据Agent编码获取可以执行的Agent。
     *
     * @param agentCode Agent编码，可以为空
     * @return 可以执行的Agent定义
     */
    @Override
    public AgentDefinition getRequiredAgent(String agentCode) {
        if (StringUtils.isBlank(agentCode)) {
            // 调用方没有指定Agent时使用系统默认Agent。
            return defaultAgentDefinition;
        }

        // 对请求编码进行统一标准化，与注册阶段使用相同的查找规则。
        String normalizedAgentCode = normalizeAgentCode(agentCode);

        AgentDefinition agentDefinition = agentDefinitionMap.get(normalizedAgentCode);

        if (Objects.isNull(agentDefinition)) {
            throw new BusinessException(AgentErrorCodeEnum.AGENT_NOT_FOUND, "指定Agent不存在，agentCode=%s".formatted(agentCode));
        }

        if (!Boolean.TRUE.equals(agentDefinition.getEnabled())) {
            throw new BusinessException(AgentErrorCodeEnum.AGENT_DISABLED, "指定Agent已停用，agentCode=%s".formatted(agentDefinition.getAgentCode()));
        }

        return agentDefinition;
    }

    /**
     * 查询当前所有已启用的Agent。
     *
     * @return 已启用Agent列表
     */
    @Override
    public List<AgentDefinition> listEnabledAgents() {
        // 过滤空元素和停用Agent，并返回不可修改的新列表。
        return agentDefinitionMap.values().stream()
                .filter(Objects::nonNull)
                .filter(agentDefinition -> Boolean.TRUE.equals(agentDefinition.getEnabled()))
                .toList();
    }

    /**
     * 校验单个Agent定义。
     *
     * @param agentDefinition 待校验Agent定义
     */
    private void validateAgentDefinition(AgentDefinition agentDefinition) {
        if (Objects.isNull(agentDefinition)) {
            throw createRegistryConfigurationException("Agent定义不能为空");
        }

        if (StringUtils.isBlank(agentDefinition.getAgentCode())) {
            throw createRegistryConfigurationException("Agent编码不能为空");
        }

        if (StringUtils.isBlank(agentDefinition.getAgentName())) {
            throw createRegistryConfigurationException("Agent名称不能为空，agentCode=%s".formatted(agentDefinition.getAgentCode()));
        }

        if (Objects.isNull(agentDefinition.getEnabled())) {
            throw createRegistryConfigurationException("Agent启用状态不能为空，agentCode=%s".formatted(agentDefinition.getAgentCode()));
        }

        if (Objects.isNull(agentDefinition.getDefaultAgent())) {
            throw createRegistryConfigurationException("Agent默认状态不能为空，agentCode=%s".formatted(agentDefinition.getAgentCode()));
        }
    }

    /**
     * 将Agent编码转换成注册中心统一使用的标准格式。
     *
     * @param agentCode 原始Agent编码
     * @return 去除首尾空格并转成小写的Agent编码
     */
    private String normalizeAgentCode(String agentCode) {
        return StringUtils.trim(agentCode).toLowerCase(Locale.ROOT);
    }

    /**
     * 创建Agent注册配置异常。
     *
     * @param detailMessage 具体配置错误信息
     * @return Agent注册配置异常
     */
    private IllegalStateException createRegistryConfigurationException(String detailMessage) {
        String errorMessage = "%s：%s".formatted(AgentErrorCodeEnum.AGENT_REGISTRY_CONFIGURATION_ERROR.getMessage(), detailMessage);
        return new IllegalStateException(errorMessage);
    }
}
