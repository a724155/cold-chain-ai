package com.ymm.coldchainai.agent.core.application.memory.provider;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.memory.model.AgentMemoryMessage;

import java.util.List;

/**
 * Agent Conversation Memory提供者。
 *
 * <p>Agent Core通过该端口读取最近对话上下文，
 * 但不关心历史消息最终来自MySQL、Redis还是其他存储。</p>
 *
 * <p>实现必须使用受信任用户和租户身份完成Conversation所有权校验，
 * 禁止仅凭conversationId读取聊天记忆。</p>
 *
 * <p>在挖矿流程中，该接口相当于智能开采设备使用的档案调阅窗口：
 * 设备只提出“读取当前项目最近记录”，
 * 不直接进入MySQL档案库或者自行绕过客户身份检查。</p>
 */
public interface IAgentConversationMemoryProvider {

    /**
     * 加载指定Conversation最近的有效上下文记忆。
     *
     * <p>返回结果必须按照sequenceNo升序排列，
     * 并且只包含可以安全组成完整问答的历史消息。</p>
     *
     * @param conversationId Conversation业务唯一标识
     * @param agentInvocationContext 受信任用户和租户上下文
     * @return 按真实对话顺序排列的上下文消息列表
     */
    List<AgentMemoryMessage> loadRecentMemory(String conversationId, AgentInvocationContext agentInvocationContext);
}
