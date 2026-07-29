package com.ymm.coldchainai.agent.conversation.interfaces.web.response;

import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatHistoryDTO;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatMessageDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.ListUtils;

import java.util.List;
import java.util.Objects;

/**
 * Agent聊天历史local验证响应。
 *
 * <p>该响应返回当前Conversation、本次消息数量以及按sequenceNo升序排列的消息列表。</p>
 *
 * <p>在挖矿流程中，该响应相当于档案窗口交给申请人的整理后档案包：
 * 外层说明项目编号和记录数量，内层按照真实作业顺序展示每条档案。</p>
 */
@Getter
@AllArgsConstructor
public class AgentChatHistoryResponse {

    /**
     * 当前聊天历史所属Conversation业务唯一标识。
     */
    private final String conversationId;

    /**
     * 本次实际返回的聊天消息数量。
     */
    private final Integer returnedMessageCount;

    /**
     * 按照sequenceNo升序排列的聊天消息响应列表。
     */
    private final List<AgentChatMessageResponse> chatMessageList;

    /**
     * 将Application DTO转换成聊天历史接口响应。
     *
     * <p>该方法对DTO、消息列表和列表元素分别进行空值防御，
     * 避免接口转换阶段因为异常数据产生裸空指针。</p>
     *
     * @param chatHistoryDTO Agent聊天历史Application DTO
     * @return HTTP响应对象
     */
    public static AgentChatHistoryResponse fromDTO(AgentChatHistoryDTO chatHistoryDTO) {
        if (Objects.isNull(chatHistoryDTO)) {
            throw new IllegalArgumentException("Agent聊天历史DTO不能为空");
        }

        List<AgentChatMessageDTO> chatMessageDTOList = ListUtils.emptyIfNull(chatHistoryDTO.getChatMessageList());

        // 每一条消息继续复用AgentChatMessageResponse.fromDTO()完成字段转换和空值校验。
        List<AgentChatMessageResponse> chatMessageResponseList = chatMessageDTOList.stream()
                .map(chatMessageDTO -> {
                    if (Objects.isNull(chatMessageDTO)) {
                        throw new IllegalArgumentException("Agent聊天消息DTO列表不能包含空元素");
                    }
                    return AgentChatMessageResponse.fromDTO(chatMessageDTO);
                })
                .toList();

        return new AgentChatHistoryResponse(chatHistoryDTO.getConversationId(), chatMessageResponseList.size(), chatMessageResponseList);
    }
}
