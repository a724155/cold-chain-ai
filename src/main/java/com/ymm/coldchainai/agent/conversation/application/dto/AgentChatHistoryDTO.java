package com.ymm.coldchainai.agent.conversation.application.dto;

import com.ymm.coldchainai.agent.conversation.domain.model.AgentChatMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * Agent聊天历史Application返回DTO。
 *
 * <p>该DTO表示一个Conversation最近一段聊天历史，
 * 内部消息已经按照sequenceNo从小到大排列，可以直接供接口层展示，
 * 后续也可以转换成Spring AI Chat Memory所需要的消息对象。</p>
 *
 * <p>在挖矿流程中，该DTO相当于档案管理员整理完成的一份项目档案包：
 * conversationId说明档案属于哪个项目，returnedMessageCount说明本次取出了多少条记录，
 * chatMessageList则按照真实作业顺序保存每一条记录。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class AgentChatHistoryDTO {

    /**
     * 当前聊天历史所属Conversation业务唯一标识。
     */
    private final String conversationId;

    /**
     * 本次实际返回的聊天消息数量。
     */
    private final Integer returnedMessageCount;

    /**
     * 按照sequenceNo升序排列的聊天消息DTO列表。
     */
    private final List<AgentChatMessageDTO> chatMessageList;

    /**
     * 将Conversation消息领域对象列表转换成Application DTO。
     *
     * <p>该方法会对列表本身和列表元素分别进行空值防御。
     * 列表为null时按空列表处理；列表中出现null元素时说明上游查询或转换逻辑异常，
     * 此时不能静默跳过，否则可能导致消息顺序与messageCount难以排查。</p>
     *
     * @param conversationId Conversation业务唯一标识
     * @param chatMessageList 聊天消息领域对象列表
     * @return Agent聊天历史DTO
     */
    public static AgentChatHistoryDTO fromDomainList(String conversationId, List<AgentChatMessage> chatMessageList) {

        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("会话标识不能为空");
        }

        List<AgentChatMessage> safeChatMessageList = ListUtils.emptyIfNull(chatMessageList);

        // 每条领域消息统一通过AgentChatMessageDTO.fromDomain()转换，保证DTO边界稳定。
        // 遍历领域消息并转换DTO，同时校验元素不能为空，避免非法对象进入Controller响应链路。
        List<AgentChatMessageDTO> chatMessageDTOList = safeChatMessageList.stream()
                .map(chatMessage -> {
                    if (Objects.isNull(chatMessage)) {
                        throw new IllegalArgumentException("Agent聊天消息列表不能包含空元素");
                    }

                    return AgentChatMessageDTO.fromDomain(chatMessage);
                })
                .toList();

        return AgentChatHistoryDTO.of(conversationId, chatMessageDTOList.size(), chatMessageDTOList);
    }
}
