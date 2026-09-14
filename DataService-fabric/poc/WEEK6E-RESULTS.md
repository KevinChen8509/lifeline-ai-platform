# Week 6-E：production-audit 复审（W5–W6-D 攻击面）+ Blocker 1/2 修复

> 2026-09-14 · 分支 datafabric-week2 · 上轮审计（W4 后）4 Blocker 已闭，本轮针对 W5–W6-D 新增攻击面复审并修复两个新 Blocker。

## 一、审计结论（修复前）

**Production audit: 64/100, Risky** —— 注入双闸与 Key 作用域扎实，但默认凭据入库、服务 Key 明文存储+列表重复透出、限流按 slug 而非按 Key 三项不修不能对外开放。

### 审计范围（W5–W6-D 新增攻击面）

服务发布 DSL（table-query/fusion/aggregate）、服务级 API Key 双通道、限流计量、注册表持久化、跨源融合执行层（引号方言+库限定）。

### Blockers（本轮修复 2 项）

| # | 问题 | 证据位置 | 修复 |
|---|------|---------|------|
| B1 | 默认凭据随代码入库且静默放行：默认全局 key 长 44 > min-key-length 16，SecurityConfig 长度 WARN 不触发，生产忘配 env = 带仓库公开凭据上线（DB 密码 poc123/external123、Cube secret 同理） | `application.yml` / `SecurityConfig:54` | ✅ `ProdSecretGuard`（@Profile("prod")）命中 4 项哨兵值即拒绝启动，报出违规属性 + 对应环境变量 |
| B2 | 服务 Key 明文落库 + 列表全量重复透出：`service_def.api_key` 明文存储，`GET /api/v1/services` 每次返回明文 key（发布日志有脱敏，API 面没有） | `JdbcRegistryStore:120` / `ServiceMarketplaceController:72` | ✅ 库内只存 SHA-256 哈希；明文仅发布/轮换响应一次性返回（`KeyedServiceView`）；存量明文行 loadAll 自愈迁移；`@JsonIgnore` 保证哈希不进任何 JSON |

### 遗留 High-value（未修，下轮候选）

1. 限流窗口按 slug 不按 Key 通道（`ServiceRegistry.tryAcquire:391`）—— 平台巡检与第三方消费者互相饿死
2. 全局 key 比较非常时（`SecurityConfig:114` `String.equals`，服务 key 已是 `MessageDigest.isEqual`，同库两套标准）
3. 限流/计量单实例内存态（多实例需 Redis）；H2 文件库无备份文档
4. CI 最新 run 状态未复核（本机 gh CLI 缺失）；真 Docker 源跨源联验未做

### 优势（审计确认）

- 发布/执行双闸 DSL 校验（OM 白名单 + 标识符正则复检），注入 E2E total:0
- 全 PreparedStatement 值绑定（含跨源 IN 全绑定）
- 服务 key 仅限自身 `/query`、恒时比较、防状态 oracle 同文案 401
- LIMIT 三处钳制；`.gitignore` 保证 registry H2 不入库；143/148 测试绿

## 二、Blocker 1 实现：ProdSecretGuard

- `config/ProdSecretGuard.java`：`@Profile("prod")` + `InitializingBean`，启动时比对 4 项哨兵值
  （security.api-key / mysql-password / postgres-password / cube.api-secret），任一命中 →
  `IllegalStateException` 列出全部违规属性 + 环境变量提示（DATAFABRIC_API_KEY / MYSQL_PASSWORD / PG_PASSWORD / CUBEJS_API_SECRET）
- dev/PoC 不设 profile → 守卫 Bean 不创建，零影响（测试实证）
- 测试 3 条：完整应用 prod+默认值拒绝启动（含报错文案断言）/ 切片 prod+真实值放行 / 无 prod 守卫不激活
- 已知边界：完整应用在 prod 下还有**既有**缺口（`StubMetadataClient @Profile("!prod")` 无替身 → boot 失败）—— 不属于本守卫范围，真实 OM MetadataClient 属 Phase 3

