package com.ymm.coldchainai.agent.core.infrastructure.tool;

import lombok.experimental.UtilityClass;

/**
 * Agent ToolContext上下文键。
 *
 * <p>该类统一规定传递给Tool的requestId、agentCode、当前用户和当前租户字段名称，避免执行器与不同Tool分别手写字符串造成上下文字段无法关联。</p>
 *
 * <p>在挖矿流程中，该类相当于矿场统一的任务标签规范：所有人都必须使用相同标签名称登记任务编号、矿区编号和客户身份，
 * 否则设备和工作人员可能读取不到彼此传递的信息。</p>
 */
@UtilityClass
public class AgentToolContextKeys {

    /**
     * Agent请求唯一标识。
     */
    public static final String REQUEST_ID = "coldchain.tool.request-id";

    /**
     * 当前执行的Agent稳定编码。
     */
    public static final String AGENT_CODE = "coldchain.tool.agent-code";

    /**
     * 当前已认证用户ID。
     */
    public static final String CURRENT_USER_ID = "coldchain.tool.current-user-id";

    /**
     * 当前已认证租户ID。
     */
    public static final String CURRENT_TENANT_ID = "coldchain.tool.current-tenant-id";
}
