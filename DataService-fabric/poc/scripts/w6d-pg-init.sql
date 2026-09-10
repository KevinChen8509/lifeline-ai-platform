-- W6-D 跨源融合半 live 种子（H2 MODE=PostgreSQL，替代 Docker PostgreSQL）
-- 经 data-service 启动参数 PG_URL 的 INIT=RUNSCRIPT FROM 加载（幂等：每条新连接重跑）
-- 全双引号：H2 PG 模式把未引号标识符折叠为大写（与真 PG 相反），引号锁定小写
-- 金标：C0001=high/82 · C0002=medium/55 · C0003=low/12
CREATE SCHEMA IF NOT EXISTS "external";
CREATE TABLE IF NOT EXISTS "external"."risk_tags" (
    "cust_id"     VARCHAR(16),
    "risk_level"  VARCHAR(8),
    "risk_score"  INT
);
MERGE INTO "external"."risk_tags" ("cust_id","risk_level","risk_score") KEY("cust_id") VALUES ('C0001','high',82);
MERGE INTO "external"."risk_tags" ("cust_id","risk_level","risk_score") KEY("cust_id") VALUES ('C0002','medium',55);
MERGE INTO "external"."risk_tags" ("cust_id","risk_level","risk_score") KEY("cust_id") VALUES ('C0003','low',12);
