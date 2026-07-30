package com.ymm.coldchainai.agent.core.application.executor;

import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.memory.model.AgentMemoryMessage;
import com.ymm.coldchainai.agent.core.domain.model.AgentDefinition;

import java.util.List;

/**
 * Agent执行器接口。
 *
 * <p>Application Service只依赖该接口，不直接依赖Spring AI、
 * ChatClient或者某个具体模型厂商。</p>
 *
 * <p>执行器除接收当前用户问题外，还接收Application层已经完成权限校验和窗口裁剪的历史Memory消息。
 * 具体如何转换成模型框架消息，由Infrastructure实现。</p>
 *
 * <p>在挖矿流程中，该接口相当于项目经理向设备操作员下达的标准作业单：
 * 既包含当前开采要求，也包含经过档案部门审核整理的最近项目记录。</p>
 */
public interface IAgentExecutor {

    /**
     * 执行一次正式Agent问答。
     *
     * @param requestId 本次Agent请求唯一标识
     * @param agentDefinition 本次需要执行的Agent定义
     * @param agentInvocationContext 本次调用使用的受信任用户和租户上下文
     * @param memoryMessageList 当前Conversation最近的有效上下文消息
     * @param question 本轮用户问题
     * @return 模型生成的完整答案
     */
    String execute(String requestId, AgentDefinition agentDefinition, AgentInvocationContext agentInvocationContext,
            List<AgentMemoryMessage> memoryMessageList, String question);
}
