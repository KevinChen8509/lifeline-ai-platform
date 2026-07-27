# Data Fabric 数据服务 PoC - Week 1

> **目标**：3 源（MySQL + ClickHouse + PostgreSQL）通过 Trino 跨源 JOIN，OpenMetadata 自动采集元数据 + 血缘。
> **资源**：~8GB RAM · ~6GB 磁盘 · 单机 Docker
> **周期**：1 周（建议 1-2 人）

---

## 1. 目录结构

```
poc/
├── docker-compose.yml          # 7 服务编排
├── .env.example                # 环境变量模板
├── mysql-init/                 # 源 A 初始化（1000 客户）
│   └── 01-customers.sql
├── clickhouse-init/            # 源 B 初始化（10000 订单）
│   └── 01-init.sql
├── clickhouse-config/
│   └── mysql-protocol.xml      # 开启 ClickHouse 9004 MySQL 协议
├── postgres-init/              # 源 C 初始化（风险标签）
│   ├── 01-schema.sql
│   └── risk_tags.csv
├── trino-conf/                 # Trino 3 个 Catalog
│   ├── mysql.properties
│   ├── clickhouse.properties
│   ├── postgres.properties
│   ├── config.properties
│   └── jvm.config
├── openmetadata-ingestion/     # OpenMetadata 数据源定义（参考）
│   ├── mysql-source.json
│   └── clickhouse-source.json
├── scripts/                    # 验证脚本
│   ├── verify-federation.sql
│   ├── smoke-test.sh
│   └── openmetadata-bootstrap.py
└── README.md                   # 本文件
```

---

## 2. 前置条件

- **Docker** 24+ 与 Docker Compose v2
- **内存**：≥ 8GB 可用
- **磁盘**：≥ 6GB 可用
- **端口**：3306 / 5432 / 8123 / 8080 / 9200 / 8585 / 13306 / 9000 / 9004 未被占用

检查：
```bash
docker --version
docker compose version
netstat -ano | grep -E "3306|5432|8123|8080|9200|8585"
```

---

## 3. 一键启动

### 3.1 准备环境变量

```bash
cd E:/Agentic/DataService-fabric/poc
cp .env.example .env
# 按需修改 .env 中的密码
```

### 3.2 拉镜像并启动（首次约 5-10 分钟）

```bash
docker compose up -d
docker compose ps
```

期望状态：
```
NAME                  STATUS                   PORTS
dfp-clickhouse        Up (healthy)             8123, 9000, 9004
dfp-mysql             Up (healthy)             3306
dfp-om-mysql          Up (healthy)             13306
dfp-openmetadata      Up (healthy)             8585
dfp-opensearch        Up (healthy)             9200
dfp-postgres          Up (healthy)             5432
dfp-trino             Up (healthy)             8080
```

> OpenMetadata 启动较慢（90-180 秒），请耐心等待 `healthy`。

### 3.3 冒烟测试

```bash
bash scripts/smoke-test.sh
```

期望输出：
- MySQL 1000 行客户 ✅
- ClickHouse 10000 行订单 ✅
- PostgreSQL 50 行风险标签 ✅
- Trino 3 个 Catalog 可见 ✅
- Trino 跨源 JOIN 返回客户画像 ✅
- OpenMetadata `/health-check` 200 ✅

---

## 4. 服务访问

| 服务 | 地址 | 用户 / 密码 | 用途 |
|------|------|------------|------|
| MySQL 业务库 | `localhost:3306` | `root / poc123` | 客户主数据 |
| ClickHouse HTTP | `http://localhost:8123` | `default / 空` | 订单行为 |
| ClickHouse MySQL 协议 | `localhost:9004` | `default / 空` | Trino 通过此访问 |
| PostgreSQL | `localhost:5433` | `external / external123` | 风险标签 |
| Trino Web | `http://localhost:8080` | — | 联邦查询 UI |
| Trino CLI（容器内） | `docker exec -it dfp-trino trino` | — | SQL 客户端 |
| OpenMetadata Web | `http://localhost:8585` | `admin / admin` | 元数据平台 |
| OpenSearch | `http://localhost:9200` | — | OM 搜索后端 |

---

## 5. 手工验证清单

### 5.1 验证三源数据

