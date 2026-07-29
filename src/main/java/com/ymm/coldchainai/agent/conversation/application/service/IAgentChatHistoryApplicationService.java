package com.ymm.coldchainai.agent.conversation.application.service;

import com.ymm.coldchainai.agent.conversation.application.command.AppendAgentChatMessageCommand;
import com.ymm.coldchainai.agent.conversation.application.command.QueryAgentChatHistoryCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatHistoryDTO;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatMessageDTO;

/**
 * Agent Chat History Application Service。
 *
 * <p>该接口负责把USER问题和ASSISTANT最终回答可靠地追加到Conversation中，
 * 并保证sequenceNo、messageCount和lastMessageTime保持一致。</p>
 *
 * <p>在挖矿流程中，该接口相当于项目档案登记服务：
 * 每收到一条客户要求或者矿场报告，都要先锁住项目总任务单，
 * 分配唯一记录序号，保存明细记录，再更新总任务单统计。</p>
 */
public interface IAgentChatHistoryApplicationService {

    /**
     * 在短事务内向Conversation追加一条聊天消息。
     *
     * @param command 追加消息Application命令
     * @return 已成功持久化的聊天消息
     */
    AgentChatMessageDTO appendMessage(AppendAgentChatMessageCommand command);

    /**
     * 查询指定Conversation最近若干条聊天消息。
     *
     * <p>该方法只执行读取，不分配sequenceNo、不修改messageCount，
     * 因此不需要对Conversation执行SELECT ... FOR UPDATE。</p>
     *
     * <p>在挖矿流程中，该操作只是调阅已经归档的项目记录，
     * 不会新增或者修改档案，因此无需锁住项目总任务单。</p>
     *
     * @param command 查询聊天历史Application命令
     * @return 按sequenceNo升序排列的最近聊天历史
     */
    AgentChatHistoryDTO listRecentMessages(QueryAgentChatHistoryCommand command);
}
