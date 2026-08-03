package com.ymm.coldchainai.agent.audit.application.service;

import com.ymm.coldchainai.agent.audit.application.command.QueryToolExecutionAuditDetailCommand;
import com.ymm.coldchainai.agent.audit.application.command.QueryToolExecutionAuditListCommand;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionRecordDTO;
import com.ymm.coldchainai.agent.audit.application.dto.ToolExecutionRecordListDTO;

/**
 * Tool执行审计查询Application Service。
 *
 * <p>该服务只负责审计数据读取，不修改Tool执行状态。</p>
 */
public interface IToolExecutionAuditQueryApplicationService {

    /**
     * 根据requestId查询当前用户和租户拥有的Tool执行记录。
     *
     * @param command Tool审计列表查询命令
     * @return Tool执行审计列表，未调用Tool时返回空列表
     */
    ToolExecutionRecordListDTO listByRequestId(QueryToolExecutionAuditListCommand command);

    /**
     * 根据toolExecutionId查询当前用户和租户有权访问的单次审计详情。
     *
     * <p>记录不存在和无权访问使用相同业务异常，
     * 避免泄露其他用户的Tool执行记录是否存在。</p>
     *
     * @param command Tool审计详情查询命令
     * @return 单次Tool执行审计详情
     */
    ToolExecutionRecordDTO getByToolExecutionId(QueryToolExecutionAuditDetailCommand command);
}
