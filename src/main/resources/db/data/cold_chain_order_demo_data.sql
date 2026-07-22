-- 删除旧的教学数据，保证本SQL可以在本地重复执行。
-- 删除范围只包含固定的AI-DEMO订单号，不影响其他订单数据。
DELETE FROM cold_chain_order
WHERE order_no IN
      (
       'CC-AI-DEMO-0001',
       'CC-AI-DEMO-0002',
       'CC-AI-DEMO-0003',
       'CC-AI-DEMO-0004',
       'CC-AI-DEMO-0005'
          );

-- 为租户1001、司机12369插入三张今天成交过的订单。
--
-- 第三张订单当前已经取消，但deal_time仍然在今天，
-- 用于验证当前暂定规则：“查询历史成交事件时，成交后取消的订单仍然返回”。
INSERT INTO cold_chain_order
(
    tenant_id,
    order_no,
    driver_id,
    pickup_city,
    delivery_city,
    order_status,
    deal_time
)
VALUES
    (
        1001,
        'CC-AI-DEMO-0001',
        12369,
        '南京市',
        '上海市',
        20,
        TIMESTAMP(CURRENT_DATE(), '09:30:00')
    ),
    (
        1001,
        'CC-AI-DEMO-0002',
        12369,
        '苏州市',
        '杭州市',
        30,
        TIMESTAMP(CURRENT_DATE(), '11:20:00')
    ),
    (
        1001,
        'CC-AI-DEMO-0003',
        12369,
        '合肥市',
        '武汉市',
        50,
        TIMESTAMP(CURRENT_DATE(), '14:10:00')
    ),
    (
        1001,
        'CC-AI-DEMO-0004',
        12369,
        '无锡市',
        '宁波市',
        40,
        TIMESTAMP(DATE_SUB(CURRENT_DATE(), INTERVAL 1 DAY), '08:45:00')
    ),
    (
        1001,
        'CC-AI-DEMO-0005',
        88888,
        '常州市',
        '嘉兴市',
        20,
        TIMESTAMP(CURRENT_DATE(), '16:20:00')
    );