// 5 个跨源联邦查询定义（V1-V5）
// 每个 query 包含: id / name / description / sources / visualization / expect / sql

export const QUERIES = [
  {
    id: 'v1',
    name: 'V1 单客户 360° 视图',
    description: 'MySQL 客户 + ClickHouse 订单聚合 + PostgreSQL 风险标签三源 JOIN，验证单客户完整画像',
    sources: ['mysql', 'clickhouse', 'postgres'],
    visualization: { type: 'radar', title: '客户多维画像' },
    expect: { rowCountEquals: 1 },
    sql: `SELECT
  c.cust_id,
  c.cust_name,
  c.cust_level,
  c.region,
  coalesce(o.total_orders, 0)     AS total_orders,
  coalesce(o.total_amount, 0.0)   AS total_amount,
  r.risk_score,
  r.risk_level
FROM mysql.customer_db.customer c
LEFT JOIN (
  SELECT cust_id, count(*) AS total_orders, sum(order_amount) AS total_amount
  FROM clickhouse.analytics.orders GROUP BY cust_id
) o ON c.cust_id = o.cust_id
LEFT JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
WHERE c.cust_id = 'C0001'`,
  },
  {
    id: 'v2',
    name: 'V2 VIP3 客户 Top10 消费排名',
    description: 'VIP3 客户在 ClickHouse 订单表的总消费金额排序，附 PostgreSQL 风险标签',
    sources: ['mysql', 'clickhouse', 'postgres'],
    visualization: { type: 'bar', title: 'VIP3 客户消费金额 Top10' },
    expect: { maxRows: 10, minRows: 1 },
    sql: `SELECT
  c.cust_id,
  c.cust_name,
  c.region,
  coalesce(o.total_amount, 0) AS total_amount,
  coalesce(o.total_orders, 0) AS total_orders,
  r.risk_level
FROM mysql.customer_db.customer c
LEFT JOIN (
  SELECT cust_id, sum(order_amount) AS total_amount, count(*) AS total_orders
  FROM clickhouse.analytics.orders GROUP BY cust_id
) o ON c.cust_id = o.cust_id
LEFT JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
WHERE c.cust_level = 'VIP3'
ORDER BY o.total_amount DESC NULLS LAST
LIMIT 10`,
  },
  {
    id: 'v3',
    name: 'V3 高风险客户区域分布',
    description: '从 MySQL+PostgreSQL JOIN 找出 high 风险客户，按区域分组统计（V3-alt 变体，绕过中文 literal 编码问题）',
    sources: ['mysql', 'postgres', 'clickhouse'],
    visualization: { type: 'pie', title: '高风险客户区域分布' },
    expect: { minRows: 1 },
    sql: `SELECT
  c.region,
  count(*) AS high_risk_customers,
  max(r.risk_score) AS max_score,
  avg(r.risk_score) AS avg_score
FROM mysql.customer_db.customer c
JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
WHERE r.risk_level = 'high'
GROUP BY c.region
ORDER BY high_risk_customers DESC`,
  },
  {
    id: 'v4',
    name: 'V4 渠道 × 等级 二维聚合',
    description: '客户等级（MySQL）与下单渠道（ClickHouse）的二维交叉聚合，验证 JOIN + GROUP BY 性能',
    sources: ['mysql', 'clickhouse'],
    visualization: { type: 'heatmap', title: '渠道 × 等级 客单价热力' },
    expect: { minRows: 4 },
    sql: `SELECT
  c.cust_level,
  o.channel,
  count(DISTINCT c.cust_id) AS customers,
  sum(o.order_amount)       AS total_amount,
  avg(o.order_amount)       AS avg_amount
FROM mysql.customer_db.customer c
JOIN clickhouse.analytics.orders o ON c.cust_id = o.cust_id
GROUP BY c.cust_level, o.channel
ORDER BY c.cust_level, total_amount DESC`,
  },
  {
    id: 'v5',
    name: 'V5 跨源分布式执行计划',
    description: 'EXPLAIN (TYPE DISTRIBUTED) 显示 Trino 如何把三源 JOIN 切分到不同 Fragment，验证联邦查询真的在工作',
    sources: ['mysql', 'clickhouse', 'postgres'],
    visualization: { type: 'text', title: 'EXPLAIN 分布式执行计划' },
    expect: { minRows: 1 },
    sql: `EXPLAIN (TYPE DISTRIBUTED)
SELECT c.cust_id, c.cust_name, count(o.order_id), sum(o.order_amount), r.risk_level
FROM mysql.customer_db.customer c
LEFT JOIN clickhouse.analytics.orders o ON c.cust_id = o.cust_id
LEFT JOIN postgres.public.risk_tags r ON c.cust_id = r.cust_id
GROUP BY c.cust_id, c.cust_name, r.risk_level`,
  },
];
