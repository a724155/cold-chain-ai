-- 冷运订单表。
--
-- 当前表只包含司机成交订单查询所需的最小字段，
-- 不代表真实冷运生产系统的完整订单模型。
--
-- 正式开发前必须根据PRD确认车型、货物类型、温区、金额、装卸时间、
-- 取消原因和履约状态等字段是否需要保存，不能直接把教学表当成生产表。
CREATE TABLE IF NOT EXISTS `cold_chain_order`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '数据库主键',
    `tenant_id`       BIGINT UNSIGNED NOT NULL COMMENT '订单所属租户ID',
    `order_no`        VARCHAR(64) NOT NULL COMMENT '对外稳定订单号',
    `driver_id`       BIGINT UNSIGNED NOT NULL COMMENT '当前订单司机ID',
    `pickup_city`     VARCHAR(64) NOT NULL COMMENT '装货城市',
    `delivery_city`   VARCHAR(64) NOT NULL COMMENT '卸货城市',
    `order_status`    TINYINT UNSIGNED NOT NULL COMMENT '订单状态：10待支付定金，20已成交，30运输中，40已完成，50已取消',
    `deal_time`       DATETIME(3) NOT NULL COMMENT '订单成交时间',
    `create_time`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '数据库创建时间',
    `update_time`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '数据库更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_tenant_driver_deal_time` (`tenant_id`, `driver_id`, `deal_time`),
    CONSTRAINT `chk_cold_chain_order_status` CHECK (`order_status` IN (10, 20, 30, 40, 50))
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_unicode_ci
    COMMENT = '冷运订单';