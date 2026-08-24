# Week 4 · F 系列生产化加固 + Agent 可观测台

> **结论先行**：Week 3 报告列出的 10 项候选（F1-F10）**全部交付**，外加 F11 兼容修复与 W4 可视化收口。测试从 27/27 增至 **79/79**；过程中发现并修复 **2 个真实生产缺陷**（clickhouse-jdbc 打包裁剪、W2 面板鉴权回归）。Agent 层从"能演示"升级为"可观测、可追溯、可计费、可红队"。

---

## 1. 范围与交付状态

| # | 主题 | 交付物 | 测试 | Commit |
|---|------|--------|------|--------|
| F11 | LangChain4j ↔ GLM-4.7 工具调用兼容 | SanitizingToolExecutor 兜底残缺 `{` args | — | `af2206b` |
| F1 | LangChain4j 0.36.2 → 1.0.0 | ChatLanguageModel→ChatModel 等 API 迁移 | — | `2b51d0f` |
| F5 | SSE 流式端点 + 升 1.18.1 | `/agent/{insight,raw}/stream`（SseEmitter） | 30/30 | `60853e7` |
| F9 | Trace 追溯端点 | `/agent/trace/{requestId}`，requestId 贯穿治理全链路 | 39/39 | `386f85a` |
| F10 | Pact Provider 验证 | dashboard↔data-service 契约锁定 5 交互 | 44/44 | `35e3b08` |
| F6 | Token 计数与成本 | `/agent/usage` 聚合 + 分路径 + 成本估算 | 52/52 | `8087697` |
| F2 | 工具调用日志 | TOOL_CALL_LOG 结构化日志 + `/agent/tool-calls` | 60/60 | `8d9d8f3` |
| F7 | 工具粒度细化 | 服务端 riskLevel 过滤 + 列表 slim() 投影 | 63/63 | `1d2baf3` |
| F8 | B 路连接池 + 重试 | HikariCP 三源懒启动池 + JdbcRetry + `/raw-db/pools` | 70/70 | `f3b0eaa` |
| F4 | 红队注入演示 | 4 用例 CI + 6 步活体演示 + `RED-TEAM-DEMO.md` | 74/74 | `2006fa3` |
| F3 | A 路 RAG 上下文 | OpenMetadata 表注释注入 + RAG_CONTEXT 进 trace | 79/79 | `3cc31b4` |
| W4 | Agent 可观测台 | `/w4-app.html` + 6 条 `/api/w4/*` 代理 + 半 live 栈 | 联调验证 | `de4f283` |

框架终态：**LangChain4j 1.18.1**（0.36.2 → 1.0.0 → 1.18.1 两跳升级，全程 `AiServices.builder` 不用 `@AiService` 注解）。

---

## 2. 架构（Week 4 终态）

```
                 ┌───────────────────────── data-service (Spring Boot 3.5) ─────────────────────────┐
                 │                                                                                   │
  POST /agent/insight[/stream] ─► CustomerInsightAgent ─┬─ RAG: MetadataContextService ─► OpenMetadata │
  (A 路·治理)                        [LangChain4j 1.18.1]  │   (TTL 缓存 + 打分 top-K + 中文术语表)        │
                 │                                        └─ @Tools(RestClient 自环 /api/v1/*) ─► 三切面   │
                 │                                             DataMaskingAspect / AuditAspect / LineageAspect
  POST /agent/raw[/stream] ────► RawDbAgent ── @Tools(JDBC PreparedStatement) ─► HikariCP 三源池 ─► MySQL/CH/PG │
  (B 路·对照)                                │                   └ JdbcRetry(08*/SQLTransient, 2次+200ms)
                 │                                                                                   │
                 │   可观测层（F2/F6/F8/F9 内存存储 + 查询端点）                                          │
                 │   ToolCallLogger ─► /agent/tool-calls      TokenUsageStore ─► /agent/usage        │
                 │   TraceStore     ─► /agent/trace/{id}      HikariMXBean    ─► /raw-db/pools       │
                 └───────────────────────────────────────────────────────────────────────────────────┘
                                          ▲  X-API-Key（B2 鉴权）
                 dashboard server.js（/api/w4/* 代理：SSE 透传 / trace / usage / tool-calls / pools / redteam）
                                          ▲
                 /w4-app.html（双路流式 + 红队面板 + trace 时间线 + 可观测三面板）  /w3-app.html（同步对比）
```

---

## 3. F 系列逐项要点

