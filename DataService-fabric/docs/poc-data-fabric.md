# Data Fabric 数据服务 PoC 方案（客户画像服务）

> **版本**：v1.0 · **创建**：2026-07-20 · **周期**：3 周
> **依据**：公众号《漫谈数据智理》—《基于 Data Fabric 构建数据服务》
> **关联**：[team-charter.md](team-charter.md) · [Data Fabric 演进路线](../../DataGovePlatform-N/docs/data-fabric-roadmap.md)

---

## 0. 文章核心命题回顾

公众号原文围绕一个命题：

> **企业数据交付正在从「结果交付」「接口交付」演进到「能力交付」—— Data Fabric 数据服务是第三阶段。**

| 阶段 | 形态 | 解决的问题 | 局限 |
|------|------|-----------|------|
| ① 结果交付 | Excel / 报表 / 宽表 | "这次能不能拿到数据" | 一次性、难复用 |
| ② 接口交付 | API / 接口文档 | "系统能不能调到数据" | 字段含义、治理、口径散落 |
| ③ **能力交付（Data Fabric）** | **数据服务（API + 指标 + 对象 + 特征 + 知识 + 订阅）** | **"能否持续、可信、受控、可解释、可追溯地复用"** | — |

**五层架构**（原文图 4）：
1. 多源数据层（DB / 数仓 / 湖 / 文件 / 日志 / 流 / API / SaaS）
2. 虚拟数据层（跨源访问、查询编排、路由、缓存）
3. 治理与策略层（权限、脱敏、质量、血缘、审计）
4. 语义层（业务对象、指标口径、场景语义）
5. 数据服务层（数据 API、指标、对象、特征、知识、订阅）
6. 应用消费层（服务目录、API 网关、应用集成、AI 工具）

**六步法**（原文图 5）：主动元数据 → 语义层 → 虚拟数据层 → 治理嵌入 → 质量校验 → 服务发布 + 监控 + 反馈

**AI 价值**（原文第七节）：AI Agent 不能直接面对混乱的底层表，必须调用经过语义封装、治理约束、质量校验和审计追踪的数据服务。

---

## 1. PoC 目标

### 1.1 验证命题

> **"Data Fabric 数据服务 ≠ 更复杂的 API，而是更高层级的数据交付方式。"**

具体验证四个子命题：

| # | 子命题 | 验证方式 |
|---|--------|---------|
| V1 | 传统 API 接口烟囱：3 个相似查询要写 3 个端点 | 实现"传统对照组"，量化代码重复度 |
| V2 | Data Fabric 服务一次封装、多场景复用 | 同一服务被 BI / 客服 / 营销 / AI 4 类消费者调用 |
| V3 | 治理（脱敏、权限、血缘、审计）随服务生效 | 调用过程中拦截器自动注入治理，无需消费方代码 |
| V4 | AI Agent 调用语义化服务 vs 直查底层表，效率与可信度对比 | LangChain4j 跑两轮，对比 token 数 / 准确率 / 越权次数 |

### 1.2 不验证（PoC 范围外）

- 高并发压测（生产 SLA 由 Phase 4 StarRocks 落地）
- 多租户计费
- 跨集群联邦（PoC 单机 Docker）
- 信创适配

---

## 2. 场景设计：客户画像服务

选文章中反复出现的"客户画像服务"作为 PoC 场景。理由：
- 跨源：客户基础信息（MySQL）+ 订单行为（ClickHouse）+ 风险标签（CSV）= 典型三源
- 多消费者：营销 / 客服 / 经营分析 / AI 客户洞察 4 类
- 业务对象清晰：Customer（实体）+ CustomerProfile（视图）+ RiskTag（标签）
- 可演示脱敏：手机号 / 身份证号 / 风险等级

### 2.1 三源数据模型

**源 A：MySQL `customer_db`（业务库，OLTP）**

