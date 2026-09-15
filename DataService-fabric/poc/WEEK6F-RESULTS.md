# Week 6-F 组合形态 — 从表聚合挂主行（join-level aggregates）

日期：2026-09-15 · 分支 `datafabric-week2` · 测试 **158/158**

## 目标

fusion DSL 补上最后一块组合能力：**每个 join 可声明聚合列** —— 从表按关联键
`GROUP BY` 后每主行挂 0/1 行指标（如「查客户，每人带订单数和订单总额」）。
这是 BI/报表最常见形态（主维表 + 事实表聚合指标）；此前只能拉全量从行
（token 浪费 + limitPerParent 截断即漏数）或拆两个服务两次调用。

**设计主张**：聚合发生在从表侧、按 joinColumn 分组 —— 纯组合 W6-C 聚合校验
（函数/列/别名命中**从表**元数据）+ W6-D 方言与库限定 + W6 两段式执行管道，
零新基础设施、零 DDL。

## 实现

### 1. DSL：JoinSpec 6 → 7 组件 `aggregates`（ServiceDefinition）

```json
"joins": [{ "fqn": "mysql.customer_db.orders", "name": "orders_agg",
  "joinColumn": "cust_id", "parentColumn": "cust_id",
  "aggregates": [
    {"function": "COUNT", "column": null,        "alias": "order_count"},
    {"function": "SUM",   "column": "order_amount", "alias": "total_amount"} ] }]
```

- **每 join XOR**：`columns` 与 `aggregates` 二选一 —— 双空 400（从表必须带回点什么）、
  双非空 400（行级与聚合混挂语义混乱）。顶层 `aggregates + joins` 互斥保持不变
- 校验复用 W6-C `validateAggregates` 语义但元数据源换 **joinMeta（从表列）**：
  函数白名单 {SUM,COUNT,AVG,MIN,MAX}、column 逐字命中从表 OM 元数据、
  alias `^[A-Za-z0-9_]+$`、仅 COUNT 可 `*`
- alias 唯一性：join 内唯一 + **不撞主表 allowedColumns**（跨 join 撞名不成立——各挂各的嵌套键下）
- `limitPerParent` 对聚合 join 无意义 → 校验忽略（GROUP BY joinColumn 保证每父键 ≤1 行）

### 2. 执行：attachJoins 聚合分支（ServiceMarketplaceController）

```sql
-- 行级（现状）：      SELECT `cust_id`,`order_id`,… WHERE `cust_id` IN (?,…) LIMIT per×父数
-- 聚合（新增，无 LIMIT）：
SELECT `cust_id`, SUM(`order_amount`) AS `total_amount`, COUNT(*) AS `order_count`
  FROM `customer_db`.`orders` WHERE `cust_id` IN (?,…) GROUP BY `cust_id`
```

- 聚合表达式方言化（W6-D `q()` 复用：PG 双引号 `SUM("risk_score")`）
- 分组 map：`joinColumn 值 → 单行 Map`；挂载 0/1 元素数组
  （`found ? List.of(row) : List.of()`）—— **嵌套数组形状与行级一致**：
  FusionQueryResponse / FV6 关联一致性断言 / 前端渲染零改动
- 响应 `joins[i]` 元数据（JoinView）：行级透 columns，聚合透 **aliases**
- 值仍全 PreparedStatement 绑定 + 标识符二道闸不变

### 3. 持久化：零 DDL

joins 走裸 ObjectMapper → JSON VARCHAR，`aggregates` 随 JoinSpec 序列化自动落库/恢复。
旧服务（无该字段）反序列化 → compact constructor 兜 `List.of()` → 行级语义不变。

### 4. 前端（w5-app）

- 从表编辑器加「📊 聚合模式」开关：开则隐藏列勾选 + 每主键上限，显示聚合行编辑器
  （fn × 从表列(COUNT 带 `*`) × alias，≤5 行，候选来自从表元数据）
- 发布体：聚合 join 发 `aggregates`，不发 `columns`/`limitPerParent`
- 卡片/契约快照分支渲染 `📊 聚合从表`；验证台下拉标 `（融合·含聚合join）`
- 验证台 **FV8**（新增）：聚合 join 金标（按函数解析 COUNT/SUM(order_amount) 别名，
  C0001 → order_count=2 · total_amount=4670.50）+ 别名注入发布闸（alias 塞
  `cnt; DROP TABLE orders` → 400；探针其余字段全合法，唯一失败原因=alias）
- FV4 钳制上限对聚合 join 取 1；FV5 注入探针聚合 join 打聚合列；FV7 金标收窄到行级 join

## 测试（151 → 158）

- **同源金标**：customer + orders 聚合 join（COUNT(*)+SUM(order_amount)）→
  C0001 → 张伟 + `orders_agg[0]{order_count:"2", total_amount:"4670.50"}`
- **跨源金标**：customer(MySQL) + risk_tags(PG) `SUM(risk_score) AS sum_score` →
  `risk_sum[0]{sum_score:"82"}` —— PG 替身 MODE=PostgreSQL，反引号即语法错，**金标即方言真验证**
- Registry：聚合 join 合法 / XOR 双空双满 / alias 注入 / alias 撞主表列 / 旧 JSON 兼容
- Store：聚合 join save→load 往返（aggregates 逐字保留）
- Controller：注入 payload 0 行零泄露 / 行级 join 回归 / JoinView aliases

## E2E 实录（半 live 栈：om-stub:18585 + dashboard:3000 + data-service:8090 双 H2 替身）

1. 启动 → W6-B/C/D/E 存量 7 服务全数恢复（registry-db 文件库）
2. 发布 `cust-orders-agg-w6f`（主 customer + orders 聚合 join）→ 201 + `sk-w6-ab1c0c7e…`
3. 同源金标：`?cust_level=VIP3` → 张伟 + `orders_agg[0]{order_count:"2", total_amount:"4670.50", cust_id:"C0001"}`
4. 跨源金标：发布 `cust-risk-aggsum-w6f`（PG 从表 SUM(risk_score)）→
   C0001 → `risk_sum[0]{sum_score:"82"}` —— 聚合方言跨源真验证
5. 注入双闸：alias 塞 `total; DROP TABLE orders` 发布 → 400「聚合别名不合法」；
   query payload `VIP3' OR '1'='1` → total:0 零泄露
6. 旧服务回归：w6b/w6c/w6d 行级 fusion / aggregate 全 200 金标不变
7. **重启持久化**：kill → 重启 → `cust-orders-agg-w6f` 恢复且聚合列完好（金标重放一致）、
   已发服务 key `sk-w6-ab1c0c7e…` 直查 200 —— aggregates 随 joins JSON 落库铁证

浏览器 `localhost:3000/w5-app.html`：向导从表「📊 聚合模式」→ 发布 → FV1-FV8 全 PASS。

## 边界（Phase 3+）

- 跨表同层 JOIN 下推（Trino/StarRocks）、HAVING / DISTINCT COUNT / 多级 GROUP BY
- 主表 GROUP BY + 从表挂载组合（顶层互斥保持）
- 聚合 join 独立限流/计量、ClickHouse 源聚合验证（机制同 PG，元数据已就绪）
