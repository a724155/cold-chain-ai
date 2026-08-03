package com.ymm.coldchainai.agent.audit.application.service;

import com.ymm.coldchainai.agent.audit.application.command.StartToolExecutionAuditCommand;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionAuditDTO;

/**
 * Agent Tool执行审计Application Service。
 *
 * <p>该服务负责把一次Tool调用拆分成三个独立短事务：</p>
 *
 * <p>1. startExecution：保存RUNNING审计记录；</p>
 * <p>2. markSucceeded：更新为SUCCEEDED；</p>
 * <p>3. markFailed：更新为FAILED。</p>
 *
 * <p>真实Tool业务必须执行在这些短事务之间，
 * 不能把订单查询、支付查询、RAG检索或者远程RPC包在审计数据库事务中。</p>
 *
 * <p>在挖矿流程中，该接口相当于外协设备审计中心：
 * 设备开工、成功和失败分别登记，但审计中心不会在设备整个作业期间一直占用数据库事务。</p>
 */
public interface IToolExecutionAuditApplicationService {

    /**
     * 在独立短事务中保存RUNNING Tool审计记录。
     *
     * @param command Tool审计开始命令
     * @return 后续成功或失败登记需要使用的执行凭证
     */
    ToolExecutionAuditDTO startExecution(StartToolExecutionAuditCommand command);

    /**
     * 在独立短事务中将Tool审计更新为SUCCEEDED。
     *
     * @param auditDTO RUNNING Tool审计凭证
     * @param outputSummary Tool输出安全摘要
     */
    void markSucceeded(ToolExecutionAuditDTO auditDTO, String outputSummary);

    /**
     * 在独立短事务中将Tool审计更新为FAILED。
     *
     * @param auditDTO RUNNING Tool审计凭证
     * @param errorCode Tool执行失败错误码
     * @param errorMessage Tool执行失败安全错误信息
     */
    void markFailed(ToolExecutionAuditDTO auditDTO, Integer errorCode, String errorMessage);
}
