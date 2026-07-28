# Week 1 验证结果（Data Fabric PoC）

**完成日期**: 2026-07-27
**集群状态**: 4 核心服务 healthy（MySQL / ClickHouse / PostgreSQL / Trino）

---

## ✅ 验证结论

**跨源联邦查询完整跑通**。Trino 435 同时打通 MySQL + ClickHouse + PostgreSQL 三个数据源，
对 11,050 行真实数据执行分布式 JOIN，性能与正确性符合 PoC 预期。

---

## 关键架构决策（写文档时必须引用）

### 1. ClickHouse Connector：放弃 mysql/postgres wire protocol，用原生 HTTP

| 协议 | 端口 | 现象 | 结论 |
|------|------|------|------|
| MySQL wire | 9004 | `Buffer length is less than expected payload length` | mysql-connector 8.x 不兼容 ClickHouse OK 包 |
| PostgreSQL wire | 9005 | `ClickHouse doesn't support extended query mechanism` | ClickHouse postgres 实现仅支持 simple query |
| **HTTP（原生）** | **8123** | **稳定** | **Trino 435 自带 clickhouse 插件** |

**最终配置** `trino-conf/clickhouse.properties`:
```properties
connector.name=clickhouse
connection-url=jdbc:clickhouse://clickhouse:8123/default
connection-user=default
connection-password=click123
clickhouse.map-string-as-varchar=true   # 必加，否则 String 映射成 varbinary 无法跨源 JOIN
```

### 2. ClickHouse default 用户开放网络访问

ClickHouse 24.8 Docker entrypoint 默认禁用 `default` 用户的网络访问（未设 `CLICKHOUSE_USER/PASSWORD`）。
方案：通过 `clickhouse-config/default-user.xml` 挂载到 `users.d/`：
```xml
<clickhouse>
  <users>
    <default>
      <password>click123</password>
      <networks><ip>::/0</ip></networks>
      <access_management>1</access_management>
    </default>
  </users>
</clickhouse>
```

### 3. Trino 435 配置兼容性

`config.properties` 必须移除 4 个 Trino 435 已弃用属性：
- `query.max-total-memory-per-node`
- `discovery.enabled`
- `exchange.data-transformer-enabled`

各 catalog `.properties` 必须移除：
- `mysql.autocommit` / `postgresql.autocommit`
- `jdbc.metastore-cache-ttl`

---

## 验证用例执行结果

| 用例 | 跨源 | 耗时 | 结果 |
|------|------|------|------|
| **V1** 单客户 360 视图（3 源 JOIN） | MySQL + ClickHouse + PostgreSQL | ~1s | ✅ C0001: 10 单 / ¥21535.28 / risk=high |
| **V2** VIP3 Top 10 消费排名 | MySQL + ClickHouse + PostgreSQL | ~1s | ✅ Top1: C0233 ¥31885.56 |
| **V3-alt** 高风险客户区域分布 | MySQL + PostgreSQL（+ ClickHouse） | <1s | ✅ 14 高风险客户覆盖 5 大区 |
| **V4** 渠道 × 等级 2D 聚合 | MySQL + ClickHouse | <1s | ✅ 12 组合全量返回 |
| **V5** EXPLAIN 分布式执行计划 | 三源 | <1s | ✅ Fragment 0/1/4 跨源 hash-partitioned |

### 数据量
- MySQL `customer_db.customer`: 1,000 行
- ClickHouse `analytics.orders`: 10,000 行
- PostgreSQL `public.risk_tags`: 50 行

---

## 待补验证（Docker 不稳定中断，留作 Week 2 入口任务）

- [ ] V3 原版（华东 + 高风险过滤，已确认是 bash→docker-exec 中文 literal 编码问题，不影响数据；用 `chr(0x534E)||chr(0x4E1C)` 构造可绕过）
- [ ] OpenMetadata + OpenSearch 启动（~1.5GB 镜像未拉取）
- [ ] OpenMetadata ingestion workflows 抽取元数据
- [ ] `scripts/smoke-test.sh` 自动化

---

## 修复的 Bug 清单（共 7 个）

1. PostgreSQL `\COPY` 语法 → 改为 `COPY`（server-side SQL）
2. MySQL 身份证号 19 位 → 改为 18 位（年份 4 位而非 3 位前缀+2 位）
3. PostgreSQL 端口冲突 5432 → 5433
4. Trino `config.properties` 4 个弃用属性移除
5. Trino catalog 2 个不识别属性移除（`*.autocommit`、`jdbc.metastore-cache-ttl`）
6. ClickHouse `default` 用户网络访问授权（`users.d/default-user.xml`）
7. ClickHouse protocol 切换：mysql wire → postgres wire → HTTP（最终方案）

---

## 下一步：Week 2（语义层 + 数据服务）

按 `docs/poc-data-fabric.md` 计划进入 Week 2：
1. Cube.dev semantic schema（基于 Week 1 的 3 源表，定义 `Customer_360` / `OrderSummary` 等 cube）
2. Spring Boot 3.5 数据服务（暴露 `/api/v1/customers/{id}/profile` REST API，内部调 Trino）
3. AOP 嵌入式治理（row-level filter by `risk_level`、column masking on `id_card`）
4. Pact 契约测试基线
