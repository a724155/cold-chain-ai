package com.ymm.coldchainai.agent.conversation.interfaces.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 查询最近Agent聊天消息的local验证请求。
 *
 * <p>该请求通过URL查询参数接收conversationId和limit，
 * currentUserId与currentTenantId仍然从受信任认证上下文获取。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 正式开发前应与产品确认聊天历史默认加载数量、最大加载数量、
 * 是否支持向前翻页以及关闭会话的历史展示规则；
 * 与前端明确conversationId必填性、limit默认值和分页兼容策略。</p>
 *
 * <p>在挖矿流程中，该请求相当于档案调阅窗口填写的简化申请表：
 * 指定项目编号以及希望查看的最近作业记录数量。</p>
 */
@Getter
@Setter
public class QueryRecentAgentChatMessageRequest {

    /**
     * 默认查询的最近消息数量。
     */
    private static final Integer DEFAULT_QUERY_LIMIT = 20;

    /**
     * 单次允许查询的最大消息数量。
     */
    private static final int MAX_QUERY_LIMIT = 100;

    /**
     * 需要查询的Conversation业务唯一标识。
     */
    @NotBlank(message = "conversationId不能为空")
    @Size(max = 64, message = "conversationId长度不能超过64个字符")
    private String conversationId;

    /**
     * 最多查询的最近消息数量。
     *
     * <p>调用方不传时默认查询最近20条消息。</p>
     */
    @Min(value = 1, message = "limit必须大于0")
    @Max(value = MAX_QUERY_LIMIT, message = "limit不能超过100")
    private Integer limit = DEFAULT_QUERY_LIMIT;
}
