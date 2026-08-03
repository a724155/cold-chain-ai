package com.ymm.coldchainai.agent.audit.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 根据requestId查询Tool执行审计列表的local验证请求。
 *
 * <p>用户和租户身份不允许从URL提交，必须由后端认证上下文获取。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 上线正式管理端前，需要与产品和前端确认审计信息展示权限、
 * 错误信息展示范围、时间格式和空列表展示方式。</p>
 */
@Getter
@Setter
public class QueryToolExecutionAuditListRequest {

    /**
     * 需要查询的Agent请求唯一标识。
     */
    @NotBlank(message = "requestId不能为空")
    @Size(max = 64, message = "requestId长度不能超过64个字符")
    private String requestId;
}
