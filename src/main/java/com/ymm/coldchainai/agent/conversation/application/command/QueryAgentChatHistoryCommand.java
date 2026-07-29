package com.ymm.coldchainai.agent.conversation.application.command;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * 查询Agent聊天历史的Application命令。
 *
 * <p>该命令用于查询指定Conversation最近若干条USER和ASSISTANT消息。
 * conversationId负责定位聊天窗口，AgentInvocationContext负责提供受信任的用户和租户身份。</p>
 *
 * <p>currentUserId和currentTenantId不能由Postman或者前端直接传入，
 * 否则调用者可能伪造其他用户身份读取不属于自己的聊天记录。</p>
 *
 * <p>在挖矿流程中，该命令相当于一张“项目档案调阅申请单”：
 * conversationId是需要调阅的项目编号，limit是需要查看的最近记录数量，
 * AgentInvocationContext则是申请人的有效身份证明和所属矿场证明。</p>
 */
@Getter
public class QueryAgentChatHistoryCommand {

    /**
     * 单次允许查询的最大消息数量。
     *
     * <p>限制最大查询数量可以避免调用方一次读取整个超长Conversation，
     * 导致数据库查询、网络响应和后续Chat Memory转换消耗过多资源。</p>
     */
    private static final Integer MAX_QUERY_LIMIT = 100;

    /**
     * 需要查询的Conversation业务唯一标识。
     */
    private final String conversationId;

    /**
     * 最多查询的最近消息数量。
     */
    private final Integer limit;

    /**
     * 当前受信任用户和租户上下文。
     */
    private final AgentInvocationContext agentInvocationContext;

    /**
     * 创建查询聊天历史命令。
     *
     * @param conversationId Conversation业务唯一标识
     * @param limit 最多查询的最近消息数量
     * @param agentInvocationContext 受信任用户和租户上下文
     * @return 已完成基础参数校验的查询命令
     */
    public static QueryAgentChatHistoryCommand create(String conversationId, Integer limit, AgentInvocationContext agentInvocationContext) {

        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("会话标识不能为空");
        }

        if (Objects.isNull(limit) || limit <= 0) {
            throw new IllegalArgumentException("消息查询数量必须大于0");
        }

        if (limit > MAX_QUERY_LIMIT) {
            throw new IllegalArgumentException("消息查询数量不能超过%s".formatted(MAX_QUERY_LIMIT));
        }

        if (Objects.isNull(agentInvocationContext)) {
            throw new IllegalArgumentException("Agent调用上下文不能为空");
        }

        if (Objects.isNull(agentInvocationContext.getCurrentUserId())) {
            throw new IllegalArgumentException("当前用户ID不能为空");
        }

        if (Objects.isNull(agentInvocationContext.getCurrentTenantId())) {
            throw new IllegalArgumentException("当前租户ID不能为空");
        }

        return new QueryAgentChatHistoryCommand(StringUtils.trim(conversationId), limit, agentInvocationContext);
    }

    /**
     * 创建查询聊天历史命令。
     *
     * @param conversationId Conversation业务唯一标识
     * @param limit 最多查询的最近消息数量
     * @param agentInvocationContext 受信任用户和租户上下文
     */
    private QueryAgentChatHistoryCommand(String conversationId, Integer limit, AgentInvocationContext agentInvocationContext) {
        this.conversationId = conversationId;
        this.limit = limit;
        this.agentInvocationContext = agentInvocationContext;
    }
}