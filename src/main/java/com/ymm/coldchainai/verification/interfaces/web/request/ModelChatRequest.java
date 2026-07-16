package com.ymm.coldchainai.verification.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 普通模型调用验证请求。
 *
 * <p>该对象只负责接收 HTTP 请求参数，不能直接传递到正式 Agent、
 * Domain、Repository 或数据库层。</p>
 */
@Getter
@Setter
public class ModelChatRequest {

    /**
     * 用户提交给模型的问题。
     *
     * <p>限制最大长度可以避免用户一次提交过大的内容，
     * 导致模型 Token 消耗异常或接口响应时间过长。</p>
     */
    @NotBlank(message = "模型问题不能为空")
    @Size(max = 2000, message = "模型问题长度不能超过2000个字符")
    private String question;
}
