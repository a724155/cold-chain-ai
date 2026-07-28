package com.ymm.coldchainai.agent.conversation.application.service;

import com.ymm.coldchainai.agent.conversation.application.command.ResolveAgentConversationCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentConversationDTO;

/**
 * Agent会话Application Service。
 *
 * <p>负责根据当前请求判断应该创建新Conversation，
 * 还是继续已有Conversation，并完成数据所有权、会话状态和Agent绑定关系校验。</p>
 */
public interface IAgentConversationApplicationService {

    /**
     * 获取已有会话或者创建一个新的Agent会话。
     *
     * @param command 会话解析命令
     * @return 当前请求最终使用的Conversation
     */
    AgentConversationDTO resolveConversation(ResolveAgentConversationCommand command);
}
