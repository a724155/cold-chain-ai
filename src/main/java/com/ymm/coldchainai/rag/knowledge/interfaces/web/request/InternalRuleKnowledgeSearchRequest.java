package com.ymm.coldchainai.rag.knowledge.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 内部规范知识检索验证请求。
 *
 * <p>当前接口只用于local环境观察向量检索结果，
 * 用户只能提供自然语言问题，不能通过接口修改topK、相似度阈值、
 * documentCode或者documentVersion。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 若未来升级成正式知识检索API，需要与产品确认查询长度、
 * 权限范围、知识库范围和返回Chunk是否允许直接展示给前端。</p>
 */
@Getter
@Setter
public class InternalRuleKnowledgeSearchRequest {

    /**
     * 需要从满帮内部规范中检索的自然语言问题。
     */
    @NotBlank(message = "内部规范检索问题不能为空")
    @Size(max = 500, message = "内部规范检索问题长度不能超过500个字符")
    private String query;
}
