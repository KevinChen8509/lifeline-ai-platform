# Week 6-B 结果 · 服务运营化 — 注册表持久化 + Key 生命周期 + 按服务限流计量

> 需求原点：WEEK6-RESULTS.md 边界节三条 Phase 3+ 待办 ——
> **注册表内存态（重启即丢、Key 会换）/ 无 Key 吊销与过期 / 无按 key 限流与计量**。
>
> W6-B 落地：第三方正式开放运营的三块地基 —— 服务注册表落 **H2 文件库**（重启不丢、Key 不换）、
> **Key 生命周期**（轮换/吊销/有效期）、**按服务限流**（429 + Retry-After）与**每日用量计量**。

## 能力清单

| 能力 | 实现 | 位置 |
|---|---|---|
| 注册表持久化 | 独立 H2 文件库 `datafabric.registry-db.*`（默认 `jdbc:h2:file:./data/registry`，env `REGISTRY_DB_URL/USER/PASSWORD`）——与三源 raw-db 池分离（半 live MySQL 池是 H2 mem，混用会架空持久化）；两表 `service_def`（15 列，列白名单/filters/joins 序列化 JSON VARCHAR）+ `service_usage`（slug+day 主键）；MERGE INTO 幂等 UPSERT；首访问 lazy 建表 | `config/RegistryDbProperties` / `config/RegistryDbConfig`（懒 Hikari 池，镜像 F8 踩坑模式）/ `service/JdbcRegistryStore` |
| 持久化降级 | registry DB 不可达 boot 不炸：loadAll 失败 ERROR+空表，save/delete/recordUsage 失败仅 WARN —— 内存注册表照常服务当次会话 | `service/JdbcRegistryStore` |
| 重启装载 | `@PostConstruct loadPersisted()` 灌回 published（含 apiKey/keyPolicy/joins 全量）——**重启后 Key 不变** | `service/ServiceRegistry` |
| Key 生命周期 | `KeyPolicy(status, expiresAt, rateLimitPerMin)` 嵌套 record + `isUsable()`；发布可选 `keyTtlHours`（1-87600，空=永久）与 `rateLimitPerMin`（1-600，默认 60）；`POST /{slug}/key/rotate`（重新生成 key + 复活，expiresAt 沿用）；`POST /{slug}/key/revoke`（REVOKED 立即 401） | `service/ServiceDefinition` / `service/ServiceRegistry` / `api/ServiceMarketplaceController` |
| Key 校验收紧 | `isValidServiceKey`：恒时比较 AND keyPolicy.isUsable()（未吊销且未过期）；吊销/过期与错误 key 同文案 401（防 key 状态 oracle），服务端 WARN 留痕 | `service/ServiceRegistry` |
| 按服务限流 | 固定窗口每分钟每 slug：`ConcurrentHashMap.compute` 原子滑动；`/query` 执行前 `tryAcquire` 失败 → **429 `RATE_LIMITED` + `Retry-After: 60`**；被限流的调用不计入用量 | `service/ServiceRegistry` / `api/ServiceMarketplaceController` |
| 每日计量 | 成功调用 `recordUsage`（当日 +1，UPDATE→INSERT）；`GET /{slug}/usage` → `{totalCalls, todayCalls, recentDays[14]}`；与内存 callCount 并行 | `service/JdbcRegistryStore` |
| 前端运营位 | 向导高级区 +「限流（次/分）」「Key 有效期（小时）」；卡片 key 行升级：状态徽章（●有效/⛔已吊销/⏰已过期）+ 🔄轮换（新 key 一键复制）/⛔吊销/📊用量（内联 totalCalls/todayCalls + 近 7 天条形）三按钮；429 专属 toast；验证台契约快照同步徽章 | `dashboard/public/w5-app.*` |

## 验证（全部实跑记录，2026-09-04）

- **测试**：`mvn test` **129/129 全绿**（W6-A 112 + 新增 17：
  ServiceRegistryTest +7：isUsable 矩阵 / ttl+rate 发布透出 / rate 越界 400 / rotate 换 key 即失效 /
  revoke+rotate 复活 / tryAcquire 窗口钳制 / usage 计数 / loadPersisted 重启还原同 key；
  JdbcRegistryStoreTest 4：全字段往返（含 joins/Instant）/ upsert 覆盖 / delete / recordUsage 累计；
  ServiceMarketplaceControllerTest +5：rotate E2E（旧 401 新 200 服务 key 自 rotate 401）/ revoke 401 /
  连打 3 次限 2 → 第 3 次 429+Retry-After / usage 计数+404 / 发布 policy 201 透出）
- **半 live 重启持久化链**（om-stub :18585 + H2 mem 源池 + **registry 文件库**，实跑）：
  1. 发布 `cust-orders-w6b`（customer ⇠ orders 融合，rateLimitPerMin=5，ttl 24h）→ **201 key A `sk-w6-9603…932`**，keyPolicy ACTIVE/2026-09-05 到期/5 每分
  2. key A `?cust_level=VIP1` → **200**（李娜 + orders 嵌套 1 条，28ms）
  3. **重启 data-service** → 服务列表：同一 key A + **微秒级一致**的 expiresAt（`06:28:16.625540Z`）→ key A 再查 **200** ← *持久化铁证（源数据靠 INIT=RUNSCRIPT 重播）*
  4. rotate → **key B**；key A 立即 **401**；expiresAt 原样沿用
  5. revoke → key B **401**，keyPolicy=REVOKED/usable=false
  6. **二次重启** → REVOKED 状态持久，key B 仍 **401**
  7. rotate 复活 → key C ACTIVE；连打 6 次（限 5/分）→ 前 5 次 **200**，第 6 次 **429 + `Retry-After: 60`**
  8. `GET /usage` → **totalCalls=7 / todayCalls=7**：boot1×1 + boot2×1 + boot3×5，与手工账完全吻合；401/429 正确不计
- **dashboard 代理链**：`/api/w5/services/:slug/{key/rotate,key/revoke,usage}` 三路由实跑通（usage 7/7 透传）
- **浏览器走查**：向导带限流/有效期发布 → 卡片状态徽章 + 三按钮（轮换弹新 key、吊销变红、用量展开近 7 天条形）

## 边界（Phase 3+ 待办）

- 限流为单实例内存固定窗口（多实例需 Redis/滑窗）；Quota（按日总量硬限）未做，当前只有速率限
- registry 文件库单写者（H2 file 不支持多实例并发共享）
- 无按 key 维度的分布计量（计量粒度=服务级，key 级计费待做）
- rotate 无冷却期/审批流（生产需审计分发流程；当前仅服务端 WARN + 审计日志）
