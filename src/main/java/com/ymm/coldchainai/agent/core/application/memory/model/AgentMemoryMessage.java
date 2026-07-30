package com.ymm.coldchainai.agent.core.application.memory.model;

import com.ymm.coldchainai.agent.core.application.memory.enumtype.AgentMemoryMessageRoleEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * Agent模型上下文记忆消息。
 *
 * <p>该对象代表准备传递给Agent执行器的一条历史消息，
 * 只保留模型理解上下文真正需要的角色、正文和审计定位信息。</p>
 *
 * <p>Application层不直接使用Spring AI的UserMessage或AssistantMessage，
 * 具体框架对象转换由SpringAiAgentExecutor完成，
 * 从而保持IAgentExecutor端口与Spring AI解耦。</p>
 *
 * <p>在挖矿流程中，该对象相当于从完整项目档案中抽取的一条作业摘要：
 * requestId说明属于哪次开采任务，sequenceNo说明它在整个项目中的顺序，
 * messageRole和messageContent则记录客户要求或者矿场报告。</p>
 */
@Getter
@AllArgsConstructor
public class AgentMemoryMessage {

    /**
     * 产生当前历史消息的Agent请求标识。
     *
     * <p>同一轮USER和ASSISTANT消息使用相同requestId。</p>
     */
    private final String requestId;

    /**
     * 当前历史消息角色。
     */
    private final AgentMemoryMessageRoleEnum messageRole;

    /**
     * 当前历史消息完整正文。
     */
    private final String messageContent;

    /**
     * 当前历史消息在Conversation中的顺序号。
     *
     * <p>该字段不直接发送给模型，主要用于顺序校验、日志和问题排查。</p>
     */
    private final Integer sequenceNo;

    /**
     * 创建一条Agent上下文记忆消息。
     *
     * @param requestId 产生消息的Agent请求标识
     * @param messageRole 消息角色
     * @param messageContent 消息正文
     * @param sequenceNo Conversation内消息顺序
     * @return 已完成参数校验的记忆消息
     */
    public static AgentMemoryMessage create(String requestId, AgentMemoryMessageRoleEnum messageRole,
            String messageContent, Integer sequenceNo) {

        if (StringUtils.isBlank(requestId)) {
            throw new IllegalArgumentException("记忆消息requestId不能为空");
        }

        if (Objects.isNull(messageRole)) {
            throw new IllegalArgumentException("记忆消息角色不能为空");
        }

        if (StringUtils.isBlank(messageContent)) {
            throw new IllegalArgumentException("记忆消息正文不能为空");
        }

        if (Objects.isNull(sequenceNo) || sequenceNo <= 0) {
            throw new IllegalArgumentException("记忆消息顺序必须大于0");
        }

        return new AgentMemoryMessage(StringUtils.trim(requestId), messageRole, messageContent, sequenceNo);
    }
}
