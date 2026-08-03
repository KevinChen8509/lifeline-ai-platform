-- Week 2 W2.3 审计日志表
-- 由 LoggingAuditLogger 模拟；生产实现切换为 ClickHouseAuditLogger（HTTP 8123 INSERT）
--
-- 设计：
--   ORDER BY (event_date, action)  按天+操作类型分区，便于按操作查询
--   event_time     访问时刻
--   actor          调用者角色（ADMIN/CUSTOMER_VIEWER/SUPPORT/ANONYMOUS）
--   action         API 端点动作（customer-profile.read）
--   resource       客户 ID
--   risk_level     该客户的风险等级（high/medium/low）
--   result         SUCCESS / FAILURE
--   event_id       OpenLineage runId（与 LineageAspect 对齐）

CREATE TABLE IF NOT EXISTS analytics.audit_log
(
    event_id    UUID DEFAULT generateUUIDv4(),
    event_time  DateTime DEFAULT now(),
    event_date  Date DEFAULT toDate(event_time),
    actor       String,
    action      String,
    resource    String,
    risk_level  Nullable(String),
    result      String
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_date, action, event_time)
SETTINGS index_granularity = 8192;

-- 演示：最近一天访问统计 Top 5 操作
-- SELECT action, count() AS hits
-- FROM analytics.audit_log
-- WHERE event_date = today()
-- GROUP BY action ORDER BY hits DESC LIMIT 5;
