package com.ymm.coldchainai.rag.knowledge.infrastructure.answer;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeAnswerDTO;
import com.ymm.coldchainai.rag.knowledge.application.enumtype.RagErrorCodeEnum;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeAnswerQuery;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeAnswerService;
import com.ymm.coldchainai.rag.knowledge.infrastructure.config.InternalRuleRagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 满帮内部规范RAG问答服务实现。
 *
 * <p>该组件不手动查询PGVector，也不手动拼Document文本。
 * QuestionAnswerAdvisor会在ChatClient调用链中自动完成知识检索和Prompt增强。</p>
 *
 * <p>在挖矿流程中，该组件相当于最终的资料分析员：
 * 用户提出问题以后，资料员先通过Advisor取得相关地质档案，
 * 再让智能分析设备严格根据档案给出结论。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InternalRuleKnowledgeAnswerServiceImpl implements IInternalRuleKnowledgeAnswerService {

    /**
     * 满帮内部规范专属RAG ChatClient。
     */
    private final ChatClient internalRuleRagChatClient;

    /**
     * 当前内部规范文档配置。
     */
    private final InternalRuleRagProperties internalRuleRagProperties;

    /**
     * 根据内部规范知识库生成最终答案。
     *
     * @param answerQuery 已完成参数校验的问答查询
     * @return RAG自然语言答案
     */
    @Override
    public InternalRuleKnowledgeAnswerDTO answer(InternalRuleKnowledgeAnswerQuery answerQuery) {
        if (Objects.isNull(answerQuery)) {
            throw new IllegalArgumentException("内部规范RAG问答查询对象不能为空");
        }

        long startTimeMillis = System.currentTimeMillis();

        try {
            log.info("内部规范RAG问答开始，questionLength={}，documentCode={}，documentVersion={}",
                    answerQuery.getQuestion().length(), internalRuleRagProperties.getDocumentCode(), internalRuleRagProperties.getDocumentVersion());

            /*
             * 这里仍然使用同步call()。真正执行顺序不是“直接问模型”，而是：
             * QuestionAnswerAdvisor检索PGVector→ 将知识Chunk加入Prompt→ ChatModel生成最终答案。
             */
            String answer = internalRuleRagChatClient.prompt()
                    .user(answerQuery.getQuestion())
                    .call()
                    .content();

            if (StringUtils.isBlank(answer)) {
                throw new IllegalStateException("内部规范RAG模型返回内容为空");
            }

            long costMillis = System.currentTimeMillis() - startTimeMillis;

            log.info("内部规范RAG问答成功，questionLength={}，answerLength={}，costMillis={}",
                    answerQuery.getQuestion().length(), answer.length(), costMillis);

            return InternalRuleKnowledgeAnswerDTO.of(
                    answerQuery.getQuestion(),
                    answer,
                    internalRuleRagProperties.getDocumentCode(),
                    internalRuleRagProperties.getDocumentVersion());
        } catch (Exception exception) {
            long costMillis = System.currentTimeMillis() - startTimeMillis;
            log.error("内部规范RAG问答失败，questionLength={}，costMillis={}", answerQuery.getQuestion().length(), costMillis, exception);
            throw createKnowledgeAnswerException(exception);
        }
    }

    /**
     * 创建RAG知识问答系统异常。
     *
     * @param cause 原始异常
     * @return RAG问答异常
     */
    private IllegalStateException createKnowledgeAnswerException(Throwable cause) {
        String errorMessage = "%s：内部规范知识检索或模型回答失败".formatted(RagErrorCodeEnum.RAG_KNOWLEDGE_ANSWER_ERROR.getMessage());
        return new IllegalStateException(errorMessage, cause);
    }
}
