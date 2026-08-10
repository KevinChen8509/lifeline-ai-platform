# Week 3 · AI Agent 对比验证（LangChain4j）

> **结论先行**：A 路治理（CustomerInsightAgent 走 `/api/v1/*`）与 B 路直查（RawDbAgent 走 JDBC）能在同一道问题下产出**显著差异**：A 路的电话/身份证自动脱敏 + 审计日志 + 血缘记录全部生效，B 路返回明文 PII 且无审计，绕过治理。这正是 PoC 旨在证明的 Data Fabric 数据服务层的核心价值。

---

## 1. 范围

| 项目 | 内容 |
|------|------|
| 框架 | LangChain4j 0.36.2（最新稳定版，OpenAI 兼容） |
| LLM Provider | Volcengine Ark API（`https://ark.cn-beijing.volces.com/api/v3`） |
| 模型 | GLM-4.7（endpoint id 由 `LLM_MODEL` 注入） |
| Agent 数 | 2 个对照：CustomerInsightAgent（A）、RawDbAgent（B） |
| 工具数 | A 路 4 个 @Tool（REST 自调用），B 路 4 个 @Tool（直连 JDBC） |
| 端点 | `POST /api/v1/agent/insight`（A）、`POST /api/v1/agent/raw`（B） |
| 鉴权 | 复用 B2 的 X-API-Key（Spring Security） |
| 展示 | `dashboard/public/w3-app.html` Vue 3 + Element Plus CDN，左右分屏对比 |

---

## 2. 架构

```
                ┌──────────────────────── Data Service (Spring Boot 3.5) ────────────────────────┐
                │                                                                │
   POST /api/v1/agent/insight ──► CustomerInsightAgent ──► @Tools (RestClient) ──► /api/v1/customers/*  ──► DataMaskingAspect
   (A 路治理)                     [LangChain4j AiServices]                            │            ──► LoggingAuditLogger
                                                                                     │            ──► LineageAspect
                                                                                     │
   POST /api/v1/agent/raw    ──► RawDbAgent ──► @Tools (JDBC PreparedStatement) ──► MySQL / ClickHouse / PostgreSQL
   (B 路直查)                     [LangChain4j AiServices]                                                                         (绕过所有 Aspect)

                └────────────────────────────────────────────────────────────────────────────────┘
                                          ▲
                                          │ X-API-Key
                                          │
                          dashboard server.js  (/api/w3/ask-fabric, /api/w3/ask-raw)
                                          ▲
                                          │ fetch
                          dashboard/public/w3-app.html  (Vue 3 + Element Plus)
                                          ▲
                                          │ 浏览器
                                       用户
```

**关键设计**：A 路工具通过 `RestClient` 调本进程的 `/api/v1/*` —— 这种"自环调用"刻意让请求穿过 DispatcherServlet，从而触发所有治理 Aspect；B 路工具用原生 JDBC 跳过 Servlet 容器，作为对照展示"无治理"的真实风险。

---

## 3. 实施清单

| 模块 | 文件 | 说明 |
|------|------|------|
| 依赖 | `pom.xml` | 加 `langchain4j-spring-boot-starter`、`langchain4j-open-ai-spring-boot-starter` 0.36.2；加 MySQL/CH/PG JDBC 驱动 |
| 配置 | `application.yml` | 加 `datafabric.llm.*` 与 `datafabric.raw-db.*` 段 |
| LLM 配置 | `config/LlmProperties.java`、`config/LangChainConfig.java` | OpenAiChatModel bean + CustomerInsightAgent bean + RestClient bean（自环带 X-API-Key） |
| RawDb 配置 | `config/RawDbProperties.java`、`config/RawDbAgentConfig.java` | 三库 DataSource + RawDbAgent bean |
| A 路 Agent | `agent/CustomerInsightAgent.java`（接口 + @SystemMessage）<br>`agent/CustomerInsightTools.java`（4 个 @Tool） | getCustomerProfile / searchCustomersByLevel / getCustomerMetrics / getHighRiskCustomers |
| B 路 Agent | `agent/RawDbAgent.java`、`agent/RawDbTools.java` | getCustomerRaw / searchCustomersByLevelRaw / getCustomerOrders / getHighRiskCustomerIds |
| 端点 | `api/AgentController.java` | 两个 POST 端点，路径白名单已纳入 B2 鉴权 |
| 测试 | `AgentControllerTest.java` | 3 个用例：A 无 key 401 / A 空 question 400 / B 无 key 401 |
| 前端 | `dashboard/public/w3-app.html` + `w3-app.js` | Vue 3 + Element Plus CDN，5 个预设问题按钮，左右分屏 + PII 泄漏自动检测 |
| 代理 | `dashboard/server.js` + `lib/data-service-client.js` | 加 `askAgent` 透传（带 X-API-Key），新增 `/api/w3/ask-fabric` 与 `/api/w3/ask-raw` |

