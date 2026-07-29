package com.ymm.coldchainai.agent.conversation.infrastructure.persistence.mapper;

import com.ymm.coldchainai.agent.conversation.infrastructure.persistence.dataobject.AgentChatMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent聊天消息MyBatis Mapper。
 *
 * <p>该Mapper负责执行cold_chain_ai_chat_message表的新增和历史消息查询SQL，
 * 不负责判断Conversation是否关闭，也不负责计算sequenceNo。</p>
 *
 * <p>在挖矿流程中，该Mapper相当于档案仓库中的具体登记人员：
 * Repository把已经整理好的作业档案交给它，它按照固定表结构写入或者读取记录。</p>
 */
@Mapper
public interface IAgentChatMessageMapper {

    /**
     * 新增一条USER或者ASSISTANT聊天消息。
     *
     * <p>messageId必须全局唯一；
     * conversationId与sequenceNo组合也必须唯一，
     * 防止同一个Conversation出现两条顺序相同的消息。</p>
     *
     * @param chatMessageDO 待写入数据库的聊天消息持久化对象
     * @return 实际插入记录数，正常情况下必须为1
     */
    int insert(AgentChatMessageDO chatMessageDO);

    /**
     * 根据会话标识和数据所有者查询最近若干条聊天消息。
     *
     * <p>SQL首先按sequence_no倒序选择最近limit条，
     * 再在外层按sequence_no正序排列，
     * 使返回结果既是最近一段历史，又保持USER和ASSISTANT真实对话顺序。</p>
     *
     * <p>conversationId用于定位聊天窗口，
     * currentUserId和currentTenantId用于校验该聊天窗口是否属于当前调用者，
     * 三个条件缺一不可。</p>
     *
     * <p>在挖矿流程中，这相当于档案管理员先从厚重的项目档案中抽取最后N条作业记录，
     * 再按照第一条到最后一条的正确时间顺序交给智能设备阅读。</p>
     *
     * @param conversationId 会话业务唯一标识
     * @param currentUserId 当前受信任用户ID
     * @param currentTenantId 当前受信任租户ID
     * @param limit 最多查询的最近消息数量
     * @return 按sequenceNo升序排列的最近消息数据库对象列表
     */
    List<AgentChatMessageDO> selectRecentByConversationIdAndOwner(
            @Param("conversationId") String conversationId,
            @Param("currentUserId") Long currentUserId,
            @Param("currentTenantId") Long currentTenantId,
            @Param("limit") Integer limit);
}