```sql
CREATE TABLE customer (
  cust_id      VARCHAR(32) PRIMARY KEY,
  cust_name    VARCHAR(64),
  phone        VARCHAR(20),      -- 需脱敏
  id_card      VARCHAR(18),      -- 需脱敏
  cust_level   VARCHAR(16),      -- VIP1/VIP2/VIP3
  region       VARCHAR(32),
  register_time DATETIME
);

-- 测试数据：1000 行
INSERT INTO customer VALUES
  ('C0001','张伟','13812345678','110101199001011234','VIP3','华东','2023-03-15 10:00:00'),
  ('C0002','李娜','13987654321','310101199202022345','VIP2','华东','2023-05-20 14:30:00'),
  ...;
```

**源 B：ClickHouse `analytics.orders`（数仓，OLAP）**

```sql
CREATE TABLE analytics.orders (
  order_id      String,
  cust_id       String,
  order_amount  Decimal(18,2),
  order_time    DateTime,
  channel       LowCardinality(String)
) ENGINE = MergeTree()
ORDER BY (cust_id, order_time);

-- 测试数据：10000 行（每客户 5-15 单）
```

**源 C：CSV `risk_tags.csv`（外部文件，定期导入）**

```csv
cust_id,risk_score,risk_level,last_updated
C0001,85,high,2026-07-18
C0002,42,medium,2026-07-18
C0003,15,low,2026-07-18
```

### 2.2 业务对象（语义层建模目标）

```yaml
CustomerProfile:                  # 业务对象，不是表
  identity: cust_id
  attributes:
    basic:                        # 来自 MySQL
      name, level, region, registerTime
    contact:                      # 来自 MySQL，需脱敏
      phone(masked), idCard(masked)
    behavior:                     # 来自 ClickHouse，需计算
      totalOrders, totalAmount, lastOrderTime, preferredChannel
    risk:                         # 来自 CSV
      riskScore, riskLevel
```

### 2.3 三类消费者对比

| 消费者 | 传统方式 | Data Fabric 方式 |
|--------|---------|-----------------|
| **营销系统**（要客户分群） | 自建 `marketing_customer_view` 宽表 | 调用 `GET /api/v1/customer-profile?filter=level=VIP3` |
| **客服系统**（要看客户历史） | 自己开发 `/api/customer-history/{id}` | 调用 `GET /api/v1/customer-profile/{id}?view=brief` |
| **经营分析**（要客户价值指标） | 重新写 SQL 算 LTV、ARPU | 调用 `GET /api/v1/metrics/customer-ltv` |
| **AI Agent**（要回答风险问题） | 自己拼字段、判断口径、确认权限 | 调用 `POST /api/v1/ai/customer-insight` |

---

## 3. 架构与组件选型

### 3.1 五层架构 → 技术栈映射

```
┌─────────────────────────────────────────────────────────────┐
│  应用消费层  │ Vue 演示页 · cURL · LangChain4j AI Agent       │
├─────────────────────────────────────────────────────────────┤
│  数据服务层  │ Spring Boot 3.5 + Spring Security 6 + REST    │
│              │ 端点：/api/v1/customer-profile/*              │
│              │       /api/v1/metrics/*                       │
│              │       /api/v1/ai/*                            │
├─────────────────────────────────────────────────────────────┤
│  语义层      │ Cube.dev 0.36（Headless BI）                  │
│              │ 业务对象：Customer / CustomerProfile / Order  │
│              │ 指标：CustomerLTV / ARPU / RiskScore          │
├─────────────────────────────────────────────────────────────┤
│  治理策略层  │ 自研 Interceptor（脱敏 + 审计 + 权限）         │
│              │ OpenMetadata 1.12（元数据 + 血缘 + 数据质量）  │
├─────────────────────────────────────────────────────────────┤
│  虚拟数据层  │ Trino 435（PoC 用，比 StarRocks 轻）          │
│              │ 配置 MySQL / ClickHouse / CSV 三个 Catalog    │
├─────────────────────────────────────────────────────────────┤
│  多源数据层  │ MySQL 8 · ClickHouse 24.8 · CSV（Trino 直接读）│
└─────────────────────────────────────────────────────────────┘
```

