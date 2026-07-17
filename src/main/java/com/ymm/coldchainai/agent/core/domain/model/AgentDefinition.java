package com.ymm.coldchainai.agent.core.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Agent 定义。
 *
 * <p>该对象描述系统中一个可被调用的 Agent，包括稳定编码、展示名称、
 * 能力说明、启用状态和是否为默认 Agent。</p>
 *
 * <p><strong>产品与前端协作提醒：</strong>
 * Agent名称、能力说明、是否允许用户选择、停用后的页面行为等属于产品能力的一部分，
 * 开发前必须与产品确认真实需求。如果agentCode需要暴露给前端，还必须与前端约定编码值、
 * 是否区分大小写、字段是否必填以及后续新增Agent时的兼容策略，不能由后端自行决定后直接上线。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentDefinition {

    /**
     * Agent稳定编码。
     *
     * <p>该编码用于接口传参和注册中心查找，创建后不应随意修改。</p>
     */
    private final String agentCode;

    /**
     * Agent展示名称。
     */
    private final String agentName;

    /**
     * Agent能力说明。
     */
    private final String description;

    /**
     * Agent是否允许被请求调用。
     */
    private final Boolean enabled;

    /**
     * 未指定agentCode时，是否将当前Agent作为默认Agent。
     */
    private final Boolean defaultAgent;
}