### F11+F1+F5 · 框架升级三连（0.36.2 → 1.18.1）
- **F11 兜底**：GLM-4.7 工具调用偶发只回 `{`，LangChain4j 0.36.2 直接抛解析异常 → `SanitizingToolExecutor` 包一层，残缺 args 兜底为空 Map 让 LLM 自行重试
- **F1 迁移**：1.0.0 改名 `ChatLanguageModel`→`ChatModel`、弃用 spring-boot-starter；wrapper 保留（1.0.0 仍未兜底 `{`）
- **F5 流式**：`TokenStream`→`SseEmitter`，事件 `{type:token|done|error}`，done 帧回传 `requestId`；流式测试逼出 GLM-4.7 `reasoning_content` 解析 bug → **升 1.18.1 零代码改动修复**

### F9 · requestId 贯穿治理全链路
- Agent 入口分配 8 位 requestId → `X-Request-Id` 头随 A 路工具自环调用穿透 DispatcherServlet → 审计/血缘切面挂到同一 id
- `TraceStore` 聚合 5 类事件：**REQUEST / RAG_CONTEXT（F3 后）/ TOOL_CALL / AUDIT / LINEAGE**，内存 FIFO 100 条
- 流式路径的治理事件在回调线程，工具级 ThreadLocal 不可见 → request 级 trace 保底

### F10 · Pact Provider 契约锁定
- dashboard（消费方）↔ data-service（提供方）5 交互：200×2 / 401（无 key）/ 400（空 question）/ 404（trace 不存在）
- 坑：`pact.provider` 必须用 `junit5spring`；Windows GBK 读花 UTF-8 契约 → surefire `-Dfile.encoding=UTF-8`

### F6+F2 · 用量与工具遥测
- **F6 不双计**：同步路径 `ChatModelListener` + `UsageContext` ThreadLocal；流式路径 `onCompleteResponse` 直记——两路互斥。粒度=单次 LLM 调用（工具循环多趟同 requestId），`/usage` 出全局+分路径聚合+按单价估算成本
- **F2 铁律**：summary 只放 `rows=N / ERROR:xxx`，**绝不放返回行内容**（B 路含 PII 明文，遥测层不能成为旁路泄漏点）；单行 JSON 走独立 logger `TOOL_CALL_LOG`（Loki/CH 友好）+ 200 FIFO + 按 tool 聚合

### F7 · 工具粒度细化（一次"客户端过滤 hack"的清理）
- 原 `getHighRiskCustomers` 在工具里拉全量再内存过滤 → **>200 行静默截断 bug** → 删掉
- search 走服务端 `riskLevel` 过滤（透传 Cube filter）；列表类工具 `slim()` 7 字段投影（脱敏后的 `138****0001` 对 LLM 是纯噪音），单客户画像保持全量——脱敏演示是 A 路卖点

### F8 · B 路连接池 + 瞬时重试
- **HikariCP 7 急切启动坑**：`new HikariDataSource(config)` 构造即启动池 → surefire JVM 无 DB → ClickHouseDriver 静态初始化炸 → 36 个 @SpringBootTest 全挂 → **无参构造 + setter 懒启动**
- `JdbcRetry`：瞬时判定 SQLTransient/SQLRecoverable/SQLState 08*（walk cause chain），2 次 + 200ms；非瞬时立即上抛
- `/raw-db/pools`：active/idle/total/awaiting；HikariCP 7 MXBean 无 `getState()`，用 pool==null 区分 NOT_STARTED

### F4 · 红队演示（安全叙事定稿）
- **双防线各自独立成立**：模型层拒答可被社工话术绕过（"客服录错的客户 ID"）→ 工具层 PreparedStatement 把 payload 当字面量 → `rows=0`；tool-calls 日志 `args.custId` 存完整 payload + `ok:true, summary:"rows=0"` 为铁证
- **真暴露不是注入**：合法查询 B 路 PII 明文 + 零审计 —— 治理缺失才是风险本体（A/B 对照的价值主张）
- CI 用例：H2 `MODE=MySQL` 替身（OR 恒真 0 行 / 堆叠 DROP 表完好 / 拼接对照泄露全表）

### F3 · A 路 RAG（刻意非向量）
- 检索：表 token +3 / 列 token +2（**去重**防 `customer_db.customer` 重复刷分）+ 中文→英文术语表 12 条（订单→order/客户→cust/风险→risk，子串命中免中文分词）
- 注入：`augment()` 产增强问题喂 agent；`RAG_CONTEXT`（tables + augmentedChars）进 trace 时间线
- 韧性：TTL 缓存**连空结果一起缓存**（防 OM 离线重试风暴）；OM 不可达 → 问题原样透传，A 路不阻断

