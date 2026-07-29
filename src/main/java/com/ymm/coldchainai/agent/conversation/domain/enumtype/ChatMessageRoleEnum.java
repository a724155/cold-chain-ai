package com.ymm.coldchainai.agent.conversation.domain.enumtype;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * Agent聊天消息角色。
 *
 * <p>当前Chat History只保存用户输入和模型最终回答。
 * System Prompt属于Agent运行配置，Tool调用记录后续进入独立审计表，不与普通聊天消息混合保存。</p>
 *
 * <p>在挖矿流程中，USER相当于客户提交的开采要求，ASSISTANT相当于矿场完成作业后交付给客户的最终报告。</p>
 */
@Getter
@AllArgsConstructor
public enum ChatMessageRoleEnum {

    /**
     * 用户发送的原始问题。
     */
    USER(1, "用户"),

    /**
     * Agent返回给用户的最终回答。
     */
    ASSISTANT(2, "AI助手");

    /**
     * 数据库存储角色码。
     */
    private final Integer code;

    /**
     * 角色说明。
     */
    private final String message;

    /**
     * 根据数据库角色码恢复消息角色。
     *
     * @param code 数据库存储角色码
     * @return 对应消息角色
     */
    public static ChatMessageRoleEnum fromCode(Integer code) {
        if (Objects.isNull(code)) {
            throw new IllegalArgumentException("聊天消息角色码不能为空");
        }

        for (ChatMessageRoleEnum roleEnum : values()) {
            if (roleEnum.getCode().equals(code)) {
                return roleEnum;
            }
        }

        throw new IllegalArgumentException("未知聊天消息角色码，code=%s".formatted(code));
    }
}
