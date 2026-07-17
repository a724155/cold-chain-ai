package com.ymm.coldchainai.shared.response;

import com.ymm.coldchainai.shared.exception.code.IErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 冷运 AI 系统统一接口返回对象。
 *
 * <p>所有 Controller 对外返回的数据统一封装成该对象，
 * 避免成功、业务失败和系统异常使用不同的 JSON 结构。</p>
 *
 * <p>字段含义：</p>
 * <p>code：业务结果编码，0 表示成功，非 0 表示失败。</p>
 * <p>message：提供给调用方或前端展示的结果说明。</p>
 * <p>data：接口成功时返回的业务数据，失败时通常为空。</p>
 *
 * @param <T> 接口业务数据类型
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class YmmResult<T> {

    /**
     * 操作成功时使用的统一业务编码。
     */
    private static final Integer SUCCESS_CODE = 0;

    /**
     * 操作成功时使用的统一提示信息。
     */
    private static final String SUCCESS_MESSAGE = "success";

    /**
     * 未指定失败编码时使用的默认业务失败编码。
     */
    private static final Integer DEFAULT_FAIL_CODE = 40001;

    /**
     * 未指定失败信息时使用的默认提示信息。
     */
    private static final String DEFAULT_FAIL_MESSAGE = "操作失败";

    /**
     * 业务结果编码。
     */
    private final Integer code;

    /**
     * 结果提示信息。
     */
    private final String message;

    /**
     * 接口业务数据。
     */
    private final T data;

    /**
     * 创建不携带业务数据的成功结果。
     *
     * @param <T> 返回数据类型
     * @return 统一成功结果
     */
    public static <T> YmmResult<T> success() {
        return new YmmResult<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /**
     * 创建携带业务数据的成功结果。
     *
     * @param data 接口需要返回的业务数据
     * @param <T> 返回数据类型
     * @return 统一成功结果
     */
    public static <T> YmmResult<T> success(T data) {
        return new YmmResult<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /**
     * 创建统一失败结果。
     *
     * @param code 业务失败编码，为空时使用默认失败编码
     * @param message 业务失败信息，为空时使用默认失败信息
     * @param <T> 返回数据类型
     * @return 统一失败结果
     */
    public static <T> YmmResult<T> fail(Integer code, String message) {
        // 对失败编码进行兜底，避免异常处理过程中再次产生空指针或无效响应。
        Integer resultCode = Objects.isNull(code) ? DEFAULT_FAIL_CODE : code;

        // 对失败信息进行兜底，确保前端始终能够获得可展示的提示内容。
        String resultMessage = StringUtils.defaultIfBlank(message, DEFAULT_FAIL_MESSAGE);

        return new YmmResult<>(resultCode, resultMessage, null);
    }

    /**
     * 根据统一错误码创建失败结果。
     *
     * @param errorCode 统一错误码，包含业务编码和默认提示信息
     * @param <T> 返回数据类型
     * @return 统一失败结果
     */
    public static <T> YmmResult<T> fail(IErrorCode errorCode) {
        if (Objects.isNull(errorCode)) {
            // 错误码对象为空时使用YmmResult自身的默认失败编码和提示信息。
            return fail(DEFAULT_FAIL_CODE, DEFAULT_FAIL_MESSAGE);
        }

        return fail(errorCode.getCode(), errorCode.getMessage());
    }

    /**
     * 根据统一错误码和自定义提示信息创建失败结果。
     *
     * <p>该方法保留错误码枚举中的业务编码，但允许调用方覆盖默认提示信息。
     * 例如参数校验失败时，编码固定为40000，message可以使用具体字段的校验提示。</p>
     *
     * @param errorCode 统一错误码
     * @param message 本次请求需要返回的具体提示信息
     * @param <T> 返回数据类型
     * @return 统一失败结果
     */
    public static <T> YmmResult<T> fail(IErrorCode errorCode, String message) {
        if (Objects.isNull(errorCode)) {
            // 错误码为空时仍然保留调用方提供的提示信息，并由原有fail方法完成最终兜底。
            return fail(DEFAULT_FAIL_CODE, message);
        }

        return fail(errorCode.getCode(), message);
    }
}
