package com.ymm.coldchainai.rag.knowledge.interfaces.web;

import com.ymm.coldchainai.rag.knowledge.application.dto.InternalRuleKnowledgeIndexDTO;
import com.ymm.coldchainai.rag.knowledge.application.service.IInternalRuleKnowledgeIndexService;
import com.ymm.coldchainai.rag.knowledge.interfaces.web.response.InternalRuleKnowledgeIndexResponse;
import com.ymm.coldchainai.shared.response.YmmResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
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
}
