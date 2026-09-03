# Week 6 结果 · 跨表融合定制接口 + 服务级 API Key 对外开放

> 需求原点：「根据已存的数据资源表，按业务场景自定义数据融合形成第三方定制接口 ——
> 如按客户编号同时取客户基础信息和名下订单，并验证正确性和对外开放」。
>
> W6-A 落地：**受限声明式融合 DSL**（不开放任意 SQL）+ **融合版验证台 FV1-FV7** +
> **服务级 API Key**（发布即生成，仅可调自身 `/query`）—— 复用 W5 全部防注入闸门。

## 能力清单

| 能力 | 实现 | 位置 |
|---|---|---|
| 融合 DSL | 发布体新增 `joins[]`（fqn/name/columns/joinColumn/parentColumn/limitPerParent）：从表列+关联键**逐字命中 OM 元数据**、parentColumn ∈ 主表白名单、joins≤2、每主键 1-50；`type=fusion` | `service/ServiceRegistry.java` |
| 两段式融合执行 | ① 主表参数化查询 → ② 收集主行 parentColumn 值去重 → `WHERE joinCol IN (?,?,…)` 全绑定查从表 → 应用层按 joinColumn 分组 → 每主行挂 `name` 嵌套数组（cap limitPerParent）；标识符执行前二次正则复检 | `api/ServiceMarketplaceController.java` |
| 融合响应契约 | `{slug, columns, joins:[{name,columns}], rows:[{…主列, orders:[…从列]}], total, elapsedMs}` —— 嵌套数组字段名 = 发布声明的 name | 同上 |
| 服务级 API Key | 发布即生成 `sk-w6-` + 32 hex（SecureRandom）；SecurityConfig **双通道**：全局 key=平台全权限 / 服务 key 仅绑定 slug 的 `/query`（路径正则 + `MessageDigest.isEqual` 恒时比较），其余一律 401 | `config/SecurityConfig.java` |
| 融合发布向导 | 发布向导新增「跨表融合」开关：从表下拉（目录联动）· 从表列勾选 · 关联键⇠主表列联动（主表勾选变化实时刷新）· 每主键上限 | `dashboard/public/w5-app.*` |
| fusion 卡片 | JOIN 徽标 + 从表关联信息（FQN/关联键/每主键上限）+ **apiKey 打码展示与一键复制** + curl 复制内嵌服务 key（第三方直调模板） | 同上 |
| 融合验证台 FV1-FV7 | 验证台按 type 分派：单表跑 V1-V5，融合跑 **FV1 形状 · FV2 注入(0主行0从行) · FV3 未知参数 · FV4 主行≤500+每主键≤cap · FV5 从表列注入发布闸 · FV6 关联一致性(每从行 joinColumn==主行值) · FV7 金标(C0001→张伟+2单)** | 同上 |

## 验证

- **测试**：`mvn test` **112/112 全绿**（W5 99 + 新增 13：ServiceRegistryTest 7 融合/key 用例 + ServiceMarketplaceControllerTest 6 端到端）
  - 金标：发布 fusion → query C0001 → 主行张伟 + `orders.length==2`
  - 注入：`C0001' OR '1'='1` → 0 主行 0 从行；从表列塞 `cust_id; DROP TABLE orders` 发布 → 400
  - 钳制：`limitPerParent=1` → orders 只出 1 条（金标 2 条被截）
  - 服务 key：自己 slug 200 / 别人 slug 401 / POST 发布 401
- **半 live 端到端**（om-stub 新增 orders 表 + H2 金标种子 O1001,O1002→C0001 / O1003→C0002 / O1004→C0003，实跑记录）：
  1. 发布 `cust-orders-fusion`（customer 主表 ⇠ orders 从表，cust_id 关联）→ **201 + apiKey `sk-w6-…`**
  2. `?cust_id=C0001` → **张伟 + orders 嵌套 2 条**（O1001/1290.00、O1002/3380.50），551ms
  3. 注入 payload → `total:0`（16ms）；未知参数 → 400
  4. 服务 key 四态：自己 200 / 别人 401 / 服务列表 401 / 无 key 401
  5. dashboard 代理链 `/api/w5/*`：fusion 卡片（JOIN 徽标 + apiKey）+ 查询全通
- **浏览器走查**：向导勾「跨表融合」→ 从表/关联键联动选择 → 发布 → 卡片 apiKey 复制 → 验证台 FV1-FV7

## 需求逐项对照

| 原始需求 | 落地 |
|---|---|
| 根据已存数据资源表 | 主表/从表均来自 `/api/v1/catalog/tables`（OM 目录） |
| 按业务场景自定义数据融合 | 受限 DSL `joins[]`：列/关联键白名单声明，无需写 SQL |
| 客户编号 → 基础信息 + 订单 | 金标用例：C0001 → 张伟行 + orders 嵌套 2 条 |
| 验证正确性 | FV1-FV7（含关联一致性 FV6 + 金标 FV7）+ mvn 112/112 |
| 对外开放 | 服务级 API Key：发布即生成，仅可调自身 /query，第三方 curl 直调 |

## 边界（Phase 3+ 待办）

- DSL 单层融合（joins≤2、不递归）；聚合列（SUM/COUNT）与跨源融合未开放
- apiKey 注册表内存态：重启即换 key，无吊销/过期/审计分发流程
- 无按 key 限流与计量计费（W6-B 候选）
- 从表查询与主表同池串行两段（无并行优化；PoC 数据量无感）
