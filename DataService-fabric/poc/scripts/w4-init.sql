-- W4 可观测台半 live 种子数据（H2 MODE=MySQL，替代 Docker MySQL）
-- 经 data-service 启动参数 MYSQL_URL 的 INIT=RUNSCRIPT FROM 加载
-- 注意：H2 的 INIT 在【每条新连接】上都会执行 —— 必须幂等
-- （MERGE INTO 按 KEY upsert；裸 INSERT 会让池的第 2 个连接撞主键，
--   HikariCP 建连失败表现为 SQLTransientConnectionException）
CREATE TABLE IF NOT EXISTS customer (
    cust_id       VARCHAR(16) PRIMARY KEY,
    cust_name     VARCHAR(64),
    phone         VARCHAR(20),
    id_card       VARCHAR(32),
    cust_level    VARCHAR(8),
    region        VARCHAR(32),
    register_time TIMESTAMP
);
MERGE INTO customer KEY(cust_id) VALUES ('C0001','张伟','13800000001','110101199001011234','VIP3','北京','2026-01-15 10:30:00');
MERGE INTO customer KEY(cust_id) VALUES ('C0002','王芳','13900000002','310101199202022345','VIP2','上海','2026-02-20 14:00:00');
MERGE INTO customer KEY(cust_id) VALUES ('C0003','李娜','13700000003','440101199303033456','VIP1','广州','2026-03-25 09:15:00');
