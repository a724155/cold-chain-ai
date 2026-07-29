package com.ymm.coldchainai.agent.conversation.domain.repository;

import com.ymm.coldchainai.agent.conversation.domain.model.AgentChatMessage;

import java.util.List;

/**
 * Agent聊天消息Repository领域端口。
 *
 * <p>Domain和Application层通过该接口保存、查询Chat History，
 * 不直接依赖MyBatis Mapper、DO或者MySQL表结构。</p>
 *
 * <p>在挖矿流程中，该接口相当于项目作业档案室对外开放的统一窗口：
 * 上层只提出“保存一条作业记录”或“取出最近N条记录”，
 * 不需要知道档案室内部使用MyBatis、MySQL还是其他存储技术。</p>
 */
public interface IAgentChatMessageRepository {

    /**
     * 保存一条聊天消息。
     *
     * <p>调用前应保证Conversation已经存在且处于ACTIVE状态，
     * sequenceNo已经在锁定Conversation的事务中完成计算。</p>
     *
     * @param chatMessage 待保存的USER或者ASSISTANT消息领域对象
     */
    void save(AgentChatMessage chatMessage);

    /**
     * 查询指定Conversation最近若干条聊天消息。
     *
     * <p>查询结果必须按照sequenceNo从小到大排列，
     * 后续才能按照真实对话顺序转换成Spring AI ChatMemory消息。</p>
     *
     * <p>查询必须同时使用conversationId、currentUserId和currentTenantId，
     * 防止其他用户仅凭conversationId读取不属于自己的聊天历史。</p>
     *
     * @param conversationId 会话业务唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @param limit 最多返回的最近消息数量
     * @return 按真实聊天顺序排列的最近消息领域对象列表
     */
    List<AgentChatMessage> listRecentMessages(String conversationId, Long currentUserId, Long currentTenantId, Integer limit);
}
