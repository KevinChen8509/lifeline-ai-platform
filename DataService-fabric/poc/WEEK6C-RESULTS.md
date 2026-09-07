# Week 6-C 结果 · 分组聚合服务 — 聚合列 DSL（SUM/COUNT/AVG/MIN/MAX）

> 需求原点：W6/W6-B 之后，自助发布只有**行级**形态（单表投影 / 融合嵌套行）——
> BI 报表、经营看板要的「按客户汇总订单额」「按等级数客户」仍需工程师写 SQL。
>
> W6-C 落地：第三种自助发布形态 **`aggregate`** —— 声明 GROUP BY 维度 + 聚合列，
> 受限 DSL 双闸校验（函数白名单 + 目录元数据逐字命中），单条 GROUP BY SQL 全值绑定执行。

## 能力清单

| 能力 | 实现 | 位置 |
|---|---|---|
| 聚合列 DSL | 发布体 `aggregates: [{function, column, alias}]`（≤5 条）；**维度 = allowedColumns**（勾选列即 GROUP BY，不引入第二套字段）；`column: null` 仅 COUNT 合法（`COUNT(*)`），其余函数必须选列 | `service/ServiceRegistry`（`normalizeAggregates`） |
| 函数白名单 | `AGG_FUNCTIONS = {SUM, COUNT, AVG, MIN, MAX}`；alias 须 `^[A-Za-z0-9_]+$` 且全局唯一、不与维度撞名 | `service/ServiceRegistry` |
| 形态互斥 | 聚合与融合（joins）同报 → 400「聚合与融合互斥」（聚合+组合属 Phase 3+）；type 判定：aggregates 非空 → `aggregate`，否则 joins 定 fusion/table-query | `service/ServiceRegistry` |
| 过滤放宽（仅聚合形态） | 过滤列放宽为「命中 OM 元数据任意列」——WHERE 先于 GROUP BY，非维度列合法（如 GROUP BY cust_id + WHERE cust_level）；table-query/fusion 保持过滤列 ∈ allowedColumns 旧不变式 | `service/ServiceRegistry`（`normalizeFilters` 签名 Collection 化） |
| 执行引擎 | 单条 SQL：`SELECT dims…, SUM(\`x\`) AS \`alias\`… FROM t WHERE … GROUP BY dims ORDER BY 1 LIMIT n`；值全 PreparedStatement 绑定；执行前第二道闸（表/维度/函数/alias/列标识符复检 + 表名白名单）| `api/ServiceMarketplaceController`（`executeAggregate`） |
| 输出契约 | 复用 QueryResponse：`columns = 维度 + 聚合别名`（前端查询结果渲染零新增逻辑）；聚合值 `rs.getString` 透出（H2 DECIMAL 保 `4670.50` 原样） | `api/ServiceMarketplaceController` |
| 持久化迁移 | `service_def` 增列 `aggregates VARCHAR(2048)`（nullable）；`ALTER TABLE … ADD COLUMN IF NOT EXISTS` 幂等迁移，W6-B 老行 NULL → `readJsonList` 兜底 `List.of()`；AggSpec.isStar() 加 `@JsonIgnore`（防派生属性混入持久化 JSON 炸反序列化） | `service/JdbcRegistryStore` / `service/ServiceDefinition` |
| W6-B 运营继承 | Key 生命周期 / 限流 / 计量全部 type 无关，聚合服务自动继承（发布即发 `sk-w6-` key，rotate/revoke/usage 可用） | — |
| 前端向导 | 「📊 分组聚合」开关（与 🌀 跨表融合**互斥联动**，开一个自动关另一个）；聚合列编辑器（函数下拉 × 列下拉[COUNT 带 `*` 选项] × 别名输入 + 增删行 ≤5）；聚合模式下提示「勾选列 = GROUP BY 维度」「过滤列可为任意元数据列」 | `dashboard/public/w5-app.*`（`renderAggRows`） |
| 前端卡片/验证台 | 卡片 `AGG` 徽章 + 分组维度/聚合列信息行；验证台自动分派 **AV1-AV5**：形状契约（columns=维度+别名）/ 注入防御 / 未知参数 400 / **LIMIT 钳制 + 聚合数值性**（SUM/COUNT/AVG 别名 parseFloat）/ **发布白名单双探针**（聚合列塞 payload + 别名塞 payload 双双 400） | `dashboard/public/w5-app.js`（`aggregateSuite`） |

## E2E 实证抓出并修复的生产 bug

**MERGE 位置绑定 vs ALTER 追加列的物理列序错位**（单测全绿也拦不住的那类）：