## 三、Blocker 2 实现：Key 哈希化 + 一次性返回

### 变更面

| 位置 | 变更 |
|------|------|
| `service/ServiceKeys.java`（新） | key 生成（sk-w6-+32hex）/ `sha256Hex` / `isPlaintextFormat`（迁移判定）/ `last4` 集中一处 |
| `ServiceDefinition` | 组件 `apiKey` → `apiKeyHash`（`@JsonIgnore`，哈希不随任何 JSON 透出） |
| `ServiceRegistry` | `publish`/`rotateKey` 返回 `PublishedService(definition, apiKey)` —— 明文只经此对象一次性交给 controller；`isValidServiceKey` 入参先哈希再恒时比较 |
| `ServiceMarketplaceController` | `KeyedServiceView(service, callCount, apiKey)` 与目录视图同形 + 顶层一次性 `apiKey`；目录/详情零 key 材料 |
| `JdbcRegistryStore` | 存哈希；`fromRow` 检测存量明文（`^sk-w6-[0-9a-f]{32}$`）→ 就地 UPDATE 哈希（**自愈迁移**，原 key 继续有效，仅从此不可再展示） |
| dashboard `w5-app.js` | 卡片 key chip 改掩码提示；复制按钮并入「轮换并复制新 Key」；发布 toast 明文仅此一次 + `state.lastIssuedKey`；curl 模板留占位符 |

### 关键设计决策

- **hash 而非加密可逆存储**：key 是 128 位随机，无需找回 —— 丢失即 rotate 重发（运维语义更简单也更安全）
- **存量行自愈**而非一次性迁移脚本：升级即生效，幂等（哈希形态 64-hex 无前缀，不会误判二次迁移）
- **响应形状**：`KeyedServiceView` 扁平（service+callCount+apiKey 同级），与目录视图兼容，前端只改 key 取值路径
- api_key 列 VARCHAR(64) 恰好容纳 64 hex，无需 DDL 变更

## 四、验证

### 单元/集成测试：148/148（143 + 5 新增）

- ProdSecretGuardTest 3 条（拒绝/放行/dev 不激活）
- JdbcRegistryStoreTest +2：存量明文 loadAll 自愈迁移 + 幂等；ServiceKeys 形态判定（非 32-hex 不误判）
- ServiceRegistryTest：发布/轮换/吊销改为哈希断言（定义 64-hex、明文仅在 PublishedService）
- ServiceMarketplaceControllerTest：目录/详情 `apiKey`/`apiKeyHash` 双不存在；发布/轮换响应顶层一次性 key

### 半 live E2E（H2 双源替身 + om-stub + dashboard）

| 步骤 | 结果 |
|------|------|
| ① 列表 12 服务 | apiKey/apiKeyHash 泄漏条目 **0** |
| ② 存量明文 key（W6-D 的 sk-w6-72e7…）自愈迁移后继续有效 | 200，跨源金标（张伟 + risk_tags{high,82}）与 W6-D 一致 |
| ③ 发布 keyless-demo-w6e | 201 响应顶层 `apiKey`（38 字符，仅此一次），service 内无 key；直查 200 王芳 |
| ④ 轮换 | 旧 key 401 / 新 key 200 张伟 / 响应一次性返回 |
| ⑤ 重启持久化 | 轮换后 key 重启仍有效；w6d 老 key 二次重启仍有效 |
| ⑥ 库内实证（H2 Shell） | 6 行 api_key **全部 64 位哈希**，无任何明文 |

## 五、边界与后续

- revoke 未在半 live E2E 重跑（逻辑无改动，控制器测试覆盖 401/全局照常路径）
- 迁移后 key 的 last4 不再持久化（卡片只显掩码）—— 发布/轮换 toast 与剪贴板是唯一获取点
- 完整 prod 部署还需：真实 MetadataClient 替身（Phase 3）+ 遗留 High-value 1/2
