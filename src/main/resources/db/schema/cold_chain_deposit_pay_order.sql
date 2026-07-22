-- 冷运定金支付单表。
--
-- 当前表只包含定金状态查询所需的最小字段，不代表真实支付中心的完整支付单结构。
-- 正式项目还需要与支付中心确认渠道支付单号、商户号、回调流水、退款状态、
-- 支付方式、币种、版本号和对账字段等内容。
CREATE TABLE IF NOT EXISTS `cold_chain_deposit_pay_order`
(
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `tenant_id`           BIGINT UNSIGNED NOT NULL COMMENT '支付单所属租户ID',
    `pay_order_no`        VARCHAR(64) NOT NULL COMMENT '冷运定金支付单号',
    `order_no`            VARCHAR(64) NOT NULL COMMENT '对应的冷运业务订单号',
    `driver_id`           BIGINT UNSIGNED NOT NULL COMMENT '发起定金支付的司机ID',
    `deposit_amount_cent` BIGINT UNSIGNED NOT NULL COMMENT '应支付定金金额，单位为分',
    `pay_status`          TINYINT UNSIGNED NOT NULL COMMENT '支付状态：10待支付，20支付中，30支付成功，40支付失败，50已关闭',
    `pay_expire_time`     DATETIME(3) NOT NULL COMMENT '支付单失效时间',
    `paid_time`           DATETIME(3) NULL COMMENT '支付渠道确认成功时间',
    `failure_reason`      VARCHAR(512) NULL COMMENT '支付失败或关闭时可安全展示的原因',
    `create_time`         DATETIME(3) NOT NULL COMMENT '支付单创建时间',
    `update_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '数据库更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pay_order_no` (`pay_order_no`),
    KEY `idx_tenant_order_create_time` (`tenant_id`, `order_no`, `create_time`, `id`),
    CONSTRAINT `chk_deposit_amount_cent` CHECK (`deposit_amount_cent` > 0),
    CONSTRAINT `chk_deposit_pay_status` CHECK (`pay_status` IN (10, 20, 30, 40, 50))
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '冷运定金支付单';