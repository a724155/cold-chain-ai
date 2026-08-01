package com.ymm.coldchainai.agent.audit.domain.enumtype;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Objects;

/**
 * Agent Tool执行状态枚举。
 *
 * <p>Tool执行记录从RUNNING开始，只能进入SUCCEEDED或者FAILED终态。SUCCEEDED和FAILED不能再次发生状态流转。</p>
 *
 * <p>在挖矿流程中，RUNNING表示外协设备已经开始作业，
 * SUCCEEDED表示设备完成任务并交付结果，
 * FAILED表示设备作业过程中发生业务或者系统异常。</p>
 */
@Getter
@AllArgsConstructor
public enum ToolExecutionStatusEnum {

    /**
     * Tool正在执行。
     */
    RUNNING(10, "执行中"),

    /**
     * Tool执行成功。
     */
    SUCCEEDED(20, "执行成功"),

    /**
     * Tool执行失败。
     */
    FAILED(30, "执行失败");

    /**
     * Tool执行状态数据库编码。
     */
    private final Integer code;

    /**
     * Tool执行状态说明。
     */
    private final String message;

    /**
     * 根据数据库状态码恢复Tool执行状态枚举。
     *
     * <p>这里使用Stream在一次遍历中完成状态码匹配。
     * 未知状态码不能返回null，否则异常会推迟到后续业务代码中才暴露。</p>
     *
     * @param code 数据库Tool执行状态码
     * @return 对应Tool执行状态枚举
     */
    public static ToolExecutionStatusEnum fromCode(Integer code) {
        if (Objects.isNull(code)) {
            throw new IllegalArgumentException("Tool执行状态码不能为空");
        }

        // 遍历全部枚举值，查找数据库编码与传入编码相同的执行状态。
        return Arrays.stream(values())
                .filter(status -> Objects.equals(status.getCode(), code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知Tool执行状态码，code=" + code));
    }
}
