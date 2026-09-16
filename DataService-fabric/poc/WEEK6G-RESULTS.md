# Week 6-G 组合形态 — 主表 GROUP BY + 从表挂载（agg-fusion）

> W6 系列第五篇。前序：W6 跨表融合 → W6-B 运营化 → W6-C 分组聚合 → W6-D 跨源融合 →
> W6-F 从表聚合挂主行。本篇打开**最后一块组合**：顶层 `aggregates + joins` 互斥开闸。

## 目标

用户场景：「按客户汇总订单（每客户一行：订单数 + 总额），再挂上该客户的风险标签」——
主行粒度 = 分组维度，从表按维度值挂载。BI 报表最完整的「事实表 GROUP BY + 维表关联补充」形态。
现状只能拆两个服务两次调用，消费方自己拼。

**设计主张：纯组合，零新基础设施。** 主表走 W6-C `executeAggregate`（已方言化 GROUP BY），
从表走 W6 `attachJoins`（读主行 `parentColumn` 值挂嵌套），管道零改动，只开闸 + 加分派。

关键洞察（探查实证）：组合形态下 `allowedColumns` 即 GROUP BY 维度，而
`parentColumn ∈ allowedColumns` 校验（normalizeJoins）已存在 —— **parentColumn 必是分组维度**
这一硬语义由既有校验链天然保证，无需新规则。

## 实现

### 1. 新 type：`agg-fusion`（ServiceDefinition + Registry）

- `TYPE_AGG_FUSION = "agg-fusion"`（10 字符，store `type VARCHAR(16)` 容纳，**零 DDL**；
  joins VARCHAR(8192) + aggregates VARCHAR(2048) 两列早已并存）
- publish 判定：`aggregates 非空 && joins 非空 → agg-fusion`；单形态判定不变
  （aggregate / fusion / table-query）
- **删除互斥 throw**（W6-C 刻意边界「Phase 3+」本篇兑现）
- filters 放宽保持：aggregates 非空 → 过滤列可为任意元数据列（WHERE 先于 GROUP BY，
  组合形态同样适用）

### 2. 校验新增：跨层撞名（Registry）

主行输出键 = 维度 ∪ 顶层聚合 alias ∪ join.name，须两两不撞：

- join.name 现只查「join 间唯一」→ 加：**不撞 allowedColumns ∪ 顶层 aliases**
  （撞名会 `row.put(join.name, children)` 覆盖主行列值）
- join 级聚合 alias 的 reserved 参数从 `mainColumns` 扩为 `mainColumns + 顶层 aliases`（防歧义）
- 顶层 alias 不撞 dims 沿用 W6-C 既有校验

### 3. 执行分派（ServiceMarketplaceController）

```java
boolean aggMain = TYPE_AGGREGATE.equals(def.type()) || TYPE_AGG_FUSION.equals(def.type());
mainRows = aggMain ? executeAggregate(...) : execute(...);
if (TYPE_AGG_FUSION) {
    rows → LinkedHashMap → attachJoins(def, rows)
    → FusionQueryResponse(columns = dims + 顶层 aliases, joins = JoinView…)
}
```

`executeAggregate` / `attachJoins` **零改动**：聚合主行含维度列，attachJoins 读
`parentColumn` 天然工作。抽 `aggOutColumns()` / `joinViews()` 两助手，aggregate 与 fusion
分支同步复用（消重）。`SERVICE_CALL_LOG` 记 `type:"agg-fusion"` + joinSources + 聚合数。

### 4. 前端（w5-app）

- **去互斥**：🌀 跨表融合 / 📊 分组聚合双开关可同开（删自动关断逻辑）
- 发布前客户端预检：join.name 不撞返回列 ∪ 顶层聚合别名；从表聚合别名不撞顶层别名
- 列表 / 验证台过滤数组 + `'agg-fusion'`；卡片与契约快照显示 **JOIN+AGG 双徽章**，
  输出列 = 维度+别名
- 验证台：fusionSuite 参数化主行列 `mainCols = [...dims, ...aliases]` 跑 FV1-FV8，
  组合形态加跑 **CF1 组合金标**（聚合+跨源+挂载一标全验，按服务结构自适应/skip）+
  **CF2 撞名发布闸**（join.name 塞顶层别名 → 400）

## 测试（158 → 165）

| 文件 | 增量 |
|---|---|
| ServiceRegistryTest | 互斥用例反转为 accepted（type=agg-fusion）；+组合发布合法（join 级聚合同样可用）；+join.name 撞维度/撞顶层别名 400；+从表聚合别名撞顶层别名 400 |
| ServiceMarketplaceControllerTest | +组合金标（C0001 = 聚合 2/4670.50 + PG risk_tags[0] high/82，三机制一标）；+注入 payload 0 组零泄露；+join.name 撞顶层别名发布 400；evil-alias 用例重构（400 保持 + mix 201 agg-fusion） |
| JdbcRegistryStoreTest | +agg-fusion 往返（type/joins/aggregates 三保留，零 DDL） |

## E2E 实录（半 live 栈：om-stub:18585 + data-service:8090 双 H2 替身 + 文件注册表）

| # | 步骤 | 结果 |
|---|---|---|
| 1 | 发布 `orders-cust-risk-w6g`（orders dims={cust_id} + COUNT/SUM + PG risk_tags 行级 join） | **201** · type=`agg-fusion` · sk-w6- key 一次性返回 |
| 2 | query `cust_id=C0001` | **200** · `{cust_id, order_count:"2", total_amount:"4670.50", risk_tags[0]{high,82,C0001}}` 金标逐字一致 |
| 3 | query 无过滤 | 200 · 3 组全挂载（C0001 high/82 · C0002 medium/55 · C0003 low/12） |
| 4 | 注入 payload `C0001' OR '1'='1` | 200 · **total=0 零泄露** |
| 5 | 发布探针 join.name=`order_count`（撞顶层别名） | **400** `join.name 与主行输出列（维度/聚合别名）撞名` |
| 6 | 老服务回归（W6-B fusion / W6-C aggregate×2 / W6-D 跨源 fusion） | **4/4 = 200** |
| 7 | 服务 key 自调 /query | 200（双通道鉴权不受组合形态影响） |
| 8 | **重启** data-service（新 H2 内存库 + 同文件注册表） | type/joins/aggregates 三保留 + **已发 key 仍 200** + 金标逐字复现 |

## 边界（Phase 3+）

HAVING / DISTINCT COUNT / 多级 GROUP BY、跨表同层 JOIN 下推（Trino/StarRocks）、
聚合 join 独立限流、Quota 日总量、多实例 Redis 限流（W6-E 遗留同源）。

至此 W6 发布形态全集：**table-query / fusion（行级+join 聚合）/ aggregate / agg-fusion（组合）**
四形态共用同一套发布校验、执行管道、Key 生命周期与验证台框架。
