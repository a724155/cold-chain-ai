package com.ymm.coldchainai.agent.scenario.knowledge.interfaces.tool;

import com.ymm.coldchainai.agent.scenario.knowledge.interfaces.tool.response.InternalRuleKnowledgeQueryToolResponse;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchDTO;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeSearchQuery;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 满帮内部规范知识查询Tool。
 *
 * <p>该Tool把已经建设完成的RAG Retrieval能力暴露给cold-chain-general Agent，
 * 让模型能够根据用户问题决定是否查询公司内部知识库。</p>
 *
 * <p>Tool本身不直接操作PGVector，不调用JdbcTemplate，也不负责Embedding。
 * 它只调用本地Application Service，保持：</p>
 *
 * <p>Agent → Tool → Application Service → VectorStore。</p>
 *
 * <p>在挖矿流程中，该Tool相当于总调度员手里的“内部资料查询电话”：
 * 当客户询问公司规则时，总调度员才拨打这部电话；询问司机订单或者支付状态时，则使用其他业务设备。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InternalRuleKnowledgeQueryTool {

    /**
     * 内部规范知识检索Application Service。
     */
    private final IInternalRuleKnowledgeSearchService internalRuleKnowledgeSearchService;

    /**
     * 根据自然语言问题查询满帮集团内部规范知识库。
     *
     * <p>该Tool只适用于公司内部制度、考勤、资产管理、Git规范、
     * 事故等级以及其他明确属于内部规范文档的问题。</p>
     *
     * @param question 用户需要从内部规范中查询的问题
     * @return PGVector召回的可信知识原文
     */
    @Tool(
            name = "query_internal_rules",
            description = """
                    查询满帮集团内部规范知识库。
                    仅当用户询问公司内部制度、考勤规则、上下班时间、工作日安排、公司资产管理、
                    Git或master分支规范、事故等级、资损事故认定等内部规则时调用。
                    不得用于查询司机成交订单、订单定金支付状态或者普通常识问题。
                    Tool返回的是内部规范原文，最终回答必须严格依据返回原文；
                    如果返回的知识片段无法明确支持用户问题，不得编造公司规则。
                    """)
    public InternalRuleKnowledgeQueryToolResponse queryInternalRules(
            @ToolParam(description = "需要从满帮集团内部规范知识库中查询的完整自然语言问题") String question) {

        if (StringUtils.isBlank(question)) {
            throw new IllegalArgumentException("内部规范知识查询Tool问题不能为空");
        }

        // 日志只记录问题长度，不直接记录完整内部问题内容，避免后续真实公司知识查询产生不必要的敏感日志。
        log.info("Agent调用内部规范知识查询Tool，questionLength={}", question.length());

        // 将模型Tool参数转换成RAG Application层标准查询对象。
        InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create(question);

        /*
         * Application Service负责真正执行：
         * 问题Embedding → PGVector → Metadata Filter → TopK知识Chunk。
         * Tool层不能直接访问VectorStore。
         */
        InternalRuleKnowledgeSearchDTO searchDTO = internalRuleKnowledgeSearchService.search(searchQuery);

        // Tool Response自身完成DTO和结果列表的防御性转换。
        InternalRuleKnowledgeQueryToolResponse response = InternalRuleKnowledgeQueryToolResponse.fromDTO(searchDTO);

        log.info("Agent内部规范知识查询Tool执行完成，questionLength={}，resultCount={}", question.length(), response.getResultCount());

        return response;
    }
}
