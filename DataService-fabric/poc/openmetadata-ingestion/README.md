# OpenMetadata 数据源配置说明

本目录提供 3 个数据源（MySQL / ClickHouse / PostgreSQL）的 JSON 定义，**仅作为参考模板**。实际配置通过 OpenMetadata Web UI 操作更直观。

## 两种创建方式

### 方式 1：UI 创建（推荐）

1. 浏览器打开 `http://localhost:8585`
2. 首次登录 `admin / admin`，按提示改密
3. **Settings → Database Connections → Add New Connection**
4. 选择数据库类型 → 填入下方对应参数 → 保存 → **Ingest**

### 方式 2：API 创建（自动化）

```bash
# 获取 access token（admin 用户）
TOKEN=$(curl -s -X POST http://localhost:8585/api/v1/users/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@open-metadata.org","password":"<your-new-password>"}' \
  | jq -r .accessToken)

# 创建 MySQL 数据源
curl -X POST http://localhost:8585/api/v1/services/databaseServices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @mysql-source.json

# 创建 ClickHouse 数据源
curl -X POST http://localhost:8585/api/v1/services/databaseServices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @clickhouse-source.json

# 创建 PostgreSQL 数据源
curl -X POST http://localhost:8585/api/v1/services/databaseServices \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @postgres-source.json
```

## UI 创建时的填表参数

### MySQL（源 A）

| 字段 | 值 |
|------|---|
| Connection Name | `mysql_customer_db` |
| Type | MySQL |
| Host | `mysql`（docker 容器名） |
| Port | `3306` |
| Username | `root` |
| Password | `poc123` |
| Database | `customer_db` |
| SSL Mode | disabled |

### ClickHouse（源 B）

| 字段 | 值 |
|------|---|
| Connection Name | `clickhouse_analytics` |
| Type | ClickHouse |
| Host | `clickhouse` |
| Port | `9000`（native TCP，不是 8123） |
| Username | `default` |
| Password | （空） |
| Database Schema | `analytics` |
| SSL Mode | disabled |

### PostgreSQL（源 C）

| 字段 | 值 |
|------|---|
| Connection Name | `postgres_external` |
| Type | Postgres |
| Host | `postgres` |
| Port | `5432` |
| Username | `external` |
| Password | `external123` |
| Database | `external` |

## 元数据采集流程

每个数据源创建后：

1. **Add Ingestion**（添加采集作业）
2. 选择 **Database Metadata** 类型
3. 重复间隔：`1 hour`（PoC），生产环境 1-24h
4. 启用 **Profiler & Data Quality**（采样统计 + 质量检查）
5. 点 **Run Now** 触发首次采集
6. **Explore** 页面查看采集结果

## 验证清单

- [ ] 3 个数据源全部状态 `Active`
- [ ] MySQL `customer_db.customer` 表的 7 个字段被识别
- [ ] ClickHouse `analytics.orders` 表的 5 个字段被识别
- [ ] PostgreSQL `public.risk_tags` 表的 4 个字段被识别
- [ ] 字段采样统计（min/max/null count）正常显示
- [ ] **Lineage** 页面：暂无跨源血缘（Week 2 在 Trino 中创建跨源 View 后会出现）

## Week 2 预告

Week 2 添加字段级标签后，本目录会扩展：
- `field-tags.json` — 为 `customer.phone`、`customer.id_card` 打 PII 标签
- `glossary.json` — 业务术语表（VIP 等级、风险等级定义）

字段标签会被 Spring Boot 拦截器读取，驱动动态脱敏。
