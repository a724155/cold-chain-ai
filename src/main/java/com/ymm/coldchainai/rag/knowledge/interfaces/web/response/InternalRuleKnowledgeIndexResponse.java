package com.ymm.coldchainai.rag.knowledge.interfaces.web.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内部规范知识索引本地验证响应。
 *
 * <p>该响应只服务于local环境RAG建设验证，不属于正式用户业务接口。</p>
 *
 * <p><strong>接口协议提醒：</strong>
 * 后续若该能力升级成正式运营后台接口，需要与产品确认重建权限、
 * 文档版本、异步任务状态、失败重试以及重复提交等规则，不能直接沿用当前local验证协议。</p>
 */
@Getter
@AllArgsConstructor(staticName = "of")
public class InternalRuleKnowledgeIndexResponse {

    /**
     * 文档编码。
     */
    private final String documentCode;

    /**
     * 文档版本。
     */
    private final String documentVersion;

    /**
     * 本次实际写入向量数据库的Chunk数量。
     */
    private final Integer chunkCount;
}
