# 整体性测试验证 — 从数据接入到数据服务（E2E Full-Chain）

> PoC 收口级整体验收：一条命令把全链路串起来 —— 数据接入（双源）→ 元数据目录 →
> 服务化四形态发布 → 安全治理 → 运营（Key/限流/计量）→ 消费通道 → 重启持久化 →
> 消费代理。区别于 W1-W6 各周**分域局部**验证，本篇验证**全链路协同**。

## 一键命令

```bash
cd poc
bash scripts/e2e-full-chain.sh
```

- **栈（不依赖 Docker，复用 w4-demo.sh 配方）**：
  1. `om-stub :18585` — OpenMetadata 4 表元数据桩
  2. `data-service :8090` — MySQL/PG 双源 H2 替身 + 文件注册表 `./data/e2e-fullchain`
  3. `dashboard :3210` — 消费代理层
- **可重复跑**：slug 带时间戳（`e2efc-<ts>-*`）不冲突；注册表独立文件库（不动开发库 `./data/registry`）
- **耗时**：~3-5 分钟（含一次 data-service 重启）
- **exit code** = 失败断言数（0 = 全绿）
- **日志留档**：`scripts/.e2e-last/{svc,dash,om}.log`（每次跑覆盖）

## 范围与范围外

| | 说明 |
|---|---|
| **范围内** | 半 live 栈全链路：双源 H2 替身接入（懒启动池）→ OM stub 目录 → 四形态发布+金标 → 安全（鉴权/注入双闸/钳制/撞名闸）→ 运营（rotate/revoke/限流/计量）→ 服务 key 双通道隔离 → 重启恢复 → dashboard 代理 |
| **范围外** | W1 Trino / W2 Cube（Docker 依赖）→ **L0 断言其降级契约**：502 `SEMANTIC_LAYER_UNAVAILABLE` + 文案不泄漏内部 URL（B4 回归）；W3 LLM Agent → `w4-demo.sh` / `red-team-b-path.sh` 已覆盖 |

## 分层断言矩阵（47 项）

| 层 | 项 | 验证内容 |
|---|---|---|
| **L0 平台健康** | 4 | health 无鉴权 UP；pools 三源在列；W2 profile 离线 502 降级契约；502 文案不泄漏内部 URL（B4） |
| **L1 元数据目录** | 5 | 目录 4 表 + degraded=false；四 FQN 逐字在列（发布校验的元数据闸来源） |
| **L2 服务化四形态** | 9 | 发布 table-query/fusion/aggregate/agg-fusion 各 201；四形态金标：C0001=张伟/VIP3/北京 · fusion 张伟+2 单（子行透出关联键）· aggregate 2 单/4670.50 · **agg-fusion 跨源 PG** 2/4670.50 + risk_tags[0]{high,82}；无过滤 3 组挂载 high/medium/low 各归其主 |
| **L3 数据接入** | 3 | mysql 池 STARTED（首查懒启动）；postgres 池 STARTED（跨源 join 触发）；clickhouse NOT_STARTED（未用不启动） |
| **L4 安全治理** | 8 | 无 key/错 key 401；注入 payload（table-query + agg-fusion）→ 200 total=0；未知参数 400；limit=99999 钳制 ≤500；发布白名单闸（注入列 400）；agg-fusion 撞名闸（join.name=顶层别名 400） |
| **L5 服务运营** | 8 | rotate 新 key 200 + 旧 key 401；revoke 后 401；rate=2/min 前两次 200 + 第 3 次 429+Retry-After=60；usage 成功 2 次计入（401/429 不计） |
| **L6 消费通道** | 3 | 服务 key 调自身 /query 200 金标；调他人 /query 401；调管理端点 401（双通道隔离） |
| **L7 持久化** | 4 | 重启后 health UP；5 个 e2efc 服务全恢复；agg-fusion type/joins/aggregates 三保留；**重启前发的服务 key 仍 200 金标逐字复现** |
| **L8 消费代理** | 3 | dashboard /api/w2/health 代理连通；/api/w5/catalog 4 表；/api/w5/services ≥5 个 e2efc 服务 |

## 实测实录（2026-09-16，run 3，slug 前缀 `e2efc-1789551464`）

