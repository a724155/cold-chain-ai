package com.ymm.coldchainai.rag.knowledge.interfaces.web;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeAnswerDTO;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeIndexDTO;
import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeSearchDTO;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeAnswerQuery;
import com.ymm.coldchainai.rag.knowledge.application.model.InternalRuleKnowledgeSearchQuery;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeAnswerService;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeIndexService;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeSearchService;
import com.ymm.coldchainai.rag.knowledge.interfaces.web.request.InternalRuleKnowledgeAnswerRequest;
import com.ymm.coldchainai.rag.knowledge.interfaces.web.request.InternalRuleKnowledgeSearchRequest;
import com.ymm.coldchainai.rag.knowledge.interfaces.web.response.InternalRuleKnowledgeAnswerResponse;
import com.ymm.coldchainai.rag.knowledge.interfaces.web.response.InternalRuleKnowledgeIndexResponse;
import com.ymm.coldchainai.rag.knowledge.interfaces.web.response.InternalRuleKnowledgeSearchResponse;
import com.ymm.coldchainai.shared.response.YmmResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部规范RAG本地验证Controller。
 *
 * <p>该接口只在local环境注册，用于研发阶段手动触发PDF重新读取、Chunk切片、Embedding生成以及PGVector入库。</p>
 *
 * <p>真正用户通过Agent问公司内部规范时不会直接调用该接口，后续会通过Retriever和RAG Advisor读取已经建立好的知识索引。</p>
 *
 * <p>在挖矿流程中，该Controller相当于研发人员使用的“重新整理地质资料库”按钮。</p>
 */
@RestController
@Profile("local")
@RequestMapping("/api/verification/rag/internal-rule")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class InternalRuleKnowledgeVerificationController {

    /**
     * 内部规范知识索引服务。
     */
    private final IInternalRuleKnowledgeIndexService internalRuleKnowledgeIndexService;

    /**
     * 内部规范向量知识检索服务。
     */
    private final IInternalRuleKnowledgeSearchService internalRuleKnowledgeSearchService;

    /**
     * 内部规范RAG问答服务。
     */
    private final IInternalRuleKnowledgeAnswerService internalRuleKnowledgeAnswerService;

    /**
     * 重新建立满帮内部规范向量索引。
     *
     * @return 本次知识索引构建结果
     */
    @PostMapping("/reindex")
    public YmmResult<InternalRuleKnowledgeIndexResponse> rebuildIndex() {
        // Application Service执行PDF读取、Embedding生成和PGVector入库完整流程。
        InternalRuleKnowledgeIndexDTO indexDTO = internalRuleKnowledgeIndexService.rebuildIndex();

        // Controller只负责转换验证接口返回对象，不参与Embedding和数据库操作。
        InternalRuleKnowledgeIndexResponse response = InternalRuleKnowledgeIndexResponse.of(
                indexDTO.getDocumentCode(),
                indexDTO.getDocumentVersion(),
                indexDTO.getChunkCount());

        return YmmResult.success(response);
    }

    /**
     * 根据自然语言问题检索最相关的内部规范Chunk。
     *
     * <p>当前接口只验证Retrieval阶段，不调用ChatModel，
     * 因此返回结果就是PGVector真实召回的知识原文和相似度Score。</p>
     *
     * @param request 内部规范检索请求
     * @return 按相似度排序的知识Chunk
     */
    @PostMapping("/search")
    public YmmResult<InternalRuleKnowledgeSearchResponse> search(@Valid @RequestBody InternalRuleKnowledgeSearchRequest request) {
        // Controller把HTTP请求转换成Application层标准查询对象。
        InternalRuleKnowledgeSearchQuery searchQuery = InternalRuleKnowledgeSearchQuery.create(request.getQuery());

        // Application Service执行问题Embedding和PGVector相似度查询。
        InternalRuleKnowledgeSearchDTO searchDTO = internalRuleKnowledgeSearchService.search(searchQuery);

        // 将Application DTO转换成local验证接口响应。
        InternalRuleKnowledgeSearchResponse response = InternalRuleKnowledgeSearchResponse.fromDTO(searchDTO);

        return YmmResult.success(response);
    }

    /**
     * 根据满帮内部规范知识库回答自然语言问题。
     *
     * <p>该接口会完成PGVector Retrieval以及ChatModel生成，
     * 用于验证完整RAG链路是否能够严格按照PDF内容回答。</p>
     *
     * @param request 内部规范问答请求
     * @return RAG最终答案
     */
    @PostMapping("/answer")
    public YmmResult<InternalRuleKnowledgeAnswerResponse> answer(@Valid @RequestBody InternalRuleKnowledgeAnswerRequest request) {
        // 将HTTP请求转换成Application层标准问答对象。
        InternalRuleKnowledgeAnswerQuery answerQuery = InternalRuleKnowledgeAnswerQuery.create(request.getQuestion());

        // Application Service通过QuestionAnswerAdvisor执行检索增强生成。
        InternalRuleKnowledgeAnswerDTO answerDTO = internalRuleKnowledgeAnswerService.answer(answerQuery);

        // Response转换方法自身负责DTO空值防御。
        InternalRuleKnowledgeAnswerResponse response = InternalRuleKnowledgeAnswerResponse.fromDTO(answerDTO);

        return YmmResult.success(response);
    }
}