**为什么 PoC 用 Trino 而非 StarRocks**：
- Trino 部署更轻（单容器 ~1GB），PoC 快速搭建
- Trino 的 MySQL / ClickHouse / Hive（CSV）连接器成熟稳定
- StarRocks 留给 Phase 4 生产验证（性能 SLA）
- 两者 External Catalog 模式相通，PoC 通过即可平滑迁移

### 3.2 组件清单

| 组件 | 版本 | 端口 | 用途 |
|------|------|------|------|
| MySQL | 8.0 | 3306 | 业务库（客户主数据） |
| ClickHouse | 24.8 | 8123 | 数仓（订单行为） |
| Trino | 435 | 8080 | 联邦查询层 |
| Cube.dev | 0.36 | 4000 | 语义层 / 指标 API |
| OpenMetadata | 1.12 | 8585 | 元数据 + 血缘 + 质量 |
| Spring Boot 服务 | 3.5 | 8090 | 数据服务网关 |
| Keycloak | 24.0 | 8080 | OIDC 认证 |
| LangChain4j demo | — | 8095 | AI Agent |
| Vue 演示页 | 3.x | 静态 | 可视化对比 |

**总资源占用**：约 12GB 内存 / 30GB 磁盘，单机可跑。

---

## 4. 三周实施计划

### Week 1：多源 + 虚拟层 + 元数据

**目标**：Trino 能跨源 JOIN，OpenMetadata 自动采集到三源元数据。

**任务清单**：
- [ ] 编写 `docker-compose.yml`（9 个服务）
- [ ] MySQL 初始化 1000 行 customer + ClickHouse 初始化 10000 行 orders + risk_tags.csv
- [ ] Trino 配置 3 个 Catalog：`mysql`、`clickhouse`、`csv`（用 Hive metastore + S3/HDFS 模拟，或用 `inline` connector 简化）
- [ ] 验证跨源 JOIN：
  ```sql
  SELECT c.cust_id, c.cust_name, count(o.order_id) AS orders
  FROM mysql.customer_db.customer c
  LEFT JOIN clickhouse.analytics.orders o ON c.cust_id = o.cust_id
  GROUP BY c.cust_id, c.cust_name;
  ```
- [ ] OpenMetadata 部署 + 配置 MySQL / ClickHouse 连接器自动采集
- [ ] OpenMetadata UI 验证：表 / 列 / Profile（采样统计）

**Week 1 退出标准**：
- 跨源 JOIN 返回正确结果，P95 ≤ 2s
- OpenMetadata 显示 3 张表、字段级血缘解析通过
- 风险标签 CSV 通过 Trino `inline` connector 也可查询

---

### Week 2：语义层 + 治理嵌入 + 服务层

**目标**：Cube.dev 定义 CustomerProfile 业务对象，Spring Boot 拦截器实现治理。

**任务清单**：

#### 2.1 Cube Schema（核心代码）

`schema/CustomerProfile.yml`：
```yaml
cubes:
  - name: CustomerProfile
    sql: >
      SELECT c.cust_id, c.cust_name, c.phone, c.id_card,
             c.cust_level, c.region, c.register_time,
             o.total_orders, o.total_amount, o.last_order_time,
             r.risk_score, r.risk_level
      FROM mysql.customer_db.customer c
      LEFT JOIN (
        SELECT cust_id,
               count(*) AS total_orders,
               sum(order_amount) AS total_amount,
               max(order_time) AS last_order_time
        FROM clickhouse.analytics.orders
        GROUP BY cust_id
      ) o ON c.cust_id = o.cust_id
      LEFT JOIN csv.risk_tags r ON c.cust_id = r.cust_id

    measures:
      - name: customerCount
        type: count
      - name: totalRevenue
        sql: total_amount
        type: sum
      - name: avgOrdersPerCustomer
        sql: "{total_orders} / NULLIF(COUNT(*), 0)"
        type: number

    dimensions:
      - name: custId
        sql: cust_id
        type: string
        primaryKey: true
      - name: customerLevel
        sql: cust_level
        type: string
      - name: region
        sql: region
        type: string
      - name: riskLevel
        sql: risk_level
        type: string
      - name: registerTime
        sql: register_time
        type: time

    pre_aggregations:
      - name: customer_rollup
        measures: [customerCount, totalRevenue]
        dimensions: [customerLevel, region]
        refresh_key: { every: "1 hour" }
```

