package com.ymm.coldchainai.agent.permission.domain.service;

import com.ymm.coldchainai.agent.permission.domain.model.ToolPermissionDecision;
import com.ymm.coldchainai.agent.permission.domain.model.ToolPermissionSubject;

/**
 * Agent Tool权限策略领域端口。
 *
 * <p>上层只负责提交Agent、Tool、用户和租户信息，
 * 不关心权限规则最终来自YAML、数据库、权限中心还是远程RPC。</p>
 *
 * <p>在挖矿流程中，该接口相当于统一设备门禁：
 * 调度员只提交通行信息，不需要知道门禁规则保存在纸质名单还是中央权限平台。</p>
 */
public interface IToolPermissionPolicy {

    /**
     * 判断当前主体是否允许调用指定Tool。
     *
     * <p>找不到授权规则时必须返回拒绝结果，不能默认允许。</p>
     *
     * @param permissionSubject Tool权限判断主体
     * @return 权限判断结果
     */
    ToolPermissionDecision evaluate(ToolPermissionSubject permissionSubject);
}