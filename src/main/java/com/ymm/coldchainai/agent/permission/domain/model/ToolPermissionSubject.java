package com.ymm.coldchainai.agent.permission.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * Tool权限判断主体。
 *
 * <p>该对象描述“哪个Agent准备代表哪个用户和租户调用哪个Tool”。</p>
 *
 * <p>agentCode、currentUserId和currentTenantId必须来自后端受信任上下文，
 * 不能由模型Tool参数或者前端请求直接决定。</p>
 *
 * <p>在挖矿流程中，该对象相当于设备入口处接受检查的通行信息：
 * 哪个项目调度中心、哪台设备、哪个矿场以及哪个操作员准备执行任务。</p>
 */
@Getter
@AllArgsConstructor
public class ToolPermissionSubject {

    /**
     * 准备调用Tool的Agent稳定编码。
     */
    private final String agentCode;

    /**
     * 准备调用的Spring AI Tool稳定名称。
     */
    private final String toolName;

    /**
     * 当前受信任用户ID。
     */
    private final Long currentUserId;

    /**
     * 当前受信任租户ID。
     */
    private final Long currentTenantId;

    /**
     * 创建Tool权限判断主体。
     *
     * @param agentCode Agent稳定编码
     * @param toolName Tool稳定名称
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @return 已完成基础身份校验的权限判断主体
     */
    public static ToolPermissionSubject create(String agentCode, String toolName, Long currentUserId, Long currentTenantId) {

        if (StringUtils.isBlank(agentCode)) {
            throw new IllegalArgumentException("Tool权限判断Agent编码不能为空");
        }

        if (StringUtils.isBlank(toolName)) {
            throw new IllegalArgumentException("Tool权限判断Tool名称不能为空");
        }

        if (Objects.isNull(currentUserId) || currentUserId <= 0L) {
            throw new IllegalArgumentException("Tool权限判断当前用户ID必须大于0");
        }

        if (Objects.isNull(currentTenantId) || currentTenantId <= 0L) {
            throw new IllegalArgumentException("Tool权限判断当前租户ID必须大于0");
        }

        // Agent和Tool编码统一去除两端空白，避免同一授权对象因为空格形成不同键。
        return new ToolPermissionSubject(StringUtils.trim(agentCode), StringUtils.trim(toolName), currentUserId, currentTenantId);
    }
}