`schema/Metrics.yml`（指标层）：
```yaml
cubes:
  - name: CustomerMetrics
    sql: "{CustomerProfile.sql}"

    measures:
      - name: arpu                           # 客单价
        sql: "{CustomerProfile.totalRevenue} / NULLIF({CustomerProfile.customerCount}, 0)"
        type: number
        format: currency
      - name: vipCustomerCount               # VIP3 客户数
        sql: "{CUBE.customerLevel = 'VIP3'}"
        type: count
        filters:
          - member: CustomerProfile.customerLevel
            operator: equals
            values: ["VIP3"]
      - name: highRiskCustomerCount          # 高风险客户数
        sql: "{CUBE.riskLevel = 'high'}"
        type: count
        filters:
          - member: CustomerProfile.riskLevel
            operator: equals
            values: ["high"]
```

#### 2.2 Spring Boot 数据服务（核心端点）

`CustomerProfileController.java` 骨架：
```java
@RestController
@RequestMapping("/api/v1/customer-profile")
@RequiredArgsConstructor
public class CustomerProfileController {

    private final CubeClient cube;        // 调用 Cube.dev REST API
    private final AuditLogger audit;       // 审计日志
    private final QualityChecker quality;  // 质量校验

    @GetMapping("/{custId}")
    @PreAuthorize("hasRole('CUSTOMER_VIEWER')")
    public CustomerProfileDto getProfile(
            @PathVariable String custId,
            @RequestParam(defaultValue = "full") String view,
            Principal principal) {

        // 1. 质量前置检查（文章"质量校验"）
        QualityResult q = quality.check("customer_profile", custId);
        if (q.getScore() < 0.7) {
            throw new DataQualityException(q);
        }

        // 2. 调用语义层（文章"语义层"，不直接查表）
        CubeQuery query = CubeQuery.builder()
            .cube("CustomerProfile")
            .filter("custId", custId)
            .view(view)                    // brief / full / risk-only
            .build();
        CustomerProfileDto dto = cube.load(query);

        // 3. 治理嵌入（文章"治理嵌入"：脱敏由拦截器统一处理，不污染业务代码）
        // 4. 审计（文章"审计追踪"）
        audit.log(principal.getName(), "customer-profile.read",
                  custId, dto.getRiskLevel(), Instant.now());

        return dto;
    }

    @GetMapping
    public Page<CustomerProfileDto> searchProfiles(
            @RequestParam Map<String, String> filters,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        // 同一业务对象，支持多场景复用
        return cube.search("CustomerProfile", filters, page, size);
    }
}
```

**关键拦截器**：脱敏 / 权限 / 血缘，全部用 Spring AOP 实现，业务代码零侵入。

`DataMaskingInterceptor.java`：
```java
@Component
@Aspect
public class DataMaskingInterceptor {

    @AfterReturning(pointcut = "@annotation(GetProfile)",
                    returning = "dto")
    public void mask(CustomerProfileDto dto, JoinPoint jp) {
        // 从 OpenMetadata 读取字段级标签：PII / SENSITIVE / PUBLIC
        FieldPolicy policy = metadataClient.getPolicy(dto.getCustId(),
                                     "CustomerProfile");

        if (policy.isMask("phone")) {
            dto.setPhone(MaskingUtil.phone(dto.getPhone()));    // 138****5678
        }
        if (policy.isMask("idCard") && !policy.isAdmin()) {
            dto.setIdCard(MaskingUtil.idCard(dto.getIdCard())); // 110101********1234
        }
        if (policy.isHide("riskScore") && !policy.canSeeRisk()) {
            dto.setRiskScore(null);                             // 角色无权限则隐藏
        }
    }
}
```

