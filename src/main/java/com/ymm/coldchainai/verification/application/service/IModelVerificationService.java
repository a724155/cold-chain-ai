package com.ymm.coldchainai.verification.application.service;

/**
 * 普通模型调用验证服务。
 *
 * <p>该服务只用于第一阶段验证 Spring AI 和 OpenAI Compatible 模型是否连通，
 * 不代表正式 Agent 的应用服务设计。</p>
 */
public interface IModelVerificationService {

    /**
     * 将用户问题发送给基础模型并返回完整答案。
     *
     * @param question 用户提交的问题
     * @return 模型完整回答
     */
    String chat(String question);
}
