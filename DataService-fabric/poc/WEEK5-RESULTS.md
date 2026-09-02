# Week 5 结果 · 数据服务市场（数据资源目录 + 业务场景 + 自助发布 API）

> 定位：团队章程支柱④「数据服务网关 + Marketplace 雏形」的 PoC 落地（Phase 2 关键产出：
> `/api/v1/catalog/*` + 管控台 + Marketplace PoC）。
>
> 核心叙事：以**目录驱动的数据服务体系**取代共享开放中的**点对点定制开发** ——
> 消费方从目录发现数据 → 按业务场景对齐口径 → 勾选列/参数**自助发布**参数化 API → 立即可调。

## 能力清单

| 能力 | 实现 | 位置 |
|---|---|---|
| 数据资源目录 | `/api/v1/catalog/tables`：OpenMetadata 表元数据整形（FQN 拆 source/database/table、域映射、列注释透出；OM 离线 degraded 降级不阻断） | `api/CatalogController.java` |
| 服务目录 | `/api/v1/services`：builtin 6 条（画像/简报/分群/总览指标/Agent 双路）+ 自助发布条目 + 调用计数 | `service/ServiceRegistry.java` |
| 自助发布 | `POST /api/v1/services`：slug/列白名单/operator/defaultLimit 校验；**列必须逐字命中 OM 元数据**（防注入第一道闸；OM 离线拒绝发布不降强度） | 同上 |
| 参数化执行 | `GET /api/v1/services/{slug}/query`：标识符二次正则复检 + 全值 PreparedStatement 绑定 + LIMIT 钳制 500 + 单行 JSON `SERVICE_CALL_LOG` 落账；复用 F8 三源 HikariCP 池 | `api/ServiceMarketplaceController.java` |
| 服务下线 | `DELETE /api/v1/services/{slug}`（builtin 拒删） | 同上 |
| 服务验证台 | `/w5-app.html` 第 4 Tab：对已发布服务跑 5 项契约验证（V1 正常调用形状 · V2 注入防御 · V3 未知参数拒绝 · V4 LIMIT 钳制 · V5 发布白名单闸红队探针），PASS/FAIL/SKIP 徽章 + 汇总裁决「验证通过可交付」；发布成功与市场卡片均有「去验证」深链 | `dashboard/public/w5-app.*` |
| 可视化 | `/w5-app.html` 四 Tab：数据资源目录（搜索/域过滤/发布直达）· 业务场景（客户域真执行 + 城市生命线展示型）· 服务市场（对比条 + 发布向导 + builtin/已发布试调台 + curl 复制）· 服务验证 | `dashboard/public/w5-app.*` |

## 验证

- **测试**：`mvn test` **99/99 全绿**（存量 79 + 新增 20：ServiceRegistryTest 11 / ServiceMarketplaceControllerTest 7 / CatalogControllerTest 2）
  - 注入用例：`VIP3' OR '1'='1` 走参数绑定 → 0 行零泄露（与 F4 红队同一防线）
  - 发布校验：`cust_level; DROP TABLE customer` 列名 → 400
- **半 live 端到端**（om-stub + H2 替身栈，实跑记录）：
  1. 发布 `vip-customer-query`（5 列 + cust_level eq + region like）→ 201
  2. 试调 `?cust_level=VIP3` → 张伟行，553ms
  3. 注入 payload → `total:0`（1ms）
  4. 目录列表 = 6 builtin + 1 已发布
- **浏览器走查**：目录 3 表 → 场景卡（客户域试调 / 生命线展示）→ 向导发布 → 试调台出数 → curl 复制 → **验证台 5 项全 PASS**（V1 形状契约 / V2 `total:0` / V3 `400 未注册的过滤参数` / V4 钳制 `total:3≤500` / V5 `400 列名不合法`，活栈 curl 逐项复核实录）

## 新旧模式对比（页面顶部对比条）

| | 点对点定制开发 | 目录自助服务 |
|---|---|---|
| 链路 | 需求→排期→开发→联调→上线 | 目录发现→勾列配参→发布→立即可调 |
| 周期 | 周级 | **分钟级** |
| 安全 | 各写各的（注入/越权靠自觉） | 白名单 + 参数化 + 调用落账内建 |
| 口径 | 接口间漂移 | 目录元数据统一 |

## 边界（Phase 3+ 待办）

- 注册表内存态（重启即清）→ 持久化
- 无订阅 / API Key 分发流程（当前共用 X-API-Key）
- 无限流 / 计量计费；SERVICE_CALL_LOG 未接 W4 可观测台视图
- 城市生命线场景为展示型（数据待接入）；发布仅 mysql 源可真执行（CH/PG 池懒启动，源不可达 502）
- OM glossary/domain 未接（域映射静态三表）
