-- 冷运AI Agent执行记录表。
--
-- 该表只保存执行元数据，不保存用户原始问题和模型完整答案，
-- 避免司机、订单、支付和公司业务信息默认长期落库。
CREATE TABLE IF NOT EXISTS `cold_chain_agent_execution`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `request_id`       VARCHAR(64) NOT NULL COMMENT 'Agent请求唯一标识',
    `agent_code`       VARCHAR(64) NOT NULL COMMENT '实际执行的Agent稳定编码',
    `agent_name`       VARCHAR(128) NOT NULL COMMENT '实际执行的Agent名称',
    `question_length`  INT UNSIGNED NOT NULL COMMENT '用户问题字符长度',
    `execution_status` TINYINT UNSIGNED NOT NULL COMMENT '执行状态：0已创建，10执行中，20成功，30失败',
    `answer_length`    INT UNSIGNED NULL COMMENT '模型最终答案字符长度',
    `error_code`       INT UNSIGNED NULL COMMENT '执行失败错误编码',
    `error_message`    VARCHAR(512) NULL COMMENT '执行失败安全提示',
    `create_time`      DATETIME(3) NOT NULL COMMENT '执行记录创建时间',
    `start_time`       DATETIME(3) NULL COMMENT 'Agent开始执行时间',
    `finish_time`      DATETIME(3) NULL COMMENT 'Agent执行结束时间',
    `cost_millis`      BIGINT UNSIGNED NULL COMMENT '实际执行耗时，单位毫秒',
    `update_time`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '数据库更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`),
    KEY `idx_agent_code_create_time` (`agent_code`, `create_time`),
    KEY `idx_status_create_time` (`execution_status`, `create_time`),
    CONSTRAINT `chk_execution_status` CHECK (`execution_status` IN (0, 10, 20, 30))
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '冷运AI Agent执行记录';