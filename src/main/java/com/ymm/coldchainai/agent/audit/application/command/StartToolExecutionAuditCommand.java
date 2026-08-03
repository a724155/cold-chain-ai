package com.ymm.coldchainai.agent.audit.application.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 开始一次Tool执行审计的Application命令。
 *
 * <p>该命令包含创建RUNNING审计记录需要的完整受信任信息。
 * requestId、agentCode、currentUserId和currentTenantId必须来自ToolContext，
 * 不能由模型Tool参数直接提供。</p>
 *
 * <p>inputSummary只允许保存经过筛选的安全摘要，
 * 禁止直接放入完整订单列表、支付报文、内部规范原文或者敏感身份信息。</p>
 *
 * <p>在挖矿流程中，该命令相当于一张外协设备开工申请：
 * 记录属于哪个项目任务、使用哪台设备、由谁发起以及本次作业的大致目标。</p>
 */
@Getter
@AllArgsConstructor
public class StartToolExecutionAuditCommand {

    /**
     * 当前Tool调用所属Agent请求唯一标识。
     */
    private final String requestId;

    /**
     * 当前实际执行的Agent稳定编码。
     */
    private final String agentCode;

    /**
     * 当前执行的Spring AI Tool稳定名称。
     */
    private final String toolName;

    /**
     * 发起本次Agent请求的受信任用户ID。
     */
    private final Long currentUserId;

    /**
     * 发起本次Agent请求的受信任租户ID。
     */
    private final Long currentTenantId;

    /**
     * Tool入参安全摘要。
     */
    private final String inputSummary;

    /**
     * 创建Tool审计开始命令。
     *
     * @param requestId Agent请求唯一标识
     * @param agentCode Agent稳定编码
     * @param toolName Tool稳定名称
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @param inputSummary Tool入参安全摘要
     * @return 已完成基础参数校验的审计开始命令
     */
    public static StartToolExecutionAuditCommand create(
            String requestId,
            String agentCode,
            String toolName,
            Long currentUserId,
            Long currentTenantId,
            String inputSummary) {

        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("Tool审计requestId不能为空");
        }

        if (StringUtils.isBlank(agentCode)) {
            throw new IllegalArgumentException("Tool审计Agent编码不能为空");
        }

        if (StringUtils.isBlank(toolName)) {
            throw new IllegalArgumentException("Tool审计Tool名称不能为空");
        }

        if (Objects.isNull(currentUserId) || currentUserId <= 0L) {
            throw new IllegalArgumentException("Tool审计当前用户ID必须大于0");
        }

        if (Objects.isNull(currentTenantId) || currentTenantId <= 0L) {
            throw new IllegalArgumentException("Tool审计当前租户ID必须大于0");
        }

        return new StartToolExecutionAuditCommand(
                StringUtils.trim(requestId),
                StringUtils.trim(agentCode),
                StringUtils.trim(toolName),
                currentUserId,
                currentTenantId,
                inputSummary);
    }
}
