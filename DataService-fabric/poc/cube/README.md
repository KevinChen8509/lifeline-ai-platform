# Cube.dev 语义层（Week 2 W2.1）

## 定位

Headless BI —— 把跨源 JOIN 结果封装为 **业务对象** 和 **指标**，
对外暴露统一 REST API（`/cubejs-api/v1/load`），消费方不再写 SQL。

```
        ┌─────────────────────────────────────────────┐
        │  消费方：Spring Boot / Dashboard / AI Agent │
        └────────────────────┬────────────────────────┘
                             │  POST /cubejs-api/v1/load
                             ▼
                    ┌──────────────────┐
                    │  Cube.dev :4000  │   ← 本目录
                    │  - CustomerProfile │
                    │  - CustomerMetrics │
                    └────────┬─────────┘
                             │  下推 SQL
                             ▼
                    ┌──────────────────┐
                    │  Trino :8080     │   Week 1 虚拟层
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
         MySQL :3306  ClickHouse :8123  Postgres :5432
         customer       orders           risk_tags
```

## 文件

| 路径 | 用途 |
|------|------|
| `schema/CustomerProfile.yml` | 跨源 JOIN 业务对象（customer ⋈ orders ⋈ risk_tags） |
| `schema/Metrics.yml` | 衍生指标（ARPU / VIP3 / 高风险客户数） |
| `conf/config.js` | Cube 配置：Trino 数据源、schema 路径 |
| `.env.example` | 环境变量模板 |

## 启动

前置：Week 1 的 Trino 已健康（`docker compose ps trino`）。

```bash
cd poc

# 1. 启动 Cube
docker compose up -d cube

# 2. 等 schema 编译（约 15-20 秒）
docker compose logs cube | tail -20

# 3. 验证
bash scripts/verify-cube.sh
```

## 手动验证示例

```bash
# 查询客户数（跨源 JOIN）
curl -X POST http://localhost:4000/cubejs-api/v1/load \
  -H "Authorization: Bearer datafabric-poc-secret-2026" \
  -H "Content-Type: application/json" \
  -d '{"query":{"measures":["CustomerProfile.customerCount"]}}'

# 单客户画像（按 custId 过滤）
curl -X POST http://localhost:4000/cubejs-api/v1/load \
  -H "Authorization: Bearer datafabric-poc-secret-2026" \
  -H "Content-Type: application/json" \
  -d '{"query":{
    "measures":["CustomerProfile.totalRevenue"],
    "dimensions":["CustomerProfile.custName","CustomerProfile.customerLevel"],
    "filters":[{"member":"CustomerProfile.custId","operator":"equals","values":["C0001"]}]
  }}'
```

## 关键设计

### 为什么用 Trino 而不是直连各源？

Cube.dev 的 `sql:` 字段定义了业务对象的根 SQL。如果直连 MySQL，就无法跨源 JOIN。
让 Cube → Trino → {MySQL/CH/PG}，复用 Week 1 已验证的虚拟层，业务对象定义保持简洁。

### 为什么 CustomerMetrics 单独一个 cube？

指标的口径由语义层统一定义，下游 API/AI Agent 共享同一真相源。
衍生指标放在独立 cube 里，便于按角色授权（业务方可看 ARPU，风控方看 highRiskCount）。

### 治理标签的解耦

Cube 不在 schema 里写脱敏逻辑（`phone` / `id_card` 字段原样暴露），
脱敏由 Spring Boot AOP 拦截器（Week 2 W2.3）根据 OpenMetadata 标签统一处理。
**标签与代码解耦** —— 改标签不需要发版。

## 已知问题

- Trino driver `@cubejs-backend/trino-driver` 已在 `cubeapi/cube:v0.36.5` 中预装；
  若启动日志报 `Cannot find module`，需在 `conf/config.js` 改用 `require('@cubejs-backend/trino-driver')` 显式加载。
- Dev mode（`CUBEJS_DEV_MODE=true`）跳过 Redis，schema 热加载；
  生产模式需要 Redis（参考 ADR-018）。
