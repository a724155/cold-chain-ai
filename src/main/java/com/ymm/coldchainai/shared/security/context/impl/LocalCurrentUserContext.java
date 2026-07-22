package com.ymm.coldchainai.shared.security.context.impl;

import com.ymm.coldchainai.shared.security.context.ICurrentUserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 本地开发环境当前用户上下文。
 *
 * <p>该实现从后端本地配置中读取固定用户ID和租户ID，只用于当前没有接入公司认证系统时验证Tool Calling链路。</p>
 *
 * <p>该类只在local环境注册。生产环境必须替换为真实认证上下文实现，例如从网关验签结果、登录Token或公司统一用户中心获取身份信息。</p>
 *
 * <p>在挖矿流程中，该组件相当于本地演练期间由矿场管理人员签发的临时工作证。它可以验证流程，但不能当成真实生产环境的身份认证系统。</p>
 */
@Component
@Profile("local")
public class LocalCurrentUserContext implements ICurrentUserContext {

    /**
     * 当前本地测试用户ID。
     */
    private final Long currentUserId;

    /**
     * 当前本地测试租户ID。
     */
    private final Long currentTenantId;

    /**
     * 创建本地用户上下文并校验配置。
     *
     * <p>构造方法需要接收配置值并执行启动校验，因此这里使用显式构造方法。
     * 配置不合法时直接阻止应用启动，避免Tool在运行时使用错误租户查询数据。</p>
     *
     * @param currentUserId 本地测试用户ID
     * @param currentTenantId 本地测试租户ID
     */
    @Autowired
    public LocalCurrentUserContext(@Value("${cold-chain-ai.security.local.current-user-id}") Long currentUserId,
                                   @Value("${cold-chain-ai.security.local.current-tenant-id}") Long currentTenantId) {
        if (Objects.isNull(currentUserId) || currentUserId <= 0L) {
            throw new IllegalStateException("本地当前用户ID必须大于0");
        }

        if (Objects.isNull(currentTenantId) || currentTenantId <= 0L) {
            throw new IllegalStateException("本地当前租户ID必须大于0");
        }

        this.currentUserId = currentUserId;
        this.currentTenantId = currentTenantId;
    }

    /**
     * 获取当前本地测试用户ID。
     *
     * @return 当前用户ID
     */
    @Override
    public Long getCurrentUserId() {
        return currentUserId;
    }

    /**
     * 获取当前本地测试租户ID。
     *
     * @return 当前租户ID
     */
    @Override
    public Long getCurrentTenantId() {
        return currentTenantId;
    }
}