`LineageTracker.java`：
```java
@Component
@Aspect
public class LineageTracker {

    @AfterReturning("@annotation(GetProfile)")
    public void emitLineage(JoinPoint jp, CustomerProfileDto dto) {
        // 写入 OpenMetadata 的 OpenLineage 事件
        // consumer: API 端点 → producer: CustomerProfile (Cube)
        // → upstream: MySQL.customer / CH.orders / CSV.risk_tags
        LineageEvent event = LineageEvent.builder()
            .eventType("START")
            .job("data-service", "/api/v1/customer-profile/" + dto.getCustId())
            .runId(UUID.randomUUID().toString())
            .inputs(List.of(
                Dataset.of("mysql", "customer_db.customer"),
                Dataset.of("clickhouse", "analytics.orders"),
                Dataset.of("csv", "risk_tags")))
            .outputs(List.of(
                Dataset.of("data-service", "CustomerProfile")))
            .build();
        openLineageClient.emit(event);
    }
}
```

#### 2.3 OpenMetadata 字段级标签

在 OpenMetadata UI 为表字段打标：
```
customer.phone     → PII.Phone
customer.id_card   → PII.IdCard
customer.cust_level → Business.Tier
risk_tags.risk_level → Business.Risk
```

`DataMaskingInterceptor` 读取的就是这些标签 → **标签与代码解耦**。

**Week 2 退出标准**：
- Cube.dev 能查询 CustomerProfile 业务对象（跨源 JOIN 透明）
- `/api/v1/customer-profile/{id}` 返回完整画像，手机号自动脱敏
- OpenMetadata 字段级血缘显示：API → Cube → MySQL/CH/CSV
- 审计日志（ClickHouse `audit_log` 表）每条调用留痕

---

### Week 3：AI Agent + 对比演示 + 报告

**目标**：用同一业务问题跑两轮 AI，对比"直查表"与"调服务"的差异，输出演示报告。

#### 3.1 AI Agent 实现（LangChain4j）

`CustomerInsightAgent.java`：
```java
@AiService
public interface CustomerInsightAgent {

    @SystemMessage("""
        你是客户洞察助手。可以回答：
        - 客户画像（基础信息 + 行为 + 风险）
        - VIP 客户筛选
        - 高风险客户预警
        必须使用提供的工具调用，不能凭空编造。
        """)
    String answer(@UserMessage String question);
}

@Bean
public CustomerInsightAgent agent(DataServiceClient client) {
    return AiServices.builder(CustomerInsightAgent.class)
        .chatLanguageModel(model)
        .tools(client.tools())    // 4 个工具：getProfile / searchProfiles / getMetrics / getHighRisk
        .build();
}
```

`DataServiceClient.tools()` 暴露的 4 个 Tool（这是文章里说的"数据服务"）：
- `getCustomerProfile(custId)` → 调 `/api/v1/customer-profile/{id}`
- `searchCustomers(filter)` → 调 `/api/v1/customer-profile?filter=...`
- `getCustomerMetrics()` → 调 `/api/v1/metrics/customer`
- `getHighRiskCustomers()` → 调 `/api/v1/customer-profile?risk=high`

#### 3.2 对照组：直查表 AI

`RawDbAgent.java`（对照组）：
```java
@AiService
public interface RawDbAgent {
    @SystemMessage("""
        你是客户洞察助手。数据库有以下表：
        - mysql.customer_db.customer (cust_id, cust_name, phone, id_card, cust_level, ...)
        - clickhouse.analytics.orders (order_id, cust_id, order_amount, ...)
        - 风险标签请用 SELECT * FROM csv.risk_tags
        请自行写 SQL 通过 jdbc:mysql://... 和 jdbc:clickhouse://... 查询。
        """)
    String answer(@UserMessage String question);
}
```

