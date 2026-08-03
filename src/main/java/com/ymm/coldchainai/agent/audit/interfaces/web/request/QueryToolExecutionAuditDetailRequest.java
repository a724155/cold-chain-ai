package com.ymm.coldchainai.agent.audit.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 根据toolExecutionId查询单次Tool执行审计详情的local验证请求。
 *
 * <p>HTTP请求只允许提交Tool执行标识，
 * 用户和租户身份必须由后端认证上下文提供。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 正式接入前需要与产品和前端确认字段展示权限、错误提示、
 * RUNNING状态刷新策略、时间格式和耗时单位。</p>
 */
@Getter
@Setter
public class QueryToolExecutionAuditDetailRequest {

    /**
     * 需要查询的Tool执行业务唯一标识。
     *
     * <p>该值来自Tool审计列表中的toolExecutionId，当前数据库字段最大长度为64个字符。</p>
     */
    @NotBlank(message = "toolExecutionId不能为空")
    @Size(max = 64, message = "toolExecutionId长度不能超过64个字符")
    private String toolExecutionId;
}
