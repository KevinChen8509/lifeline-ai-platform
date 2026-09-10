# Week 6-D 跨源融合 — 从表跨数据源（MySQL 主表 + PostgreSQL 从表）

日期：2026-09-10 · 分支 `datafabric-week2` · 测试 **143/143**

## 目标

fusion DSL 的从表突破单源限制：主表在 MySQL、从表在 PostgreSQL（或 ClickHouse），
应用层按关联键合并 —— 验证「数据服务层组合多源」这一 Data Fabric 核心主张
（W1 用 Trino 验证过查询引擎联邦，本次验证**服务发布形态**的联邦）。

## 发现：架构早已 source-aware，缺的是三块执行层短板

探索结论（未写一行代码前）：

| 环节 | 现状 | 结论 |
|---|---|---|
| DSL 校验 | `normalizeJoins` 已按从表 FQN 前缀校验源 ∈ {mysql,clickhouse,postgres}，列逐字命中**从表自己的** OM 元数据 | ✅ 已就绪 |
| 池路由 | `attachJoins` 已按从表源取连接池（`pools.get(joinSource)`） | ✅ 已就绪 |
| 元数据 | om-stub 已登记 `postgres.external.risk_tags` / `clickhouse.orders_db.orders` | ✅ 已就绪 |
| **标识符引号** | 三处 SQL 拼装全用 MySQL 反引号 —— PostgreSQL 只认双引号 | ❌ 跨源即语法错 |
| **FROM 库限定** | SQL 只用 FQN 末段表名，中段 database 未持久化 —— 跨库/模式解析不到（同源异库也是潜在 bug：CH 池 URL 指向 default 而表在 orders_db） | ❌ 需持久化中段 |
| **第二可查源** | 半 live 栈只有 H2-as-MySQL，PG 池指向不存在的真 PG | ❌ 需 PG 替身 |

## 实现

### 1. `database` 持久化（ServiceDefinition 16 → 17 组件）

- FQN 中段（`mysql.customer_db.customer` 的 `customer_db`）随定义落库，发布期过标识符正则
- `service_def` 加列 `db_name VARCHAR(128)`（`ALTER … ADD COLUMN IF NOT EXISTS` 迁移）
- **W6-C 教训前置应用**：MERGE 显式 17 列清单 —— 不重蹈位置绑定 vs ALTER 追加列错位

### 2. 引号方言 + 库限定（ServiceMarketplaceController）

```java
q(ident, source)        // postgres → "ident" ；mysql/clickhouse → `ident`（CH 双引号也认）
qualifiedTable(db, tbl, source)  // FROM `customer_db`.`customer` / FROM "external"."risk_tags"
```

- 主表（execute / executeAggregate）与从表（attachJoins）三处 SQL 全部改方言引号 + 库.表限定
- 聚合表达式同步方言化：`SUM(\`x\`)` / `SUM("x")` / `COUNT(*)`
- 第二道闸（IDENTIFIER 正则复检）与全值 PreparedStatement 绑定不变
- fusion 的 SERVICE_CALL_LOG 增加 `"joinSources":["postgres"]` —— 跨源调用可观测

### 3. 种子与替身

- `w4-init.sql`：表落 `customer_db` schema（与 FQN 中段一致）
- 新 `w6d-pg-init.sql`：PG 模式 H2 替身 `external.risk_tags`（C0001=high/82 · C0002=medium/55 · C0003=low/12）
- 启动参数 `PG_URL=jdbc:h2:mem:…;MODE=PostgreSQL;INIT=RUNSCRIPT FROM '…/w6d-pg-init.sql'`

### 4. 旧数据兼容（一次性回填）

W6-B/W6-C 落库的 4 条旧服务无 `db_name`（当时 FQN 中段未持久化，无法从行内重建）——
对开发库执行一次性回填 `UPDATE service_def SET db_name='customer_db' WHERE db_name IS NULL`
（4 行均来自 customer_db，事实核对后回填）。生产环境应走正式迁移脚本或要求重发布。
代码不做猜测式兜底：`database` 为 null 即拒绝拼限定名，宁可显式失败。

## 测试（141 → 143）

- **跨源金标**：发布 customer(MySQL) + risk_tags(PG) → VIP3 → 张伟行嵌套
  `risk_tags[0] = {risk_level:"high", risk_score:"82", cust_id:"C0001"}` ——
  PG 替身是 MODE=PostgreSQL，若从表 SQL 沿用反引号会直接语法错（502），**金标即方言真验证**
- 跨源注入：`VIP3' OR '1'='1` → 0 主行 0 从行零泄露
- 既有 fusion/aggregate 用例全部回归绿（库限定不破坏同源）
- JdbcRegistryStore：database 往返断言 + 旧表迁移用例（15 列旧表 ALTER 追加 aggregates+db_name 双列均不错位）

**H2 坑（两条，均实证）**：PG 模式把未引号标识符折叠为**大写**（与真 PG 相反）→
PG 替身种子必须全双引号锁定小写；MODE=MySQL + DATABASE_TO_LOWER=TRUE 则未引号折小写（w4-init 免引号安全）。

## E2E 实录（半 live 栈：om-stub:18585 + dashboard:3000 + data-service:8090 双 H2 替身）

1. 池证据：查询后 `/api/v1/raw-db/pools` → **mysql STARTED + postgres STARTED**（双源活体）
2. 发布 `cust-risk-xsrc-w6d`（Write 工具 UTF-8 JSON + `--data-binary`，规避 GBK）→ 201 + sk-w6- key
3. 金标：`?cust_level=VIP3` → 张伟 + risk_tags=[{high, 82, C0001}]（与单测逐字一致）；无过滤 3 客户全对（high/medium/low 各归其主）
4. 注入：`total:0`；SERVICE_CALL_LOG `joinSources:[postgres]`
5. 旧服务回归：`cust-orders-w6b`（同源 fusion）张伟+2 单 ✅ · `orders-by-cust-w6c`（aggregate）C0001=2单/4670.50 ✅
6. **重启持久化**：kill 8090 → 重启 → 同 key `sk-w6-72e7…` 微秒级不变 · `database=customer_db` 恢复 ·
   服务 key 直查金标依旧 → 17 列 MERGE（含 db_name）落库铁证
7. 前端：向导从表下拉 `⟂跨源 postgres.external.risk_tags（客户风险标签）` 标记可见 → 发布 → FV1-FV7 复用 fusion 套件

## 边界（Phase 3+）

- 跨源 JOIN 下推（Trino/StarRocks 把合并下沉引擎）—— 应用层合并即是本次主张的对照面
- 真实 Docker ClickHouse/PostgreSQL 联验（机制同 PG 替身，`clickhouse.orders_db.orders` 元数据已就绪）
- 聚合+融合组合（跨源 + GROUP BY）、从表独立限流/计量
- 旧行 db_name 生产级迁移脚本（本次为开发库一次性回填）
