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
 * <p><strong>Chat History与Chat Memory顺序说明：</strong></p>
 *
 * <p>Chat History必须按照sequenceNo永久保留消息真实落库顺序，
 * 例如同一个Conversation发生两个并发请求时，数据库可能真实记录为：</p>
 *
 * <p>USER-A → USER-B → ASSISTANT-B → ASSISTANT-A。</p>
 *
 * <p>Chat Memory则是提供给模型理解上下文的逻辑问答视图。
 * 它需要根据requestId确认“哪个ASSISTANT回答了哪个USER”，
 * 再按照USER问题发起顺序整理为：</p>
 *
 * <p>USER-A → ASSISTANT-A → USER-B → ASSISTANT-B。</p>
 *
 * <p>这种整理不会修改Chat History数据库记录，也不会伪造审计数据；
 * 它只是在模型调用前恢复正确的问答归属关系，
 * 避免模型把并发请求的交叉回答理解错位。</p>
 *
 * <p>AgentMemoryMessage中的sequenceNo仍然保留原始数据库顺序号，
 * 用于日志、审计和异常排查。因此发生并发交叉时，
 * Memory返回List中的sequenceNo不保证全局递增。</p>
 *
 * <p>在挖矿流程中，该接口相当于智能开采设备使用的档案调阅窗口：
 * 档案仓库继续保留资料真实归档顺序，
 * 档案员则按照任务编号把每份客户要求与对应开采报告整理成逻辑任务包。</p>
 */
public interface IAgentConversationMemoryProvider {

    /**
     * 加载指定Conversation最近的有效上下文记忆。
     *
     * <p>返回结果只包含已经形成完整USER和ASSISTANT问答的历史轮次：</p>
     *
     * <p>1. 同一轮USER和ASSISTANT必须具有相同requestId；</p>
     * <p>2. ASSISTANT的原始sequenceNo必须大于对应USER；</p>
     * <p>3. 完整问答轮次按照USER消息的原始sequenceNo排列；</p>
     * <p>4. 每轮Memory中USER后面紧跟该requestId对应的ASSISTANT；</p>
     * <p>5. 模型失败留下的孤立USER以及窗口截断产生的孤立ASSISTANT不进入Memory。</p>
     *
     * <p>注意：该方法返回的是逻辑问答顺序，不要求所有Memory消息的sequenceNo全局递增。
     * 原始物理落库顺序仍然完整保存在Chat History中。</p>
     *
     * @param conversationId Conversation业务唯一标识
     * @param agentInvocationContext 受信任用户和租户上下文
     * @return 按USER问题发起顺序整理的完整上下文问答列表
     */
    List<AgentMemoryMessage> loadRecentMemory(String conversationId, AgentInvocationContext agentInvocationContext);
}