- 现象：半 live 发布聚合服务 201 + 可查，但重启后**消失**（W6-B 老服务 `cust-orders-w6b` 却恢复）
- 根因：`MERGE INTO service_def VALUES(?×16)` 未带列名清单；生产注册表文件库是 W6-B 旧表
  ALTER 迁移而来，`aggregates` 物理上追加在**表尾第 16 列**，而绑定序把它当第 10 列 →
  aggregates JSON 落进 `DEFAULT_LIMIT`(INTEGER) → `Data conversion error` → save 静默降级内存态
- 为什么单测没拦住：测试库用新 DDL 建表，列序恰好与绑定序一致；旧行兜底测试用显式列名 INSERT，两条路径都没踩中「ALTER 追加列」的物理序
- 修复：MERGE 显式列清单（`service/JdbcRegistryStore.save`）；回归测试改为**真实迁移路径**——
  手工重建 W6-B 15 列旧表 + 插旧行，再走 store.save 聚合定义，断言旧行兜底 + 新行不错位

## 验证（全部实跑记录，2026-09-07）

- **测试**：`mvn test` **141/141 全绿**（W6-B 129 + 新增 12：
  ServiceRegistryTest +6：合法聚合发布（type/dims/isStar/sqlExpr/key）/ 未知函数 400 /
  列规则三连 400（非 COUNT 空列·标识符·目录元数据）/ 别名三连 400（撞维度·重复·不合法）/
  聚合+融合互斥 400 / 非维度列过滤放行；
  JdbcRegistryStoreTest +2：聚合往返（COUNT 星号 null 保留 + sqlExpr）/
  **旧表迁移**（15 列旧表 ALTER 追加 → save 不错位 + 旧行 NULL 兜底）；
  ServiceMarketplaceControllerTest +4：聚合金标 E2E / 非维度过滤（VIP1 只出 C0003）/
  注入 payload total=0 / 未知参数 400 + alias 注入与混报双 400）
- **半 live E2E**（om-stub :18585 + H2 mem 源池 + registry 文件库，实跑）：
  1. **金标**：发布 `orders-by-cust-w6c`（orders GROUP BY cust_id + COUNT(\*)→order_count + SUM(order_amount)→total_amount）→ 查询：
     `C0001: 2 单 / 4670.50 · C0002: 1 / 860.00 · C0003: 1 / 2180.00`（与单测金标逐字一致）
  2. **非维度过滤**：`customer-agg-vip-w6c` GROUP BY cust_id + WHERE cust_level=VIP1 → 仅 C0003 一组
  3. **注入防御**：`cust_level=VIP1' OR '1'='1` → 200 且 `total=0`（值全绑定，注释/UNION 无入口）
  4. **发布闸**：alias 塞 `total; DROP TABLE orders` → 400「聚合别名不合法」；列塞 payload → 400「聚合列名不合法」；聚合+融合同报 → 400「聚合与融合互斥」
  5. **服务 key 直调**：`sk-w6-04fd…` 仅凭自己 slug `/query` → 200（对外开放通道继承）
  6. **限流**：`rateLimitPerMin=3` 连打 → 第 4 次 **429 + `Retry-After: 60`**
  7. **重启持久化**（bug 修复后重跑）：发布 3 服务 → 重启 → slugs 全恢复，
     `orders-by-cust-w6c` **key 微秒级一致**（`sk-w6-873b…4fd7`）、aggregates 完整还原
     （COUNT→order_count / SUM→total_amount）、金标查询结果一致；
     **W6-B 老服务 `cust-orders-w6b` 同库共存恢复（200）** ← *ALTER 迁移不破坏旧数据的实证*
  8. **计量**：`GET /usage` → totalCalls=3 / todayCalls=3 / recentDays 当日 3，与手工账吻合
- **浏览器走查**：`http://localhost:3000/w5-app.html` —— 服务市场 Tab 向导开「📊 分组聚合」
  （自动关融合）→ 声明聚合列发布 → 卡片 AGG 徽章 + 维度/聚合列信息行 → 验证台选聚合服务
  → 「▶ 运行验证套件」跑 AV1-AV5（数值性校验 + 别名注入双探针）

## 边界与下一步

- 聚合+融合组合（如「按客户汇总 + 挂最近订单」）仍是 Phase 3+（互斥闸守着）
- HAVING（聚合后过滤）/ DISTINCT COUNT / 多级 GROUP BY 未纳入 DSL —— 按需扩展
- 跨源融合从表（MySQL × ClickHouse × PostgreSQL）仍限同源
- Quota 日总量（现仅每分钟限流 + 日计量）与配额策略待 W6-D+
