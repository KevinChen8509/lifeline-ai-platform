-- ============================================================
-- ClickHouse 数仓初始化（源 B）
-- 生成 10000 条订单行为数据，关联客户编号 C0001-C1000
-- ============================================================

CREATE DATABASE IF NOT EXISTS analytics;

DROP TABLE IF EXISTS analytics.orders;
CREATE TABLE analytics.orders (
  order_id      String              COMMENT '订单编号 O100000-O110000',
  cust_id       String              COMMENT '客户编号 C0001-C1000',
  order_amount  Decimal(18, 2)      COMMENT '订单金额(元)',
  order_time    DateTime            COMMENT '下单时间',
  channel       LowCardinality(String) COMMENT '下单渠道: pc/mobile/app/wx'
) ENGINE = MergeTree()
  PARTITION BY toYYYYMM(order_time)
  ORDER BY (cust_id, order_time)
  SETTINGS index_granularity = 8192;

-- ------------------------------------------------------------
-- 用 numbers() 函数批量生成 10000 行
-- ------------------------------------------------------------
INSERT INTO analytics.orders
SELECT
  toString(100000 + number)                 AS order_id,
  concat('C', leftPad(toString(1 + (number % 1000)), 4, '0'))
                                             AS cust_id,
  -- 金额分布：80% < 500，20% > 500（长尾）
  round(if(randCanonical() < 0.8,
           10 + randCanonical() * 490,
           500 + randCanonical() * 5000), 2) AS order_amount,
  now() - INTERVAL (number % 730) DAY
       + INTERVAL (number % 86400) SECOND   AS order_time,
  -- 渠道分布：app 45% / mobile 25% / pc 20% / wx 10%
  ['app','app','app','app','app',
   'mobile','mobile','mobile',
   'pc','pc','wx'][1 + (number % 11)]       AS channel
FROM numbers(10000);

-- ------------------------------------------------------------
-- 验证
-- ------------------------------------------------------------
SELECT 'orders inserted' AS step, count() AS cnt FROM analytics.orders;
SELECT 'orders per customer' AS metric,
       min(cnt) AS min_orders, max(cnt) AS max_orders, avg(cnt) AS avg_orders
FROM (SELECT cust_id, count() AS cnt FROM analytics.orders GROUP BY cust_id);

-- 渠道分布
SELECT channel, count() AS cnt, sum(order_amount) AS total
FROM analytics.orders
GROUP BY channel
ORDER BY cnt DESC;
