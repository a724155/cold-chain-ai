package com.ymm.coldchainai.shared.security.context;

/**
 * 当前登录用户上下文接口。
 *
 * <p>业务代码通过该接口获取已经经过认证的用户ID和租户ID，不直接从HTTP请求体、模型参数或Tool参数中读取这些安全字段。</p>
 *
 * <p>在挖矿流程中，该接口相当于矿场统一的身份凭证查询规范：
 * 项目经理可以查询当前客户属于哪个公司、具有什么身份，
 * 但不能接受客户自己在任务单上随意填写一个租户编号。</p>
 */
public interface ICurrentUserContext {

    /**
     * 获取当前已认证用户ID。
     *
     * @return 当前用户ID
     */
    Long getCurrentUserId();

    /**
     * 获取当前已认证租户ID。
     *
     * @return 当前租户ID
     */
    Long getCurrentTenantId();
}
