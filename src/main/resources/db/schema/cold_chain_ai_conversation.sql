CREATE TABLE cold_chain_ai_conversation
(
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    conversation_id     VARCHAR(64)      NOT NULL COMMENT '会话业务唯一标识，前后端交互使用，不直接暴露数据库自增ID',
    current_user_id     BIGINT           NOT NULL COMMENT '当前会话所属用户ID',
    current_tenant_id   BIGINT           NOT NULL COMMENT '当前会话所属租户ID，用于多租户数据隔离',
    agent_code          VARCHAR(64)      NOT NULL COMMENT '当前会话绑定的Agent编码',
    conversation_title  VARCHAR(128)              COMMENT '会话标题，允许首次创建时为空，后续可根据首轮问题生成',
    conversation_status TINYINT          NOT NULL DEFAULT 1 COMMENT '会话状态：1-进行中，2-已关闭',
    message_count       INT              NOT NULL DEFAULT 0 COMMENT '当前会话累计消息数量',
    last_message_time   DATETIME(3)               COMMENT '最近一条消息产生时间',
    version             INT              NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time         DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time         DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_conversation_id (conversation_id),
    KEY idx_user_tenant_update_time (current_tenant_id, current_user_id, update_time),
    KEY idx_agent_code (agent_code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = 'AI Agent会话表';