```
━━━ L0 平台健康 ━━━
  [PASS] health 无鉴权 UP · HTTP 200
  [PASS] pools 端点 200 且三源在列 · HTTP 200
  [PASS] W2 profile 离线降级 502 SEMANTIC_LAYER_UNAVAILABLE · HTTP 502
  [PASS] 502 文案不泄漏内部 URL（B4） · message=语义层暂时不可用，请稍后重试
━━━ L1 元数据目录 ━━━
  [PASS] 目录 4 表且未降级 · HTTP 200 · 4 表 · degraded=false
  [PASS] 目录含 mysql.customer_db.customer / mysql.customer_db.orders / clickhouse.orders_db.orders / postgres.external.risk_tags
━━━ L2 服务化四形态 ━━━
  [PASS] 发布 table-query 201 + 服务 key · key=sk-w6-5c9…
  [PASS] table-query 金标 C0001=张伟/VIP3/北京（接入数据活体） · HTTP 200
  [PASS] 发布 fusion 201 · type=fusion
  [PASS] fusion 金标 C0001=张伟+2 单（子行透出关联键） · HTTP 200 · 2 单
  [PASS] 发布 aggregate 201 · type=aggregate
  [PASS] aggregate 金标 C0001 → 2 单 / 4670.50 · HTTP 200
  [PASS] 发布 agg-fusion 201 + 服务 key（L6/L7 复用） · type=agg-fusion
  [PASS] agg-fusion 金标 C0001 → 2/4670.50 + risk_tags[0]{high,82}（跨源 PG 方言） · HTTP 200
  [PASS] agg-fusion 无过滤 → 3 组全挂载 high/medium/low 各归其主 · total=3
━━━ L3 数据接入 ━━━
  [PASS] mysql 池 STARTED（首查后懒启动） · idle=1
  [PASS] postgres 池 STARTED（跨源 join 触发）
  [PASS] clickhouse 池 NOT_STARTED（未用不启动，预期）
━━━ L4 安全治理 ━━━
  [PASS] 无 key → 401 / 错 key → 401
  [PASS] table-query 注入 payload → 200 total=0 / agg-fusion 注入 payload → 200 total=0
  [PASS] 未知参数 → 400 · BAD_REQUEST
  [PASS] limit=99999 → 钳制 ≤500 不失控 · 3 行
  [PASS] 发布白名单闸：注入列 → 400 / agg-fusion 撞名闸：join.name=顶层别名 → 400
━━━ L5 服务运营 ━━━
  [PASS] rotate → 新 key 返回 / 新 key 直调 /query → 200 / 旧 key → 401（恒时比较同文案）
  [PASS] revoke → 200 / 吊销后 key → 401
  [PASS] 限流：前两次 200 · 第 3 次 → 429 + Retry-After · Retry-After=60
  [PASS] usage：成功 2 次计入（429/401 不计） · totalCalls=2
━━━ L6 消费通道 ━━━
  [PASS] 服务 key 调自身 /query → 200 金标 · 4670.50
  [PASS] 服务 key 调他人 /query → 401 / 调管理端点 → 401
━━━ L7 持久化 ━━━
  [PASS] 重启后 health UP
  [PASS] 5 个 e2efc 服务全部恢复 · 5 个
  [PASS] agg-fusion type/joins/aggregates 三保留 · type=agg-fusion · joins=1 · aggregates=2
  [PASS] 重启前发的服务 key 仍 200 金标逐字复现 · 2/4670.50/high
━━━ L8 消费代理 ━━━
  [PASS] dashboard /api/w2/health 代理连通 · HTTP 200
  [PASS] dashboard /api/w5/catalog 4 表 · HTTP 200
  [PASS] dashboard /api/w5/services ≥5 个 e2efc 服务 · 5 个

分层汇总
  L0 平台健康        4 PASS  0 FAIL
  L1 元数据目录      5 PASS  0 FAIL
  L2 服务化四形态    9 PASS  0 FAIL
  L3 数据接入        3 PASS  0 FAIL
  L4 安全治理        8 PASS  0 FAIL
  L5 服务运营        8 PASS  0 FAIL
  L6 消费通道        3 PASS  0 FAIL
  L7 持久化          4 PASS  0 FAIL
  L8 消费代理        3 PASS  0 FAIL
 ✓ 整体性验证通过：47/47 —— 从数据接入到数据服务全链路 green
```

### 可重复性

- **连跑第二次**（slug 前缀 `e2efc-1789551585`，同一文件注册表累加）：**47/47 全 PASS，exit 0**
- 注册表旧服务（前次 run 的 e2efc-*）与新 run 共存互不干扰；断言均锚定**当前时间戳前缀**，历史服务仅自然累加。

## 金标数据（H2 双源种子）

| 源 | 表 | 金标 |
|---|---|---|
| MySQL 替身（MODE=MySQL） | customer_db.customer | C0001=张伟/VIP3/北京 |
| MySQL 替身 | customer_db.orders | C0001=2 单/4670.50（O1001 1290 + O1002 3380.50） |
| PG 替身（MODE=PostgreSQL） | external.risk_tags | C0001=high/82 · C0002=medium/55 · C0003=low/12 |

## 调试过程中踩到的环境坑（已固化进脚本）

1. **MSYS `$PWD` 是 `/e/...` POSIX 路径**：H2 `INIT=RUNSCRIPT FROM` 的 JDBC URL 必须是
   Windows 盘符路径（`E:/...`），否则 H2 打不开种子文件 → 池 total=0 连接建不起来
   （被 `initializationFailTimeout=-1` 掩盖）→ 所有查询 502。脚本用 `cygpath -m` 转换。
2. **端口 3000 落在本机 Windows WinNAT 排除区间**（2929-3028，`netsh interface ipv4
   show excludedportrange protocol=tcp` 可查）→ dashboard EACCES。脚本默认 `DASH_PORT=3210`
   （env 可覆盖）。
3. **孤儿进程**：历史 run 的 java/node 可能残留占端口 → 脚本起栈前 `free_port` 按端口
   taskkill 兜底（L7 重启链同法）。
4. **cleanup trap 先留档再删临时目录**：早期版本 trap 直接 `rm -rf` 把诊断日志销毁 →
   现固化 `scripts/.e2e-last/` 留档。

## 边界

- W1 Trino 联邦 / W2 Cube 语义层 / W3 LLM Agent 的**活体**验证依赖 Docker/Ark key，
  本脚本仅断言其降级契约（L0）——活体已分别由 WEEK1-RESULTS / w4-demo.sh / red-team-b-path.sh 覆盖。
- OM 离线降级路径不重跑（F3 单测覆盖，TTL 缓存引入会 flaky）。
- CI 不集成（本地 mvn spring-boot:run 双进程重启不适合 GitHub Actions）——保持本地验收定位。
