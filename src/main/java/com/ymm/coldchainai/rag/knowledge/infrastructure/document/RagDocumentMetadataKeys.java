package com.ymm.coldchainai.rag.knowledge.infrastructure.document;

import lombok.experimental.UtilityClass;

/**
 * RAG Document Metadata统一字段名称。
 *
 * <p>后续文档入库、向量检索、知识来源展示和版本过滤都会依赖这些Metadata，
 * 因此统一维护，禁止不同组件各自手写documentCode、documentVersion等字符串。</p>
 *
 * <p>在挖矿流程中，该类相当于档案仓库统一的标签规范：每块知识原料都必须按照相同格式标记来源、版本和编号，
 * 否则后续检索设备无法准确判断知识来自哪份档案。</p>
 */
@UtilityClass
public class RagDocumentMetadataKeys {

    /**
     * 稳定文档编码。
     */
    public static final String DOCUMENT_CODE = "document_code";

    /**
     * 文档展示名称。
     */
    public static final String DOCUMENT_NAME = "document_name";

    /**
     * 文档版本。
     */
    public static final String DOCUMENT_VERSION = "document_version";

    /**
     * 知识文档业务类型。
     */
    public static final String KNOWLEDGE_TYPE = "knowledge_type";

    /**
     * 当前Chunk在本次切片结果中的顺序。
     */
    public static final String CHUNK_INDEX = "chunk_index";
}