```bash
# MySQL: 应返回 1000
docker exec dfp-mysql mysql -uroot -ppoc123 -e \
  "SELECT COUNT(*) FROM customer_db.customer;"

# ClickHouse: 应返回 10000
curl -s 'http://localhost:8123/?query=SELECT+COUNT(*)+FROM+analytics.orders'

# PostgreSQL: 应返回 50
docker exec dfp-postgres psql -U external -d external -c \
  "SELECT COUNT(*) FROM risk_tags;"
```

### 5.2 验证 Trino 跨源 JOIN

```bash
docker exec -it dfp-trino trino --file /dev/stdin <<'SQL'
SHOW CATALOGS;
-- 期望: clickhouse, mysql, postgres, system

-- 跨源 JOIN：客户 + 订单聚合 + 风险
SELECT
  c.cust_id,
  c.cust_name,
  c.cust_level,
  c.region,
  coalesce(o.total_orders, 0)     AS total_orders,
  coalesce(o.total_amount, 0.0)   AS total_amount,
  r.risk_level
FROM mysql.customer_db.customer c
LEFT JOIN (
  SELECT cust_id,
         count(*) AS total_orders,
         sum(order_amount) AS total_amount
  FROM clickhouse.analytics.orders
  GROUP BY cust_id
) o ON c.cust_id = o.cust_id
LEFT JOIN postgres.external.risk_tags r ON c.cust_id = r.cust_id
WHERE c.cust_level = 'VIP3'
ORDER BY o.total_amount DESC NULLS LAST
LIMIT 10;
SQL
```

### 5.3 配置 OpenMetadata 元数据采集

1. 浏览器打开 `http://localhost:8585`，登录 `admin / admin`（首次登录强制改密）
2. **Settings → Database Connections → Add New Connection**
3. 选 **MySQL**，填入：
   - Host: `mysql`（docker 内部域名）
   - Port: `3306`
   - Username: `root` · Password: `poc123`
   - Database: `customer_db`
4. 保存后点 **Ingest** 触发首次采集
5. 重复为 ClickHouse 添加连接：
   - Host: `clickhouse` · Port: `9000` · Schema: `analytics`
6. 在 **Explore** 页面验证：表、字段、采样统计
7. 在 **Lineage** 页面验证：后续 Week 2 添加的跨源视图会自动显示血缘

详见 `openmetadata-ingestion/` 下的 JSON 模板（可通过 API 创建）。

---

## 6. 停止 / 清理

```bash
# 停止（保留数据）
docker compose stop

# 完全删除（保留 volumes）
docker compose down

# 彻底清理（含数据）
docker compose down -v
```

---

## 7. 故障排查

| 问题 | 原因 | 解决 |
|------|------|------|
| Trino 启动失败 / Catalog 不可见 | 三个 catalog 文件权限或路径错 | `docker exec dfp-trino ls /etc/trino/catalog/` |
| ClickHouse 9004 拒绝连接 | `mysql-protocol.xml` 未挂载 | `docker exec dfp-clickhouse cat /etc/clickhouse-server/config.d/mysql-protocol.xml` |
| OpenMetadata 启动 60s 仍 unhealthy | OpenSearch 初始化慢 | `docker logs dfp-opensearch` 等待 `yellow → green` |
| `docker compose ps` 显示某服务 `unhealthy` | 资源不足 | `docker stats` 检查 CPU/Memory |
| Windows Git Bash 路径转换问题 | MSYS_NO_PATHCONV 未设置 | `export MSYS_NO_PATHCONV=1` |

---

## 8. Week 1 → Week 2 衔接

Week 1 完成后，进入 Week 2（语义层 + 治理嵌入 + 服务层）：

- 在 Trino 中创建跨源 View（`view.customer_profile_v`）作为 Cube.dev 的数据源
- 部署 Cube.dev 容器，连接 Trino
- 在 OpenMetadata 中为关键字段打标（PII / Business）
- 部署 Spring Boot 数据服务（连接 Cube + OpenMetadata）

详见 `docs/poc-data-fabric.md` §4 Week 2 任务清单。

---

## 9. 已知限制

- ClickHouse 通过 9004 MySQL 协议连接 Trino，部分 ClickHouse 方言（如 `arrayJoin`）不可用
- OpenMetadata 自动血缘依赖 JDBC 解析（v1.12 的 ClickHouse View 血缘有 bug Issue #17574，表血缘正常）
- 数据量较小（1 万订单），Cube.dev 预聚合优势不明显，Week 3 会用 JMeter 扩量验证
- 单机部署，无 HA