测试结果：**27/27 通过**（含 W2 既有 24 + W3 新增 3）

---

## 4. 5 道对比问题与期望差异

| # | 问题 | A 路期望（治理后） | B 路期望（无治理） | 价值点 |
|---|------|-------------------|-------------------|--------|
| Q1 | 查询客户 C0001 的完整画像 | 电话 `138****1234`、身份证 `110***********1234`；触发审计日志 | 电话 `13812341234`、身份证 `110101199001011234` 明文 | **PII 脱敏对照** |
| Q2 | VIP3 等级的客户有多少？ | 数字（来自语义层 metrics） | 数字（来自 MySQL COUNT） | **回答能力相当**（证明 A 路不损失精度） |
| Q3 | 哪些客户是高风险？列出前 5 个 | 高风险列表 + 审计日志 | 高风险列表，无审计 | **审计可追溯** |
| Q4 | 查询客户 C0001 的订单数据 | 经服务层联邦 | 直接查 ClickHouse | **联邦查询能力** |
| Q5 | 查询客户 C0002 的画像和电话号码 | 明确拒绝提供明文电话或返回脱敏 | 直接返回明文电话 | **合规边界对照** |

> 实际运行时观察到的字段格式与具体话术取决于 LLM 推理结果；PII 检测由前端使用正则（手机号/身份证/邮箱）自动标注。

---

## 5. 调用示例（PoC 演示流程）

1. 启动 `data-service`（`LLM_API_KEY=ark-xxx mvn spring-boot:run`）和 `dashboard`（`npm start`）
2. 打开 `http://localhost:3000/w3-app.html`
3. 点击「C0001 画像」按钮
4. 等待 ~3-8 秒（LLM 调用 + 工具迭代）
5. 左侧（A 路）：电话脱敏显示，PII 状态为"已脱敏 ✓"
6. 右侧（B 路）：电话明文显示，PII 状态为"⚠ 明文泄漏"
7. 查看后端日志：A 路 `LoggingAuditLogger` 输出 `AUDIT actor=api-client action=customer-profile.read resource=C0001`；B 路无任何审计输出

---

## 6. 关键设计决策

### 6.1 为何选 LangChain4j 0.36.2 而不是 1.0.0？

- 1.x 仍是 alpha/beta（Maven Central 上没有 1.0.0 stable）
- 华为云 Maven 镜像未收录 langchain4j 1.x
- 0.36.2 是当前最新稳定版，API 成熟
- 0.36.x 不使用 `@AiService` 注解（那是 1.x 的特性），改用 `AiServices.builder(InterfaceClass.class).chatLanguageModel(model).tools(tools).build()`，更直观

### 6.2 为何 A 路工具用 RestClient 自调用？

- 让请求穿过 Spring MVC → 所有 AOP 治理 Aspect（DataMasking / Audit / Lineage）正常触发
- 不需要重复实现脱敏逻辑
- 形成清晰的"治理边界"语义：凡是走 `/api/v1/*` 的请求，自动受到治理保护
- B 路故意跳过这一层，证明"绕过治理"的真实风险

### 6.3 为何 B 路用 PreparedStatement 而不是 JdbcTemplate？

- PoC 代码量更少、依赖更轻
- 工具方法直接 `Connection.prepareStatement(sql).setString(1, custId)`，SQL 注入面已被参数化堵住
- 重点不在 B 路的"优雅"，而在 A/B 对比的可观察性

---

## 7. 待改进（Week 4 候选）

- **F1** LangChain4j 1.0 stable 发布后升级，启用 `@AiService` 注解简化 Agent 注册
- **F2** Agent 工具调用日志（观察 LLM 的 tool-call iteration 次数与 token 消耗）
- **F3** A 路增加 RAG 上下文（从 OpenMetadata 拉取表注释作为 LLM 提示）
- **F4** B 路用作"红队"，演示 SQL 注入尝试被 PreparedStatement 拒绝的对比
- **F5** 增加流式响应（SSE），LLM 边生成边推送到前端
- **F6** 加 Token 计数与成本统计
- **F7** A 路 Agent 工具粒度细化（增加"按地区筛选"、"按时间窗口聚合"等）
- **F8** B 路增加失败重试与连接池监控
- **F9** 增加 `/api/v1/agent/trace/{requestId}` 端点，返回某次请求的审计 + 血缘完整链路
- **F10** Pact contract 测试覆盖 Agent 端点

---

## 8. 测试与 CI

- 本地：`mvn test` → 27/27 通过（W2 既有 24 + W3 新增 3）
- CI：GitHub Actions workflow（`/.github/workflows/data-service-ci.yml`，仓库根）会在每次 push 到 `datafabric-week*` 或 `master` 且改动 `DataService-fabric/poc/data-service/**` 时触发

---

**Week 3 状态：✅ 完成** — 5 道对比问题已就绪，Vue 3 对比页可访问 `/w3-app.html`，A 路治理 vs B 路无治理的差异在前端可视化呈现。
