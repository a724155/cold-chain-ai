CREATE TABLE cold_chain_ai_tool_execution
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库内部自增主键',
    tool_execution_id   VARCHAR(64)      NOT NULL COMMENT '单次Tool执行业务唯一标识',
    request_id          VARCHAR(64)      NOT NULL COMMENT '所属Agent请求标识，一次Agent请求可以调用多个Tool',
    agent_code          VARCHAR(64)      NOT NULL COMMENT '发起本次Tool调用的Agent稳定编码',
    tool_name           VARCHAR(128)     NOT NULL COMMENT 'Spring AI Tool稳定名称',
    current_user_id     BIGINT           NOT NULL COMMENT '发起Agent请求的受信任用户ID',
    current_tenant_id   BIGINT           NOT NULL COMMENT '发起Agent请求的受信任租户ID',
    input_summary       VARCHAR(1024)    NOT NULL COMMENT 'Tool入参安全摘要，不保存敏感原始报文',
    output_summary      VARCHAR(1024)             DEFAULT NULL COMMENT 'Tool结果安全摘要，不保存完整业务数据',
    execution_status    TINYINT          NOT NULL COMMENT '执行状态：10-RUNNING，20-SUCCEEDED，30-FAILED',
    error_code          INT                       DEFAULT NULL COMMENT 'Tool执行失败错误码',
    error_message       VARCHAR(512)              DEFAULT NULL COMMENT 'Tool执行失败安全错误信息',
    start_time          DATETIME(3)      NOT NULL COMMENT 'Tool开始执行时间',
    finish_time         DATETIME(3)               DEFAULT NULL COMMENT 'Tool完成或失败时间',
    cost_millis         BIGINT                    DEFAULT NULL COMMENT 'Tool执行耗时，单位为毫秒',
    create_time         DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '数据库记录创建时间',
    update_time         DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '数据库记录更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_execution_id (tool_execution_id),
    KEY idx_request_id (request_id),
    KEY idx_tenant_user_start_time (current_tenant_id, current_user_id, start_time),
    KEY idx_tool_status_start_time (tool_name, execution_status, start_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = 'AI Agent Tool执行审计表';