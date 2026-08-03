package com.ymm.coldchainai.agent.scenario.knowledge.interfaces.tool;

import com.ymm.coldchainai.agent.audit.application.command.StartToolExecutionAuditCommand;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionAuditDTO;
import com.ymm.coldchainai.agent.audit.application.service.IToolExecutionAuditApplicationService;
import com.ymm.coldchainai.agent.core.application.enumtype.AgentErrorCodeEnum;
import com.ymm.coldchainai.agent.core.infrastructure.tool.AgentToolContextKeys;
import com.ymm.coldchainai.agent.scenario.knowledge.interfaces.tool.response.InternalRuleKnowledgeQueryToolResponse;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchDTO;
import com.ymm.coldchainai.rag.knowledge.application.enumtype.RagErrorCodeEnum;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeSearchQuery;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeSearchService;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

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
 * <p><strong>审计与敏感数据说明：</strong>
 * Tool审计表只保存问题长度、检索参数、结果数量和文档版本，禁止保存用户完整问题和PGVector召回的公司内部规范原文。</p>
 *
 * <p>在挖矿流程中，该Tool相当于总调度员手里的“内部资料查询电话”：
 * 当客户询问公司规则时，总调度员才拨打这部电话；询问司机订单或者支付状态时，则使用其他业务设备。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InternalRuleKnowledgeQueryTool {

    /**
     * 内部规范知识查询Tool稳定名称。
     */
    private static final String TOOL_NAME = "query_internal_rules";

    /**
     * ToolContext缺少安全信息时使用的系统异常说明。
     */
    private static final String TOOL_CONTEXT_ERROR_MESSAGE = "内部规范知识Tool缺少受信任调用上下文";

    /**
     * 内部规范知识检索Application Service。
     */
    private final IInternalRuleKnowledgeSearchService internalRuleKnowledgeSearchService;

    /**
     * Tool执行审计Application Service。
     *
     * <p>负责在真实Embedding和PGVector检索前后，通过独立短事务保存Tool执行状态。</p>
     */
    private final IToolExecutionAuditApplicationService toolExecutionAuditApplicationService;

    /**
     * 根据自然语言问题查询满帮集团内部规范知识库。
     *
     * <p>该Tool只适用于公司内部制度、考勤、资产管理、Git规范、事故等级以及其他明确属于内部规范文档的问题。</p>
     *
     * <p>ToolContext是Spring AI提供的特殊运行时参数，
     * 不会出现在模型需要生成的Tool JSON参数中。模型仍然只需要提供question。</p>
     *
     * @param question 用户需要从内部规范中查询的问题
     * @param toolContext 后端注入的受信任Tool上下文，不由模型生成
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
            @ToolParam(description = "需要从满帮集团内部规范知识库中查询的完整自然语言问题") String question, ToolContext toolContext) {

        // RAG Tool现在也读取后端ToolContext，用于保存可信用户、租户、requestId和agentCode审计信息。
        Map<String, Object> toolContextMap = resolveToolContextMap(toolContext);

        // 虽然内部规范知识当前对租户共享，审计仍然必须记录具体访问租户。
        Long currentTenantId = resolveRequiredLong(toolContextMap, AgentToolContextKeys.CURRENT_TENANT_ID, "当前租户ID");

        // 记录实际发起公司内部规范查询的认证用户。
        Long currentUserId = resolveRequiredLong(toolContextMap, AgentToolContextKeys.CURRENT_USER_ID, "当前用户ID");

        // requestId用于关联本轮AgentExecution和后续全部Tool调用记录。
        String requestId = resolveRequiredString(toolContextMap, AgentToolContextKeys.REQUEST_ID, "requestId");

        // agentCode用于记录哪一个Agent调阅了内部规范知识。
        String agentCode = resolveRequiredString(toolContextMap, AgentToolContextKeys.AGENT_CODE, "agentCode");

        // 输入摘要只记录问题长度。StringUtils.length()对null安全，question为null时返回0，不会产生空指针。
        String inputSummary = buildInputSummary(question);

        // 先在独立短事务中保存RUNNING。审计写入失败时不会继续执行Embedding和PGVector检索。
        ToolExecutionAuditDTO auditDTO = toolExecutionAuditApplicationService.startExecution(
                StartToolExecutionAuditCommand.create(requestId, agentCode, TOOL_NAME, currentUserId, currentTenantId, inputSummary));

        try {
            /*
             * Tool层首先校验模型传入的问题，防止无效参数继续进入RAG Application链路。
             * 这里抛出BusinessException而不是IllegalArgumentException：
             * 空问题属于可预期参数错误，需要进入后面的BusinessException分支，使用参数错误码更新FAILED审计，而不能被伪装成Embedding或PGVector系统故障。
             */
            if (StringUtils.isBlank(question)) {
                throw new BusinessException(RagErrorCodeEnum.RAG_KNOWLEDGE_SEARCH_PARAMETER_ERROR, "内部规范知识查询Tool问题不能为空");
            }

            // 将模型Tool参数转换成RAG Application层标准查询对象。SearchQuery负责正式业务参数校验。空问题会抛出RAG模块BusinessException，并进入FAILED审计链路。
            InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create(question);

            log.info("Agent调用内部规范知识查询Tool，toolExecutionId={}，requestId={}，currentUserId={}，currentTenantId={}，questionLength={}",
                    auditDTO.getToolExecutionId(), requestId, currentUserId, currentTenantId, searchQuery.getQuery().length());

            /*
             * Application Service负责真正执行：
             * 问题Embedding → PGVector → Metadata Filter → TopK知识Chunk。
             * 当前调用发生在RUNNING审计事务提交以后，不会长期占用MySQL审计事务。
             */
            InternalRuleKnowledgeSearchDTO searchDTO = internalRuleKnowledgeSearchService.search(searchQuery);

            // Tool Response自身完成DTO和结果列表的防御性转换。
            InternalRuleKnowledgeQueryToolResponse response = InternalRuleKnowledgeQueryToolResponse.fromDTO(searchDTO);

            // 成功摘要只保存检索参数和召回数量。PGVector返回的内部规范正文只交给模型使用，不复制进MySQL审计表。
            toolExecutionAuditApplicationService.markSucceeded(auditDTO, buildSuccessOutputSummary(searchDTO, response));

            log.info("Agent内部规范知识查询Tool完成，toolExecutionId={}，requestId={}，questionLength={}，resultCount={}，documentCode={}，documentVersion={}",
                    auditDTO.getToolExecutionId(), requestId, searchQuery.getQuery().length(), response.getResultCount(),
                    response.getDocumentCode(), response.getDocumentVersion());

            return response;
        } catch (BusinessException exception) {
            // 空问题等RAG参数错误属于可预期业务失败。当前Tool Response没有失败协议字段，因此审计完成后继续抛出原BusinessException，不擅自修改已有Tool返回协议。
            toolExecutionAuditApplicationService.markFailed(auditDTO, exception.getCode(), exception.getMessage());

            log.warn("内部规范知识查询Tool业务失败，toolExecutionId={}，requestId={}，questionLength={}，code={}，message={}",
                    auditDTO.getToolExecutionId(), requestId, StringUtils.length(question), exception.getCode(), exception.getMessage());

            throw exception;
        } catch (RuntimeException exception) {
            // Embedding、PGVector、DTO转换或者审计终态更新异常属于系统故障。尝试登记FAILED后继续抛出原异常，由Agent执行链统一处理。
            markUnexpectedExecutionFailed(auditDTO, exception);
            throw exception;
        }
    }

    /**
     * 构建内部规范Tool入参安全摘要。
     *
     * <p>禁止将question原文写入MySQL审计表。
     * 公司内部问题可能包含制度名称、业务细节或者尚未公开的信息，
     * 审计只需要记录问题长度即可证明本次调用存在有效查询内容。</p>
     *
     * @param question 模型传入的内部规范问题
     * @return 不包含问题原文的安全输入摘要
     */
    private String buildInputSummary(String question) {
        // StringUtils.length()能够安全处理null，null问题长度按0记录。
        int questionLength = StringUtils.length(question);

        // String.formatted()是JDK 15新增方法，用于根据模板生成审计摘要。
        return "questionLength=%s".formatted(questionLength);
    }

    /**
     * 构建内部规范Tool成功输出安全摘要。
     *
     * <p>该摘要不会读取resultItemList中的content字段，
     * 因此不会把公司制度原文复制进Tool审计表。</p>
     *
     * @param searchDTO RAG Application层检索结果
     * @param response Agent Tool结构化响应
     * @return 可安全保存的RAG检索结果摘要
     */
    private String buildSuccessOutputSummary(
            InternalRuleKnowledgeSearchDTO searchDTO,
            InternalRuleKnowledgeQueryToolResponse response) {

        if (Objects.isNull(searchDTO)) {
            throw new IllegalArgumentException("内部规范知识检索DTO不能为空");
        }

        if (Objects.isNull(response)) {
            throw new IllegalArgumentException("内部规范知识Tool响应不能为空");
        }

        // 没有召回知识片段时，文档编码和版本可能为空，摘要使用明确文字表示。
        String documentCode = StringUtils.defaultIfBlank(response.getDocumentCode(), "无召回文档");
        String documentVersion = StringUtils.defaultIfBlank(response.getDocumentVersion(), "无召回版本");

        /*
         * 只保存TopK、相似度阈值、召回数量和文档版本。
         * 不访问response.getResultItemList()，避免错误读取并保存内部知识原文。
         */
        return "topK=%s，similarityThreshold=%s，resultCount=%s，documentCode=%s，documentVersion=%s"
                .formatted(
                        searchDTO.getTopK(),
                        searchDTO.getSimilarityThreshold(),
                        response.getResultCount(),
                        documentCode,
                        documentVersion);
    }

    /**
     * 处理内部规范Tool执行过程中的非预期系统异常。
     *
     * <p>RAG执行失败统一使用RAG_KNOWLEDGE_SEARCH_ERROR登记，
     * 但审计表只保存安全错误信息，不保存PGVector连接地址、SQL或者异常堆栈。</p>
     *
     * @param auditDTO 当前Tool审计凭证
     * @param originalException 最初导致RAG Tool失败的异常
     */
    private void markUnexpectedExecutionFailed(ToolExecutionAuditDTO auditDTO, RuntimeException originalException) {
        try {
            // 使用RAG模块现有错误码，禁止在Tool中重新定义53005魔法数字。
            toolExecutionAuditApplicationService.markFailed(auditDTO, RagErrorCodeEnum.RAG_KNOWLEDGE_SEARCH_ERROR.getCode(),
                    RagErrorCodeEnum.RAG_KNOWLEDGE_SEARCH_ERROR.getMessage());
        } catch (RuntimeException auditException) {
            // 保留原始RAG异常，并把审计失败作为suppressed附加异常。
            originalException.addSuppressed(auditException);
            log.error("内部规范知识Tool失败审计更新异常，toolExecutionId={}，requestId={}",
                    auditDTO.getToolExecutionId(), auditDTO.getRequestId(), auditException);
        }
    }

    /**
     * 安全获取ToolContext中的上下文Map。
     *
     * @param toolContext Spring AI Tool执行上下文
     * @return 非空Tool上下文Map
     */
    private Map<String, Object> resolveToolContextMap(ToolContext toolContext) {
        // ToolContext不存在时不允许继续读取内部规范，避免产生无法归属到用户的知识访问记录。
        Map<String, Object> toolContextMap = Objects.isNull(toolContext) ? null : toolContext.getContext();

        // MapUtils.isEmpty能够同时校验null和空Map。
        if (MapUtils.isEmpty(toolContextMap)) {
            throw createToolContextException("ToolContext为空");
        }

        return toolContextMap;
    }

    /**
     * 从ToolContext中读取必填Long字段。
     *
     * @param toolContextMap Tool上下文Map
     * @param contextKey 上下文字段名称
     * @param fieldName 异常信息使用的字段说明
     * @return 大于0的Long值
     */
    private Long resolveRequiredLong(Map<String, Object> toolContextMap, String contextKey, String fieldName) {
        // MapUtils.getLong能够安全处理字段缺失和常见Number类型转换。
        Long contextValue = MapUtils.getLong(toolContextMap, contextKey);

        if (Objects.isNull(contextValue) || contextValue <= 0L) {
            throw createToolContextException("%s不能为空且必须大于0".formatted(fieldName));
        }

        return contextValue;
    }

    /**
     * 从ToolContext中读取必填字符串字段。
     *
     * @param toolContextMap Tool上下文Map
     * @param contextKey 上下文字段名称
     * @param fieldName 异常信息使用的字段说明
     * @return 非空字符串值
     */
    private String resolveRequiredString(Map<String, Object> toolContextMap, String contextKey, String fieldName) {
        // requestId和agentCode必须来自Executor注入的受信任上下文。
        String contextValue = MapUtils.getString(toolContextMap, contextKey);

        if (StringUtils.isBlank(contextValue)) {
            throw createToolContextException("%s不能为空".formatted(fieldName));
        }

        return contextValue;
    }

    /**
     * 创建ToolContext系统异常。
     *
     * @param detailMessage 具体上下文错误说明
     * @return ToolContext系统异常
     */
    private IllegalStateException createToolContextException(String detailMessage) {
        // 缺失身份上下文属于Agent执行安全链路故障，不属于RAG业务参数错误。
        String errorMessage = "%s：%s，%s".formatted(
                AgentErrorCodeEnum.AGENT_EXECUTION_ERROR.getMessage(),
                TOOL_CONTEXT_ERROR_MESSAGE,
                detailMessage);

        return new IllegalStateException(errorMessage);
    }

}