---

## 4. 过程中发现并修复的生产缺陷

| 缺陷 | 症状 | 修复 |
|------|------|------|
| **clickhouse-jdbc 0.6.5-all 打包裁剪** | minimizeJar 裁掉 `ClickHouseClient` 接口但 `ClickHouseDriver.<clinit>` 引用它 → 驱动注册后静态初始化失败 → **DriverManager 队列里其后所有连接全炸**（含 B 路 CH 线上查询，被懒启动+未调用掩盖） | pom 显式补 `clickhouse-client:0.6.5`（F4 附带产出） |
| **W2 面板 401 回归** | B2 Spring Security 上线后，dashboard 的 profile/customers/metrics/audit 四面板没带 X-API-Key | `authHeaders()` 统一注入（W4 附带产出） |
| **undici SSE 假异常** | Node fetch 对 Spring SseEmitter 正常收尾的流额外抛 `terminated`（done 帧其实已完整送达） | 透传尾部 256 字符含终止帧则吞掉异常 |
| **H2 INIT 每连接重跑** | `INIT=RUNSCRIPT` 在每条新连接执行 → 池第 2 连接撞主键 → `SQLTransientConnectionException`（首连成功极具迷惑性） | 种子脚本 `MERGE INTO ... KEY(cust_id)` 幂等 |

---

## 5. W4 · Agent 可观测台（`/w4-app.html`）

F 系列端点的收口可视化，六块能力：

| 区块 | 覆盖 | 亮点 |
|------|------|------|
| 双路 SSE 打字机对比 | F5 | done 帧 requestId 徽章，一键跳 trace |
| 红队注入面板 | F4 | 预设社工话术，附同 requestId 工具日志 rows=0 铁证表 |
| Trace 时间线 | F9+F3 | 五类事件彩色竖排（+offsetMs），payload JSON 可展开；**B 路时间线没有 AUDIT/LINEAGE 正是"无治理"的可视化证据** |
| Token 用量卡 | F6 | 调用数/输入输出 tokens/¥成本/分路径 |
| 工具日志 | F2 | byTool 聚合（次/败/均/峰）+ recent 明细，requestId 可点跳 trace |
| 连接池卡 | F8 | 三源 active/idle/total/awaiting，懒启动标"未用" |

**半 live 模式**（Docker 离线可演示 B 路全功能）：

```bash
cd poc
LLM_API_KEY=<Ark key> bash scripts/w4-demo.sh   # om-stub:18585 + data-service:8090(H2 替身) + dashboard:3000
# 打开 http://localhost:3000/w4-app.html
```

联调实测：B 路流 29 帧干净 done 收官；A 路流 trace 含 RAG_CONTEXT 3 表（augmentedChars=468）；红队 payload 字面量化 rows=0 零泄露；usage 分路径分计 ¥0.0148。

---

## 6. 测试与 CI

- **79/79 通过**（W3 27 → F5 30 → F9 39 → F10 44 → F6 52 → F2 60 → F7 63 → F8 70 → F4 74 → F3 79）
- CI：仓库根 `.github/workflows/data-service-ci.yml`，push 触发 Maven 全量测试（首跑 62s success）
- 全部安全类端点（agent/trace/usage/tool-calls/pools）已纳入 X-API-Key 鉴权（B2）

---

## 7. 遗留与下一步

| 事项 | 说明 | 依赖 |
|------|------|------|
| Docker 全栈四页联验 | W1 联邦 / W2 治理 / W3 对比 / W4 可观测一次跑通（A 路全功能） | Docker 引擎稳定（本机间歇失联） |
| F3 实源版 | 接真 OpenMetadata 容器导元数据，替换 om-stub 桩 | Docker |
| PoC 边界声明 | trace/usage/tool-calls 均为内存 FIFO（100/100/200 条），生产落地 ClickHouse + OpenTelemetry | M3+ |
| 回主仓 | Data Fabric PoC 阶段收官，回 DataGovePlatform-N 推进 M3 应用层迁移（ADR-021/022/023 落地参考本 PoC） | — |

---

**Week 4 状态：✅ 完成** — F1-F11 全部交付（79/79 测试），2 个生产缺陷顺手修复，W4 可观测台半 live 可演示。四页 dashboard（W1 联邦 / W2 治理 / W3 对比 / W4 可观测）构成 Data Fabric PoC 完整可视化验证体系。
