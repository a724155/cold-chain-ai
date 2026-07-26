package com.ymm.coldchainai.rag.knowledge.interfaces.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 满帮内部规范RAG问答验证请求。
 *
 * <p><strong>接口协议提醒：</strong>
 * 正式产品化前需要与产品确认问题最大长度、知识权限、
 * 无答案提示、是否允许展示知识来源以及兼容策略。</p>
 */
@Getter
@Setter
public class InternalRuleKnowledgeAnswerRequest {

    /**
     * 用户需要根据内部规范回答的问题。
     */
    @NotBlank(message = "内部规范问答问题不能为空")
    @Size(max = 500, message = "内部规范问答问题长度不能超过500个字符")
    private String question;
}