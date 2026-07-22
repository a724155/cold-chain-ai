-- 统一声明教学数据使用的支付状态，避免INSERT中到处散落状态数字。
-- 这些值必须与DepositPayStatusEnum保持一致。
SET @PAY_STATUS_WAIT_PAY = 10;
SET @PAY_STATUS_PAYING = 20;
SET @PAY_STATUS_PAID = 30;
SET @PAY_STATUS_FAILED = 40;
SET @PAY_STATUS_CLOSED = 50;

-- 删除旧的本地教学数据，使本SQL可以重复执行。
DELETE FROM cold_chain_deposit_pay_order
WHERE pay_order_no IN
      (
       'PAY-AI-DEMO-0001-OLD',
       'PAY-AI-DEMO-0001-NEW',
       'PAY-AI-DEMO-0002',
       'PAY-AI-DEMO-0003',
       'PAY-AI-DEMO-0005'
          );

-- 订单CC-AI-DEMO-0001第一次支付失败。
INSERT INTO cold_chain_deposit_pay_order
(
    tenant_id,
    pay_order_no,
    order_no,
    driver_id,
    deposit_amount_cent,
    pay_status,
    pay_expire_time,
    paid_time,
    failure_reason,
    create_time
)
VALUES
    (
        1001,
        'PAY-AI-DEMO-0001-OLD',
        'CC-AI-DEMO-0001',
        12369,
        1300,
        @PAY_STATUS_FAILED,
        DATE_SUB(NOW(3), INTERVAL 50 MINUTE),
        NULL,
        '用户取消支付',
        DATE_SUB(NOW(3), INTERVAL 60 MINUTE)
    );

-- 订单CC-AI-DEMO-0001第二次支付成功。
-- 查询最新支付单时应该返回这一笔，而不是上一笔失败支付单。
INSERT INTO cold_chain_deposit_pay_order
(
    tenant_id,
    pay_order_no,
    order_no,
    driver_id,
    deposit_amount_cent,
    pay_status,
    pay_expire_time,
    paid_time,
    failure_reason,
    create_time
)
VALUES
    (
        1001,
        'PAY-AI-DEMO-0001-NEW',
        'CC-AI-DEMO-0001',
        12369,
        1300,
        @PAY_STATUS_PAID,
        DATE_SUB(NOW(3), INTERVAL 20 MINUTE),
        DATE_SUB(NOW(3), INTERVAL 28 MINUTE),
        NULL,
        DATE_SUB(NOW(3), INTERVAL 30 MINUTE)
    );

-- 订单CC-AI-DEMO-0002正在支付，并且尚未超过失效时间。
INSERT INTO cold_chain_deposit_pay_order
(
    tenant_id,
    pay_order_no,
    order_no,
    driver_id,
    deposit_amount_cent,
    pay_status,
    pay_expire_time,
    paid_time,
    failure_reason,
    create_time
)
VALUES
    (
        1001,
        'PAY-AI-DEMO-0002',
        'CC-AI-DEMO-0002',
        12369,
        2000,
        @PAY_STATUS_PAYING,
        DATE_ADD(NOW(3), INTERVAL 5 MINUTE),
        NULL,
        NULL,
        DATE_SUB(NOW(3), INTERVAL 5 MINUTE)
    );

-- 订单CC-AI-DEMO-0003数据库仍是支付中，但已经超过失效时间。
-- 用于验证领域对象isExpiredAt()的超时判断。
INSERT INTO cold_chain_deposit_pay_order
(
    tenant_id,
    pay_order_no,
    order_no,
    driver_id,
    deposit_amount_cent,
    pay_status,
    pay_expire_time,
    paid_time,
    failure_reason,
    create_time
)
VALUES
    (
        1001,
        'PAY-AI-DEMO-0003',
        'CC-AI-DEMO-0003',
        12369,
        1800,
        @PAY_STATUS_PAYING,
        DATE_SUB(NOW(3), INTERVAL 10 MINUTE),
        NULL,
        NULL,
        DATE_SUB(NOW(3), INTERVAL 20 MINUTE)
    );

-- 其他租户的数据，用于验证租户隔离。
INSERT INTO cold_chain_deposit_pay_order
(
    tenant_id,
    pay_order_no,
    order_no,
    driver_id,
    deposit_amount_cent,
    pay_status,
    pay_expire_time,
    paid_time,
    failure_reason,
    create_time
)
VALUES
    (
        2002,
        'PAY-AI-DEMO-0005',
        'CC-AI-DEMO-0001',
        12369,
        9999,
        @PAY_STATUS_PAID,
        DATE_SUB(NOW(3), INTERVAL 20 MINUTE),
        DATE_SUB(NOW(3), INTERVAL 28 MINUTE),
        NULL,
        DATE_SUB(NOW(3), INTERVAL 30 MINUTE)
    );