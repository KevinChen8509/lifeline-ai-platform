-- ============================================================
-- PostgreSQL 外部数据初始化（源 C：风险标签）
-- 通过 COPY FROM CSV 加载（PostgreSQL 官方镜像自动执行）
-- ============================================================

CREATE TABLE IF NOT EXISTS risk_tags (
  cust_id       VARCHAR(32) PRIMARY KEY,
  risk_score    INTEGER NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
  risk_level    VARCHAR(16) NOT NULL CHECK (risk_level IN ('low','medium','high')),
  last_updated  DATE NOT NULL
) WITH (fillfactor = 90);

CREATE INDEX idx_risk_level ON risk_tags(risk_level);

COMMENT ON TABLE risk_tags IS '外部风险标签表（来源：第三方风控 SaaS，每周导出）';
COMMENT ON COLUMN risk_tags.cust_id     IS '客户编号，与 customer_db.customer.cust_id 关联';
COMMENT ON COLUMN risk_tags.risk_score  IS '风险评分 0-100，越高越危险';
COMMENT ON COLUMN risk_tags.risk_level  IS '风险等级：low(<40) / medium(40-70) / high(>70)';

-- 从 CSV 导入 50 条数据
-- 注意：用服务器端 COPY（不是 psql 的 \COPY 元命令），external 用户是 superuser 可执行
COPY risk_tags (cust_id, risk_score, risk_level, last_updated)
FROM '/docker-entrypoint-initdb.d/risk_tags.csv'
WITH (FORMAT csv, HEADER true, DELIMITER ',');

-- 验证
SELECT 'risk_tags inserted' AS step, COUNT(*) AS count FROM risk_tags;

SELECT risk_level, COUNT(*) AS cnt, ROUND(AVG(risk_score), 1) AS avg_score
FROM risk_tags
GROUP BY risk_level
ORDER BY risk_level;
