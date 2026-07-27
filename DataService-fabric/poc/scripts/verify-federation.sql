-- ============================================================
-- Week 1 跨源 JOIN 验证 SQL（在 Trino CLI 内执行）
-- 用法: docker exec -it dfp-trino trino --file /path/to/verify-federation.sql
-- ============================================================

-- ============================================================
-- 1. 验证 3 个 Catalog 可见
-- ============================================================
SHOW CATALOGS;
-- 期望: clickhouse, mysql, postgres, system

-- ============================================================
-- 2. 列出每个 Catalog 的 schema
-- ============================================================
SHOW SCHEMAS FROM mysql;
SHOW SCHEMAS FROM clickhouse;
SHOW SCHEMAS FROM postgres;

-- ============================================================
-- 3. 各源行数验证
-- ============================================================
SELECT 'mysql.customer' AS source, count(*) AS cnt FROM mysql.customer_db.customer;
SELECT 'clickhouse.orders' AS source, count(*) AS cnt FROM clickhouse.analytics.orders;
SELECT 'postgres.risk_tags' AS source, count(*) AS cnt FROM postgres.public.risk_tags;

-- ============================================================
-- 4. 单客户跨源聚合（验证三源 JOIN）
-- ============================================================
SELECT
  c.cust_id,
  c.cust_name,
  c.cust_level,
  c.region,
  c.register_time,
  coalesce(o.total_orders, 0)     AS total_orders,
  coalesce(o.total_amount, 0.0)   AS total_amount,
  o.last_order_time,
  o.preferred_channel,
  r.risk_score,
  r.risk_level,
  r.last_updated                  AS risk_updated
FROM mysql.customer_db.customer c
LEFT JOIN (
  SELECT
    cust_id,
    count(*)                      AS total_orders,
    sum(order_amount)             AS total_amount,
    max(order_time)               AS last_order_time,
    max_by(channel, order_amount) AS preferred_channel
  FROM clickhouse.analytics.orders
  GROUP BY cust_id
) o ON c.cust_id = o.cust_id
LEFT JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
WHERE c.cust_id = 'C0001';

-- ============================================================
-- 5. VIP3 客户 Top 10 按总消费金额排序
-- ============================================================
SELECT
  c.cust_id,
  c.cust_name,
  c.region,
  coalesce(o.total_amount, 0) AS total_amount,
  coalesce(o.total_orders, 0) AS total_orders,
  r.risk_level
FROM mysql.customer_db.customer c
LEFT JOIN (
  SELECT cust_id, sum(order_amount) AS total_amount, count(*) AS total_orders
  FROM clickhouse.analytics.orders
  GROUP BY cust_id
) o ON c.cust_id = o.cust_id
LEFT JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
WHERE c.cust_level = 'VIP3'
ORDER BY o.total_amount DESC NULLS LAST
LIMIT 10;

-- ============================================================
-- 6. 高风险客户在华东的列表（典型多源过滤场景）
-- ============================================================
SELECT
  c.cust_id,
  c.cust_name,
  c.cust_level,
  c.register_time,
  r.risk_score,
  r.risk_level,
  coalesce(o.total_amount, 0) AS total_amount
FROM mysql.customer_db.customer c
JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
LEFT JOIN (
  SELECT cust_id, sum(order_amount) AS total_amount
  FROM clickhouse.analytics.orders
  GROUP BY cust_id
) o ON c.cust_id = o.cust_id
WHERE c.region = '华东'
  AND r.risk_level = 'high'
ORDER BY r.risk_score DESC;

-- ============================================================
-- 7. 渠道 × 等级 客户数 + 总金额（多维聚合）
-- ============================================================
SELECT
  c.cust_level,
  o.channel,
  count(DISTINCT c.cust_id) AS customers,
  sum(o.order_amount)       AS total_amount,
  avg(o.order_amount)       AS avg_amount
FROM mysql.customer_db.customer c
JOIN clickhouse.analytics.orders o ON c.cust_id = o.cust_id
GROUP BY c.cust_level, o.channel
ORDER BY c.cust_level, total_amount DESC;

-- ============================================================
-- 8. 性能基线（保留执行计划，Week 3 对比）
-- ============================================================
EXPLAIN (TYPE DISTRIBUTED)
SELECT c.cust_id, c.cust_name, count(o.order_id), sum(o.order_amount), r.risk_level
FROM mysql.customer_db.customer c
LEFT JOIN clickhouse.analytics.orders o ON c.cust_id = o.cust_id
LEFT JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
GROUP BY c.cust_id, c.cust_name, r.risk_level;