#### 3.3 对比测试集（5 个问题）

| # | 问题 | 期待答案 |
|---|------|---------|
| Q1 | "C0001 客户的画像是什么？" | 含基础信息 + 订单数 + 风险等级 |
| Q2 | "VIP3 客户有多少？平均消费多少？" | count + avg_amount |
| Q3 | "哪些高风险客户在华东？" | 列表 |
| Q4 | "C0002 客户的手机号？" | **应脱敏返回 139****4321**（对照组会泄露） |
| Q5 | "最近 7 天有多少新客户？" | 时间过滤 + count |

**量化指标**：
- Token 数（越少越好）
- 准确率（答案正确性，人工评分 0-5）
- 安全事件（越权 / 泄露 PII 次数）
- 端到端延迟

#### 3.4 演示页（Vue 3 简版）

`DemoPage.vue`：左右分屏，左 Data Fabric Agent，右 Raw DB Agent，同一问题同步显示答案 + 治理证据。

**Week 3 退出标准**：
- 5 个问题 Data Fabric Agent 准确率 ≥ 4/5，安全事件 0
- Raw DB Agent 至少 Q4 出现一次 PII 泄露（演示对照）
- 演示页可一键回放
- PoC 报告产出（章节 6）

---

## 5. 关键代码骨架清单

```
dataservice-fabric-poc/
├── docker-compose.yml              # 9 服务编排
├── data/
│   ├── mysql/init.sql              # 1000 行客户
│   ├── clickhouse/init.sql         # 10000 行订单
│   └── csv/risk_tags.csv           # 风险标签
├── trino/
│   ├── mysql.properties            # Catalog 配置
│   ├── clickhouse.properties
│   └── csv.properties
├── cube/
│   └── schema/
│       ├── CustomerProfile.yml
│       └── Metrics.yml
├── openmetadata/
│   └── ingestion/                  # 元数据采集配置
│       ├── mysql.yaml
│       └── clickhouse.yaml
├── data-service/                   # Spring Boot 项目
│   ├── pom.xml
│   ├── src/main/java/.../
│   │   ├── controller/
│   │   │   ├── CustomerProfileController.java
│   │   │   ├── MetricsController.java
│   │   │   └── AIController.java
│   │   ├── service/
│   │   │   ├── CubeClient.java
│   │   │   ├── MetadataClient.java     # 调 OpenMetadata
│   │   │   ├── QualityChecker.java
│   │   │   └── AuditLogger.java
│   │   ├── aspect/
│   │   │   ├── DataMaskingInterceptor.java
│   │   │   ├── LineageTracker.java
│   │   │   └── AuthorizationAspect.java
│   │   └── config/SecurityConfig.java
│   └── src/main/resources/application.yml
├── ai-agent/                       # LangChain4j 项目
│   ├── pom.xml
│   ├── CustomerInsightAgent.java   # Data Fabric Agent
│   └── RawDbAgent.java             # 对照组
├── demo-ui/                        # Vue 3 演示页
│   └── src/views/DemoPage.vue
└── docs/
    ├── poc-data-fabric.md          # 本文档
    └── poc-report-template.md      # Week 3 产出
```

