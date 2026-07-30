package com.ymm.coldchainai.agent.conversation.infrastructure.memory;

import com.ymm.coldchainai.agent.conversation.application.command.QueryAgentChatHistoryCommand;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatHistoryDTO;
import com.ymm.coldchainai.agent.conversation.application.dto.AgentChatMessageDTO;
import com.ymm.coldchainai.agent.conversation.application.service.IAgentChatHistoryApplicationService;
import com.ymm.coldchainai.agent.conversation.domain.enumtype.ChatMessageRoleEnum;
import com.ymm.coldchainai.agent.conversation.infrastructure.config.AgentChatMemoryProperties;
import com.ymm.coldchainai.agent.core.application.context.AgentInvocationContext;
import com.ymm.coldchainai.agent.core.application.memory.enumtype.AgentMemoryMessageRoleEnum;
import com.ymm.coldchainai.agent.core.application.memory.model.AgentMemoryMessage;
import com.ymm.coldchainai.agent.core.application.memory.provider.IAgentConversationMemoryProvider;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于MySQL Chat History的Agent Conversation Memory提供者。
 *
 * <p>该类负责：</p>
 *
 * <p>1. 按conversationId、currentUserId和currentTenantId读取最近历史消息；</p>
 * <p>2. 根据requestId识别一轮USER和ASSISTANT问答；</p>
 * <p>3. 过滤模型执行失败后留下的孤立USER消息；</p>
 * <p>4. 过滤查询窗口从中间截断后出现的孤立ASSISTANT消息；</p>
 * <p>5. 将Conversation消息转换成Agent Core能够理解的中立Memory消息。</p>
 *
 * <p><strong>为什么不能直接把查询结果全部发送给模型：</strong></p>
 *
 * <p>模型执行失败时，正式链路会保留已经提交的USER问题，
 * 但不会伪造ASSISTANT回答。如果下次直接把这条孤立USER消息放入Memory，
 * 模型会看到上一条未完成问题和本轮新问题连续出现，可能混淆本轮意图。</p>
 *
 * <p>查询最近N条消息时，窗口也可能刚好从某轮ASSISTANT消息开始，
 * 它对应的USER消息已经位于窗口之外。这种孤立ASSISTANT同样不适合单独进入模型上下文。</p>
 *
 * <p>因此这里按照requestId组合完整USER和ASSISTANT问答，
 * 只有两条消息都存在时才进入Chat Memory。</p>
 *
 * <p>在挖矿流程中，该组件相当于档案筛选员：
 * 档案仓库保存所有真实记录，包括失败任务留下的客户申请；
 * 但发给矿工的随身资料必须只包含已经形成“客户要求+完成报告”的完整历史任务。</p>
 */
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentConversationMemoryProviderImpl implements IAgentConversationMemoryProvider {

    /**
     * Agent Chat History应用服务。
     *
     * <p>负责通过受信任用户和租户身份查询最近历史消息。</p>
     */
    private final IAgentChatHistoryApplicationService agentChatHistoryApplicationService;

    /**
     * Agent Chat Memory窗口配置。
     */
    private final AgentChatMemoryProperties agentChatMemoryProperties;

    /**
     * 加载指定Conversation最近的有效上下文记忆。
     *
     * @param conversationId Conversation业务唯一标识
     * @param agentInvocationContext 受信任用户和租户上下文
     * @return 按真实问答顺序排列的有效Memory消息
     */
    @Override
    public List<AgentMemoryMessage> loadRecentMemory(String conversationId, AgentInvocationContext agentInvocationContext) {

        if (StringUtils.isBlank(conversationId)) {
            throw new IllegalArgumentException("Chat Memory会话标识不能为空");
        }

        if (Objects.isNull(agentInvocationContext)) {
            throw new IllegalArgumentException("Chat Memory调用上下文不能为空");
        }

        if (Objects.isNull(agentChatMemoryProperties.getMaxMessages()) || agentChatMemoryProperties.getMaxMessages() <= 0) {
            throw new IllegalStateException("Chat Memory最大消息数量配置不合法");
        }

        // 复用已经实现的数据权限查询链路。该查询不会使用FOR UPDATE，因为这里只读取历史，不修改Conversation或ChatMessage。
        AgentChatHistoryDTO chatHistoryDTO = agentChatHistoryApplicationService.listRecentMessages(
                        QueryAgentChatHistoryCommand.create(StringUtils.trim(conversationId),
                                agentChatMemoryProperties.getMaxMessages(), agentInvocationContext));

        if (Objects.isNull(chatHistoryDTO)) {
            throw new IllegalStateException("Chat History查询结果不能为空");
        }

        List<AgentChatMessageDTO> chatMessageDTOList = ListUtils.emptyIfNull(chatHistoryDTO.getChatMessageList());

        if (chatMessageDTOList.isEmpty()) {
            return List.of();
        }

        return buildCompletedRoundMemoryList(chatMessageDTOList);
    }

    /**
     * 将最近历史消息整理成完整问答轮次。
     *
     * <p>数据库中的Chat History只是按照sequenceNo记录真实发生顺序，
     * 但并不能保证USER和ASSISTANT一定严格交替。例如并发请求可能出现：
     * USER-A → USER-B → ASSISTANT-B → ASSISTANT-A。
     *
     * <p>如果直接按照列表相邻两条消息配对，会错误地把USER-A和USER-B认为是一轮对话。
     * 因此这里使用requestId作为一次Agent请求的唯一关联标识：
     * 同一个requestId代表一次用户提问以及对应的模型回答。</p>
     *
     * <p>整体处理分两步：
     * 第一轮遍历负责建立索引，将USER和ASSISTANT分别按照requestId保存；
     * 第二轮遍历按照USER首次出现顺序寻找对应ASSISTANT，最终生成模型需要的Memory消息。</p>
     *
     * @param chatMessageDTOList 按sequenceNo升序排列的历史消息
     * @return 只包含完整USER-ASSISTANT问答轮次的Memory消息列表
     */
    private List<AgentMemoryMessage> buildCompletedRoundMemoryList(List<AgentChatMessageDTO> chatMessageDTOList) {

        /*
         * LinkedHashMap用于保存USER消息。使用requestId作为key，因为一次Agent调用只能对应一个用户问题。
         * 使用LinkedHashMap而不是HashMap，是为了保留USER消息第一次出现的顺序，最终生成Memory时仍然按照用户提问顺序排列，而不是按照模型完成回答的顺序排列。
         */
        Map<String, AgentChatMessageDTO> userMessageMap = new LinkedHashMap<>();

        // ASSISTANT只需要按requestId快速定位，不依赖Map自身迭代顺序。
        Map<String, AgentChatMessageDTO> assistantMessageMap = new HashMap<>();

        /*
         * 第一轮遍历历史消息：
         * 1. 校验每条消息数据合法性；
         * 2. 根据角色判断当前消息属于USER还是ASSISTANT；
         * 3. 按requestId分别建立索引。
         *
         * 这里不能在一次循环中直接生成Memory，因为当前遍历到USER时，对应ASSISTANT可能还没有出现，需要先完整扫描所有历史消息。
         */
        for (AgentChatMessageDTO chatMessageDTO : chatMessageDTOList) {
            // 防止非法消息进入Map，避免后续根据requestId匹配时出现空指针或者错误Memory。
            validateChatMessageDTO(chatMessageDTO);
            // 将数据库保存的角色编码转换为枚举，提高业务代码可读性，避免散落魔法数字。
            ChatMessageRoleEnum messageRole = ChatMessageRoleEnum.fromCode(chatMessageDTO.getMessageRoleCode());

            if (messageRole == ChatMessageRoleEnum.USER) {
                /*
                 * putIfAbsent只在requestId不存在时保存消息。
                 * 如果返回非null，说明当前requestId已经存在USER消息，即同一次Agent请求出现了重复用户输入，属于异常数据。
                 * 这里不能使用put，因为put会覆盖旧USER消息，导致历史数据问题被隐藏。
                 */
                AgentChatMessageDTO existingUserMessage = userMessageMap.putIfAbsent(chatMessageDTO.getRequestId(), chatMessageDTO);
                if (Objects.nonNull(existingUserMessage)) {
                    throw new IllegalStateException("同一requestId存在重复USER消息，requestId=%s".formatted(chatMessageDTO.getRequestId()));
                }
                // 当前消息已经完成USER处理，不应该继续进入ASSISTANT处理逻辑。
                continue;
            }
            /*
             * 非USER消息进入ASSISTANT Map。当前系统只有USER和ASSISTANT两种聊天角色，因此剩余情况默认为ASSISTANT。
             * 如果未来增加SYSTEM、TOOL等角色，需要增加明确判断，避免错误进入这里。
             */
            AgentChatMessageDTO existingAssistantMessage = assistantMessageMap.putIfAbsent(chatMessageDTO.getRequestId(), chatMessageDTO);

            if (Objects.nonNull(existingAssistantMessage)) {
                throw new IllegalStateException("同一requestId存在重复ASSISTANT消息，requestId=%s".formatted(chatMessageDTO.getRequestId()));
            }
        }
        // 保存最终提供给模型上下文的完整问答消息。
        List<AgentMemoryMessage> memoryMessageList = new ArrayList<>();

        /*
         * 第二轮遍历USER消息。
         * 为什么遍历USER Map而不是原始列表？因为USER消息代表一轮对话的起点，模型Memory需要按照用户提问顺序组织：
         * USER-A → ASSISTANT-A → USER-B → ASSISTANT-B。
         * ASSISTANT完成时间可能因为模型耗时不同发生乱序，所以不能按照数据库sequenceNo机械排列。
         */
        for (Map.Entry<String, AgentChatMessageDTO> userEntry : userMessageMap.entrySet()) {
            // Entry中的key就是requestId，value就是对应的USER消息。
            AgentChatMessageDTO userMessageDTO = userEntry.getValue();
            /*
             * 使用同一个requestId到assistantMessageMap中寻找对应回答。
             * 例如：userEntry.key = request-A
             * assistantMessageMap.get(request-A)
             * 可以找到对应的模型回答。
             */
            AgentChatMessageDTO assistantMessageDTO = assistantMessageMap.get(userEntry.getKey());
            /*
             * USER存在但是ASSISTANT不存在，说明这一轮可能：
             * 1. 模型调用失败；
             * 2. 查询历史时窗口截断；
             * 3. 回答仍未生成。
             * 该消息仍然保留在数据库历史中用于审计，
             * 但是不能进入模型Memory，否则模型可能误认为这是需要继续回答的问题。
             */
            if (Objects.isNull(assistantMessageDTO)) {
                continue;
            }

            /*
             * 校验消息顺序。
             * 正常情况：
             * USER sequenceNo=10
             * ASSISTANT sequenceNo=11
             * 如果ASSISTANT早于USER，说明数据库消息关联关系异常，不能把错误上下文发送给模型。
             */
            if (assistantMessageDTO.getSequenceNo() <= userMessageDTO.getSequenceNo()) {
                throw new IllegalStateException("ASSISTANT消息顺序不能早于对应USER消息，requestId=%s".formatted(userEntry.getKey()));
            }
            // 按模型要求的标准聊天格式加入Memory：用户问题在前，AI回答在后。
            memoryMessageList.add(convertToMemoryMessage(userMessageDTO, AgentMemoryMessageRoleEnum.USER));
            // 将对应回答加入Memory，形成完整一次问答轮次。
            memoryMessageList.add(convertToMemoryMessage(assistantMessageDTO, AgentMemoryMessageRoleEnum.ASSISTANT));
        }
        // 返回不可修改列表，避免后续调用链意外修改已经整理好的模型上下文。
        return List.copyOf(memoryMessageList);
    }

    /**
     * 校验Chat History消息DTO核心字段。
     *
     * @param chatMessageDTO 待校验历史消息
     */
    private void validateChatMessageDTO(AgentChatMessageDTO chatMessageDTO) {
        if (Objects.isNull(chatMessageDTO)) {
            throw new IllegalStateException("Chat History消息列表不能包含空元素");
        }

        if (StringUtils.isBlank(chatMessageDTO.getRequestId())) {
            throw new IllegalStateException("Chat History消息requestId不能为空");
        }

        if (Objects.isNull(chatMessageDTO.getMessageRoleCode())) {
            throw new IllegalStateException("Chat History消息角色码不能为空");
        }

        if (StringUtils.isBlank(chatMessageDTO.getMessageContent())) {
            throw new IllegalStateException("Chat History消息正文不能为空");
        }

        if (Objects.isNull(chatMessageDTO.getSequenceNo()) || chatMessageDTO.getSequenceNo() <= 0) {
            throw new IllegalStateException("Chat History消息顺序必须大于0");
        }
    }

    /**
     * 将Chat History消息转换成Agent Core Memory消息。
     *
     * @param chatMessageDTO Chat History消息DTO
     * @param memoryMessageRole Memory消息角色
     * @return 中立的Agent Memory消息
     */
    private AgentMemoryMessage convertToMemoryMessage(AgentChatMessageDTO chatMessageDTO, AgentMemoryMessageRoleEnum memoryMessageRole) {

        return AgentMemoryMessage.create(chatMessageDTO.getRequestId(), memoryMessageRole,
                chatMessageDTO.getMessageContent(), chatMessageDTO.getSequenceNo());
    }
}
