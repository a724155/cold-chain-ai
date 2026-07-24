package com.ymm.coldchainai.rag.knowledge.application.enumtype;

import com.ymm.coldchainai.shared.exception.code.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * RAG知识库模块错误码枚举。
 *
 * <p>该枚举统一管理知识文档读取、切片、向量化和检索过程中的错误，
 * 避免后续PDF Loader、VectorStore和Retriever分别维护错误码。</p>
 */
@Getter
@AllArgsConstructor
public enum RagErrorCodeEnum implements IErrorCode {

    /**
     * RAG文档读取或切片相关配置不合法。
     */
    RAG_DOCUMENT_CONFIGURATION_ERROR(53000, "RAG文档配置错误"),

    /**
     * PDF不存在、无法读取或者没有解析出有效文本。
     */
    RAG_DOCUMENT_LOAD_ERROR(53001, "RAG文档读取失败"),

    /**
     * PGVector数据源、向量维度、Schema或者表配置不合法。
     */
    RAG_VECTOR_STORE_CONFIGURATION_ERROR(53002, "RAG向量存储配置错误"),

    /**
     * PostgreSQL连接或者PGVector基础设施初始化失败。
     */
    RAG_VECTOR_STORE_INITIALIZATION_ERROR(53003, "RAG向量存储初始化失败"),

    /**
     * PDF Chunk生成Embedding或者写入PGVector过程中发生异常。
     */
    RAG_KNOWLEDGE_INDEX_ERROR(53004, "RAG知识索引构建失败");

    /**
     * RAG模块错误编码。
     */
    private final Integer code;

    /**
     * RAG模块默认错误信息。
     */
    private final String message;
}
