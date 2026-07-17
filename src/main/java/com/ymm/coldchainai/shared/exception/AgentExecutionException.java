package com.ymm.coldchainai.shared.exception;

import com.ymm.coldchainai.shared.exception.code.CommonErrorCodeEnum;
import com.ymm.coldchainai.shared.exception.code.IErrorCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent 执行异常。
 *
 * <p>该异常用于包装模型调用、Advisor执行、Tool Calling或其他Agent执行阶段
 * 出现的非预期系统异常。</p>
 *
 * <p>异常中同时保留requestId和统一错误码，
 * 使全局异常处理器不需要再次维护Agent错误编码和提示信息。</p>
 */
@Getter
public class AgentExecutionException extends RuntimeException {

    /**
     * Java 序列化版本号。
     */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 本次Agent请求唯一标识。
     */
    private final String requestId;

    /**
     * 本次Agent执行异常对应的统一错误码。
     */
    private final IErrorCode errorCode;

    /**
     * 使用requestId、统一错误码和原始异常创建Agent执行异常。
     *
     * @param requestId 本次Agent请求唯一标识
     * @param errorCode Agent执行错误码
     * @param cause 导致Agent执行失败的原始异常
     */
    public AgentExecutionException(String requestId, IErrorCode errorCode, Throwable cause) {
        // RuntimeException保留错误码默认提示和原始异常调用链，便于日志记录完整堆栈。
        super(resolveErrorCode(errorCode).getMessage(), cause);

        // requestId为空时生成兜底标识，保证异常日志与接口响应始终能够关联。
        this.requestId = StringUtils.defaultIfBlank(requestId, UUID.randomUUID().toString().replace("-", ""));

        // 错误码为空属于内部调用异常，此时使用公共系统错误码兜底。
        this.errorCode = resolveErrorCode(errorCode);
    }

    /**
     * 安全解析Agent执行错误码。
     *
     * @param errorCode 调用方传入的错误码
     * @return 有效错误码
     */
    private static IErrorCode resolveErrorCode(IErrorCode errorCode) {
        if (Objects.isNull(errorCode)) {
            return CommonErrorCodeEnum.SYSTEM_ERROR;
        }

        return errorCode;
    }
}
