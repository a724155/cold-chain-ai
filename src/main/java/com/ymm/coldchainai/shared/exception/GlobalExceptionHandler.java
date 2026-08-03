package com.ymm.coldchainai.shared.exception;

import com.ymm.coldchainai.shared.response.YmmResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.*;

/**
 * 冷运 AI 系统全局异常处理器。
 *
 * <p>该类统一捕获 Controller 请求链路中的异常，
 * 将不同异常转换成固定格式的 YmmResult 返回给调用方。</p>
 *
 * <p>业务异常属于可预期失败，返回业务失败编码；
 * 请求格式错误使用对应 HTTP 状态码；
 * 未知系统异常返回 HTTP 500，并生成 requestId 方便日志排查。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 请求参数校验失败编码。
     */
    private static final Integer PARAMETER_VALIDATION_ERROR_CODE = 40000;

    /**
     * 请求体格式错误编码。
     */
    private static final Integer REQUEST_BODY_ERROR_CODE = 40002;

    /**
     * 请求方法不支持编码。
     */
    private static final Integer REQUEST_METHOD_NOT_SUPPORTED_CODE = 40500;

    /**
     * 请求媒体类型不支持编码。
     */
    private static final Integer MEDIA_TYPE_NOT_SUPPORTED_CODE = 41500;

    /**
     * 未知系统异常编码。
     */
    private static final Integer SYSTEM_ERROR_CODE = 50000;

    /**
     * Agent执行异常编码。
     */
    private static final Integer AGENT_EXECUTION_ERROR_CODE = 50001;

    /**
     * Agent执行失败时返回给调用方的安全提示。
     */
    private static final String AGENT_EXECUTION_ERROR_MESSAGE = "Agent执行失败，请稍后重试";

    /**
     * 参数校验失败时的默认提示信息。
     */
    private static final String PARAMETER_VALIDATION_ERROR_MESSAGE = "请求参数校验失败";

    /**
     * 请求体无法解析时的提示信息。
     */
    private static final String REQUEST_BODY_ERROR_MESSAGE = "请求体格式错误，请检查 JSON 格式";

    /**
     * 请求方法不支持时的提示信息。
     */
    private static final String REQUEST_METHOD_NOT_SUPPORTED_MESSAGE = "当前请求方法不支持";

    /**
     * Content-Type 不支持时的提示信息。
     */
    private static final String MEDIA_TYPE_NOT_SUPPORTED_MESSAGE = "请求 Content-Type 不支持，请使用 application/json";

    /**
     * 未知系统异常时返回给调用方的统一提示，避免直接暴露服务器异常信息。
     */
    private static final String SYSTEM_ERROR_MESSAGE = "系统繁忙，请稍后重试";

    /**
     * 处理可预期的业务异常。
     *
     * <p>业务异常通常由业务校验主动抛出，例如订单状态错误或参数不符合业务规则。
     * 此类异常记录 WARN 日志，不记录完整异常堆栈，避免正常业务失败污染错误日志。</p>
     *
     * @param exception 业务异常
     * @param request 当前 HTTP 请求
     * @return 统一业务失败结果
     */
    @ExceptionHandler(BusinessException.class)
    public YmmResult<Void> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        // 请求地址用于定位是哪个接口触发了业务异常。
        String requestUri = getRequestUri(request);

        log.warn("业务异常，requestUri={}，code={}，message={}", requestUri, exception.getCode(), exception.getMessage());

        return YmmResult.fail(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理 @Valid 请求对象校验失败异常。
     *
     * @param exception 参数校验异常
     * @param request 当前 HTTP 请求
     * @return HTTP 400 和统一失败结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<YmmResult<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception, HttpServletRequest request) {
        // 获取所有字段校验错误，后续优先返回第一条可展示的校验信息。
        List<ObjectError> objectErrorList = Optional.ofNullable(exception.getBindingResult().getAllErrors()).orElse(Collections.emptyList());

        // 过滤空错误对象和空错误信息，避免异常处理器自身再次产生空指针。
        String errorMessage = objectErrorList.stream()
                .filter(Objects::nonNull)
                .map(ObjectError::getDefaultMessage)
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .orElse(PARAMETER_VALIDATION_ERROR_MESSAGE);

        // 请求地址用于在日志中定位具体校验失败接口。
        String requestUri = getRequestUri(request);

        log.warn("请求参数校验失败，requestUri={}，message={}", requestUri, errorMessage);

        return ResponseEntity.badRequest().body(YmmResult.fail(PARAMETER_VALIDATION_ERROR_CODE, errorMessage));
    }

    /**
     * 处理请求体为空或 JSON 格式错误异常。
     *
     * @param exception 请求体解析异常
     * @param request 当前 HTTP 请求
     * @return HTTP 400 和统一失败结果
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<YmmResult<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException exception, HttpServletRequest request) {
        // 请求地址用于定位是哪一个接口收到了错误 JSON。
        String requestUri = getRequestUri(request);

        log.warn("请求体解析失败，requestUri={}，exceptionMessage={}", requestUri, exception.getMessage());

        return ResponseEntity.badRequest().body(YmmResult.fail(REQUEST_BODY_ERROR_CODE, REQUEST_BODY_ERROR_MESSAGE));
    }

    /**
     * 处理使用错误 HTTP 方法调用接口的异常。
     *
     * @param exception 请求方法不支持异常
     * @param request 当前 HTTP 请求
     * @return HTTP 405 和统一失败结果
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<YmmResult<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        // 请求地址用于定位具体使用了错误请求方法的接口。
        String requestUri = getRequestUri(request);

        log.warn("请求方法不支持，requestUri={}，requestMethod={}", requestUri, exception.getMethod());

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(YmmResult.fail(REQUEST_METHOD_NOT_SUPPORTED_CODE, REQUEST_METHOD_NOT_SUPPORTED_MESSAGE));
    }

    /**
     * 处理 Content-Type 不支持异常。
     *
     * <p>例如 Controller 使用 @RequestBody 接收 Java 对象，
     * 但调用方在 Postman 中选择了 raw Text，导致 Content-Type 为 text/plain。</p>
     *
     * @param exception 媒体类型不支持异常
     * @param request 当前 HTTP 请求
     * @return HTTP 415 和统一失败结果
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<YmmResult<Void>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        // 请求地址用于定位具体收到错误 Content-Type 的接口。
        String requestUri = getRequestUri(request);

        log.warn("请求媒体类型不支持，requestUri={}，contentType={}", requestUri, exception.getContentType());

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(YmmResult.fail(MEDIA_TYPE_NOT_SUPPORTED_CODE, MEDIA_TYPE_NOT_SUPPORTED_MESSAGE));
    }

    /**
     * 处理未被其他异常处理方法覆盖的未知异常。
     *
     * @param exception 未知系统异常
     * @param request 当前 HTTP 请求
     * @return HTTP 500 和统一失败结果
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<YmmResult<Void>> handleException(Exception exception, HttpServletRequest request) {
        /*
         * requestId 用于关联前端错误提示和后端完整异常日志。
         * 调用方反馈 requestId 后，开发人员可以快速在日志中检索到对应异常堆栈。
         */
        String requestId = UUID.randomUUID().toString().replace("-", "");

        // 请求地址用于定位发生未知异常的接口。
        String requestUri = getRequestUri(request);

        // 未知异常必须记录完整异常堆栈，方便定位模型、数据库或代码执行问题。
        log.error("系统未知异常，requestId={}，requestUri={}", requestId, requestUri, exception);

        // 返回 requestId 但不返回原始异常信息，避免将数据库、模型密钥或内部代码信息暴露给调用方。
        String errorMessage = "%s，requestId=%s".formatted(SYSTEM_ERROR_MESSAGE, requestId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(YmmResult.fail(SYSTEM_ERROR_CODE, errorMessage));
    }

    /**
     * 处理Agent执行阶段发生的系统异常。
     *
     * @param exception Agent执行异常
     * @param request 当前HTTP请求
     * @return HTTP 500和包含requestId的统一失败结果
     */
    @ExceptionHandler(AgentExecutionException.class)
    public ResponseEntity<YmmResult<Void>> handleAgentExecutionException(AgentExecutionException exception, HttpServletRequest request) {
        // AgentExecutionException已经携带Application层生成的requestId，不能重新生成导致日志链路断开。
        String requestId = exception.getRequestId();

        // 请求地址用于定位发生Agent执行异常的具体接口。
        String requestUri = getRequestUri(request);

        // Agent系统异常必须记录原始异常堆栈，便于排查模型、网络、Advisor或Tool执行问题。
        log.error("Agent执行异常，requestId={}，requestUri={}", requestId, requestUri, exception);

        // 接口只返回安全提示和requestId，不直接暴露模型地址、密钥或原始异常信息。
        String errorMessage = "%s，requestId=%s".formatted(AGENT_EXECUTION_ERROR_MESSAGE, requestId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(YmmResult.fail(AGENT_EXECUTION_ERROR_CODE, errorMessage));
    }

    /**
     * 安全获取当前请求地址。
     *
     * @param request 当前 HTTP 请求
     * @return 请求地址，request 为空时返回空字符串
     */
    private String getRequestUri(HttpServletRequest request) {
        if (Objects.isNull(request)) {
            return StringUtils.EMPTY;
        }
        return StringUtils.defaultString(request.getRequestURI());
    }
}