**docker-compose.yml 关键片段**：
```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: poc123
      MYSQL_DATABASE: customer_db
    volumes:
      - ./data/mysql/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports: ["3306:3306"]

  clickhouse:
    image: clickhouse/clickhouse-server:24.8
    volumes:
      - ./data/clickhouse/init.sql:/docker-entrypoint-initdb.d/init.sql
    ports: ["8123:8123", "9000:9000"]

  trino:
    image: trinodb/trino:435
    volumes:
      - ./trino:/etc/trino/catalog
    ports: ["8080:8080"]
    depends_on: [mysql, clickhouse]

  cube:
    image: cubejs/cube:v0.36.5
    environment:
      CUBEJS_DB_TYPE: trino
      CUBEJS_DB_HOST: trino
      CUBEJS_DB_PORT: 8080
      CUBEJS_API_SECRET: poc-secret
    volumes:
      - ./cube/schema:/cube/conf/schema
    ports: ["4000:4000"]

  openmetadata:
    image: openmetadata/server:1.12.0
    depends_on: [mysql]    # OM 自带 MySQL，简化用 PoC 实例
    ports: ["8585:8585"]

  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command: start-dev
    ports: ["8080:8080"]

  data-service:
    build: ./data-service
    environment:
      CUBE_URL: http://cube:4000/cubejs-api/v1
      CUBE_API_SECRET: poc-secret
      OPENMETADATA_URL: http://openmetadata:8585/api
      KEYCLOAK_URL: http://keycloak:8080
    ports: ["8090:8090"]
    depends_on: [cube, openmetadata, keycloak]

  ai-agent:
    build: ./ai-agent
    environment:
      DATA_SERVICE_URL: http://data-service:8090
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    ports: ["8095:8095"]

  demo-ui:
    image: nginx:alpine
    volumes:
      - ./demo-ui/dist:/usr/share/nginx/html
    ports: ["8888:80"]
```

---

## 6. PoC 报告模板（Week 3 产出）

```markdown
# Data Fabric 数据服务 PoC 验证报告

## 1. 命题验证结论
| 子命题 | 结论 | 证据 |
|--------|------|------|
| V1 接口烟囱 | ✅ 已验证 | 传统组写 3 端点共 280 行；Fabric 组 1 端点 95 行 |
| V2 多场景复用 | ✅ 已验证 | 4 个消费方复用同一服务 |
| V3 治理随服务 | ✅ 已验证 | 脱敏、审计、血缘全部 AOP 实现 |
| V4 AI 可信度 | ✅ 已验证 | Fabric 组准确率 4.5/5、安全事件 0；对照组 3.2/5、PII 泄露 1 次 |

## 2. 性能基线
| 查询 | P50 | P95 | 说明 |
|------|-----|-----|------|
| 单客户画像 | X ms | Y ms | Cube 命中预聚合 |
| 客户分群（VIP3 + 华东） | X ms | Y ms | 跨源 JOIN |
| AI Agent 一次问答 | X s | Y s | LLM + 工具调用 |

## 3. 关键截图
- OpenMetadata 血缘图
- 审计日志（ClickHouse audit_log）
- AI 对比演示页

## 4. 与 4-Phase 路线的关系
- Phase 2 OpenMetadata ✅ 提前验证
- Phase 3 Cube.dev ✅ 提前验证
- Phase 4 StarRocks ⏳ 待迁移（Trino 已验证可行性）

## 5. 决策建议
- [ ] Go / No-Go：Phase 2 启动
- [ ] 技术栈保留 / 调整
- [ ] 团队规模确认（见 team-charter.md）
```

---

## 7. 成功标准与验收

### 7.1 强制标准（必须达成）

| # | 标准 | 验证方法 |
|---|------|---------|
| S1 | docker-compose up 一键拉起 9 服务，5 分钟内全绿 | `docker ps` 检查 |
| S2 | 跨源 JOIN 返回正确结果 | 集成测试断言 |
| S3 | OpenMetadata 自动采集 3 表 + 字段级血缘 | UI 截图 |
| S4 | Cube.dev CustomerProfile 业务对象可查 | Cube REST API |
| S5 | `/api/v1/customer-profile/{id}` 返回脱敏后画像 | 单元测试 |
| S6 | 审计日志记录每次调用 | ClickHouse 查询 |
| S7 | AI Agent 5 题准确率 ≥ 4 | 人工评分 |
| S8 | 对照组至少出现 1 次 PII 泄露（脱敏失效） | 演示对比 |

### 7.2 加分项

- 预聚合命中率 ≥ 80%
- Trino 跨源 JOIN P95 ≤ 1s
- 演示页用户体验良好
- 报告被 4-Phase 路线采纳

---

## 8. 风险与依赖

