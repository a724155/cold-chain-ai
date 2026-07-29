CREATE TABLE cold_chain_ai_chat_message
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    message_id        VARCHAR(64)      NOT NULL COMMENT '消息业务唯一标识',
    conversation_id   VARCHAR(64)      NOT NULL COMMENT '消息所属会话业务标识',
    current_user_id   BIGINT           NOT NULL COMMENT '会话所属用户ID，用于数据权限隔离',
    current_tenant_id BIGINT           NOT NULL COMMENT '会话所属租户ID，用于多租户隔离',
    request_id        VARCHAR(64)      NOT NULL COMMENT '产生该消息的Agent请求标识，一问一答可以使用同一个requestId',
    message_role      TINYINT          NOT NULL COMMENT '消息角色：1-USER，2-ASSISTANT',
    message_content   LONGTEXT         NOT NULL COMMENT '聊天消息正文',
    sequence_no       INT              NOT NULL COMMENT '消息在当前Conversation中的顺序，从1开始',
    create_time       DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消息创建时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_message_id (message_id),
    UNIQUE KEY uk_conversation_sequence (conversation_id, sequence_no),
    KEY idx_conversation_owner_sequence
        (current_tenant_id, current_user_id, conversation_id, sequence_no),
    KEY idx_request_id (request_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = 'AI Agent聊天消息历史表';