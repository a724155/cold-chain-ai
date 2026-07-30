package com.ymm.coldchainai.agent.conversation.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Agent Chat Memory窗口配置。
 *
 * <p>Chat History可以长期保存一个Conversation的全部消息，
 * 但Chat Memory只选择最近一部分有效消息发送给模型。</p>
 *
 * <p>限制Memory窗口能够控制Prompt长度、模型Token成本和历史噪声，
 * 避免Conversation持续数月后把全部聊天记录一次性发送给模型。</p>
 *
 * <p>在挖矿流程中，完整Chat History相当于档案仓库保存的全部历史资料，
 * Chat Memory则相当于矿工本次进场前放入随身资料袋的最近关键记录。
 * 档案仓库可以很大，但随身资料袋必须限制容量。</p>
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "cold-chain-ai.agent.chat-memory")
public class AgentChatMemoryProperties {

    /**
     * 单次最多读取的历史消息数量。
     *
     * <p>这里限制的是消息条数而不是问答轮数。
     * 默认20条消息通常对应最多10轮完整USER和ASSISTANT问答。</p>
     */
    @Min(value = 1, message = "Chat Memory消息数量必须大于0")
    @Max(value = 100, message = "Chat Memory消息数量不能超过100")
    private Integer maxMessages = 20;
}