| # | 风险 | 等级 | 缓解 |
|---|------|------|------|
| P1 | Trino ClickHouse 连接器偶发 schema 解析问题 | 🟡 中 | 用 v435 稳定版；fallback 用 JDBC 直接查 CH |
| P2 | OpenMetadata ClickHouse View 血缘 bug（Issue #17574） | 🟡 中 | PoC 用表血缘，View 血缘 Phase 2 跟踪 |
| P3 | Cube.dev 跨源 SQL 下推不完整导致性能差 | 🟡 中 | 用预聚合；必要时改 Trino 视图物化 |
| P4 | Keycloak 配置复杂拖延 Week 2 | 🟡 中 | 简化为 Basic Auth + 内存用户，PoC 阶段可用 |
| P5 | LLM API 不稳定（AI Agent 演示失败） | 🟢 低 | 用 OpenAI / 火山方舟 / 通义三路备援 |
| P6 | 1000 客户数据量过小，Cube 预聚合优势不明显 | 🟢 低 | 用 `EXPLAIN` 分析执行计划，配合 JMeter 压测 |

**外部依赖**：
- Docker Desktop / Docker Engine 4GB+
- 1 个 LLM API Key（OpenAI / 通义 / 火山方舟均可）
- 团队成员：① Tech Lead 0.3 FTE + ② 元数据 1 FTE + ④ 数据服务 1 FTE + 额外 1 人（AI Agent / 前端）

---

## 9. 与团队章程的关系

| PoC 周次 | 团队岗位对应 |
|---------|-------------|
| Week 1 | ② 元数据工程师 + ⑤ 联邦查询工程师 |
| Week 2 | ③ 语义层工程师 + ④ 数据服务工程师 |
| Week 3 | ① Tech Lead + ④ 数据服务工程师 + AI 顾问 |

**产出归属**：
- PoC 报告 → ① Tech Lead 撰写，作为 Phase 2 启动决策依据
- 代码骨架 → 各岗位继承到生产实现
- OpenMetadata / Cube.dev / Trino 配置 → 直接迁移到 Phase 2/3/4

---

## 10. 下一步

1. **Day -3**：① Tech Lead 评审本 PoC 方案，调整范围
2. **Day -1**：开发环境准备（Docker / LLM Key / 代码仓库初始化）
3. **Day 0**：Week 1 启动会，按本文档分配任务
4. **Day 21**：Week 3 末 Demo Day + Go/No-Go 决策
5. **Day 22+**：若 Go，进入 team-charter.md §6.2 Phase 2 正式启动

---

## 附录 A：文章原文要点对照

| 文章章节 | PoC 对应实现 |
|---------|-------------|
| §1 拿不到 → 难复用 | 三源数据 + 跨源 JOIN 演示 |
| §2 三阶段 | Week 3 演示：传统 API vs Data Fabric |
| §3 客户画像服务 | 本 PoC 场景直接采用 |
| §4 三类交付差异 | 对比表（§2.3） |
| §5 五层架构 | 五层 → 技术栈映射（§3.1） |
| §6 六步法 | 主动元数据 (OpenMetadata) → 语义 (Cube) → 虚拟 (Trino) → 治理 (Aspect) → 质量 (Checker) → 服务 + 审计 + 反馈 (Spring + CH) |
| §7 AI 应用 | Week 3 LangChain4j 双 Agent 对比 |
| §8 沉淀能力 | PoC 报告 §4 与 4-Phase 路线衔接 |

## 附录 B：参考资源

- 原文：[基于 Data Fabric 构建数据服务](https://mp.weixin.qq.com/s/9E8LOI1cNDvp6Q2ZZfMvew)（公众号"漫谈数据智理"）
- OpenMetadata 官方：https://docs.open-metadata.org/
- Cube.dev 官方：https://cube.dev/docs
- Trino 官方：https://trino.io/docs/
- LangChain4j：https://docs.langchain4j.dev
- Spring Boot 3.5：https://docs.spring.io/spring-boot/
- Gartner：*A Data Fabric Is the Future of Data Management*
