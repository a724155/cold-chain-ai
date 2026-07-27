package com.ymm.coldchainai.agent.conversation.domain.enumtype;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * AI Agent会话状态。
 *
 * <p>会话创建后默认处于进行中状态，关闭后不应继续追加新的用户消息和AI回答。</p>
 *
 * <p>在挖矿流程中，一个Conversation相当于一张持续生效的项目任务单：
 * ACTIVE表示项目仍在进行，可以继续追加任务；
 * CLOSED表示项目已经结项，不应继续向原任务单追加新的开采记录。</p>
 */
@Getter
@AllArgsConstructor
public enum ConversationStatusEnum {

    /**
     * 会话进行中，可以继续进行多轮问答。
     */
    ACTIVE(1, "进行中"),

    /**
     * 会话已经关闭，不允许继续追加消息。
     */
    CLOSED(2, "已关闭");

    /**
     * 数据库存储状态码。
     */
    private final Integer code;

    /**
     * 状态说明。
     */
    private final String message;

    /**
     * 根据数据库状态码获取会话状态。
     *
     * @param code 数据库存储的会话状态码
     * @return 对应会话状态
     */
    public static ConversationStatusEnum fromCode(Integer code) {
        if (Objects.isNull(code)) {
            throw new IllegalArgumentException("会话状态码不能为空");
        }

        for (ConversationStatusEnum statusEnum : values()) {
            if (statusEnum.getCode().equals(code)) {
                return statusEnum;
            }
        }

        throw new IllegalArgumentException("未知会话状态码，code=%s".formatted(code));
    }
}
