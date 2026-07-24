package com.ymm.coldchainai.rag.knowledge.application.model;

import com.ymm.coldchainai.rag.knowledge.application.enumtype.RagErrorCodeEnum;
import com.ymm.coldchainai.shared.exception.BusinessException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 满帮内部规范知识检索查询对象。
 *
 * <p>该对象是Application层真正接受的标准查询参数，Controller Request不能直接进入基础设施检索层。</p>
 *
 * <p>在挖矿流程中，该对象相当于项目经理整理后的标准资料查询单：外部用户只负责提出问题，系统先把问题整理成合法任务，再交给向量档案库检索。</p>
 */
@Getter
public class InternalRuleKnowledgeSearchQuery {

    /**
     * 用户需要从内部规范中查询的问题。
     */
    private final String query;

    /**
     * 创建合法的内部规范知识查询。
     *
     * @param query 用户问题
     * @return 已完成基础校验的查询对象
     */
    private InternalRuleKnowledgeSearchQuery(String query) {
        this.query = query;
    }

    /**
     * 创建内部规范知识检索查询。
     *
     * @param query 用户问题
     * @return 合法查询对象
     */
    public static InternalRuleKnowledgeSearchQuery create(String query) {
        if (StringUtils.isBlank(query)) {
            throw new BusinessException(RagErrorCodeEnum.RAG_KNOWLEDGE_SEARCH_PARAMETER_ERROR, "内部规范检索问题不能为空");
        }

        return new InternalRuleKnowledgeSearchQuery(StringUtils.trim(query));
    }
}
