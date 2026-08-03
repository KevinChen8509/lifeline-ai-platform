# Spring Boot 数据服务（Week 2 W2.2）

## 定位

把 Cube.dev 语义层的能力封装为对外 REST 契约，业务消费方（前端 / AI Agent / 第三方）
**只调 `/api/v1/*`**，不感知底层 Cube / Trino / 三源。

```
   消费方（curl / Dashboard / AI Agent）
                │
                ▼
       ┌──────────────────────┐
       │ data-service :8090   │ ← 本服务
       │  /api/v1/customers/* │
       │  /api/v1/metrics/*   │
       └──────────┬───────────┘
                  │  POST /cubejs-api/v1/load
                  ▼
            Cube.dev :4000     Week 2 W2.1
                  │
                  ▼
              Trino :8080      Week 1 虚拟层
```

## 端点契约

| 方法 | 路径 | 用途 | 例子 |
|------|------|------|------|
| GET | `/api/v1/customers/{custId}/profile` | 单客户 360° 画像（full 视图，含风险分） | `/api/v1/customers/C0001/profile` |
| GET | `/api/v1/customers/{custId}/brief` | 简要画像（brief 视图，强制隐藏身份证和风险分） | `/api/v1/customers/C0001/brief` |
| GET | `/api/v1/customers?level=VIP3&page=0&size=20` | 客户分群查询 | 按 `level` 过滤 + 分页 |
| GET | `/api/v1/metrics/customer-overview` | 全局指标快照 | ARPU / VIP3 / 高风险计数 |
| GET | `/api/v1/audit/recent?n=20` | 最近审计记录（PoC ring buffer） | 治理可视化 |
| GET | `/actuator/health` | 健康检查 | Liveness |

**角色通过 HTTP Header 传递（PoC 简化，生产用 Keycloak OIDC）**：

```
X-User-Role: ADMIN              # 不脱敏
X-User-Role: CUSTOMER_VIEWER    # 脱敏 idCard，隐藏风险分
X-User-Role: SUPPORT            # 全脱敏 + 隐藏风险分（最小权限）
（不带 header）                  # 走 SUPPORT 策略
```

## 关键文件

```
data-service/
├── pom.xml                                Spring Boot 3.5 / Java 17
├── Dockerfile                             多阶段构建（Maven build + JRE run）
├── src/main/java/com/datafabric/dataservice/
│   ├── DataServiceApplication.java        启动类（@ConfigurationPropertiesScan）
│   ├── config/CubeProperties.java         Cube 连接配置（host/port/secret）
│   ├── client/CubeClient.java             Cube REST 客户端 + CubeQuery 构造器
│   ├── domain/
│   │   ├── CustomerProfileDto.java        单客户画像 DTO（record）
│   │   ├── CustomerOverviewDto.java       全局指标 DTO（record）
│   │   └── CustomerProfileService.java    业务服务（调用 Cube）
│   ├── governance/                        ← W2.3 嵌入式治理（AOP）
│   │   ├── GetProfile.java                @GetProfile 注解（切入点）
│   │   ├── MaskingUtil.java               phone / idCard 脱敏工具
│   │   ├── FieldPolicy.java               字段策略（mask/hide 哪些）
│   │   ├── MetadataClient.java            元数据接口（OpenMetadata stub）
│   │   ├── StubMetadataClient.java        按角色生成策略（PoC 实现）
│   │   ├── DataMaskingAspect.java         @Around 脱敏切面
│   │   ├── AuditEvent.java                审计事件 record
│   │   ├── AuditLogger.java               审计接口
│   │   ├── LoggingAuditLogger.java        SLF4J + ring buffer 实现
│   │   ├── AuditAspect.java               @AfterReturning 审计切面
│   │   ├── LineageAspect.java             OpenLineage 事件切面
│   │   └── AuditController.java           /api/v1/audit/recent 查询端点
│   ├── api/
│   │   ├── CustomerProfileController.java REST controller（含 @GetProfile 注解）
│   │   └── ApiExceptionHandler.java       统一错误响应
│   └── exception/CustomerNotFoundException.java
├── src/main/resources/application.yml
└── src/test/java/.../
    ├── CustomerProfileControllerTest.java  MockMvc 单元测试
    └── governance/DataMaskingAspectTest.java  脱敏切面单元测试
```

## 启动方式

### 方式 A：Docker Compose（推荐，端到端验证）

```bash
cd poc
docker compose up -d data-service
# 首次构建约 5-8 分钟（Maven 下载依赖）
docker compose logs -f data-service
```

### 方式 B：host 本地运行（开发迭代）

前置：本地装 Maven + JDK 17+（路径 `D:/Program Files/Java/jdk-18.0.2` 已就位）。

```bash
cd poc/data-service
mvn spring-boot:run \
    -Dspring-boot.run.arguments="--cube.host=localhost --cube.port=4000"
```

### 方式 C：仅跑测试（不需 Cube 在跑）

```bash
cd poc/data-service
mvn test
```

## 验证

```bash
# 1. 端到端（依赖 Cube.dev 已启动且 verify-cube.sh 通过）
bash scripts/verify-data-service.sh

# 2. 手动 cURL
curl http://localhost:8090/api/v1/customers/C0001/profile | jq .
curl 'http://localhost:8090/api/v1/customers?level=VIP3&size=5' | jq .
curl http://localhost:8090/api/v1/metrics/customer-overview | jq .
```

预期：
- `/customers/C0001/profile` 返回 200 + JSON，包含 custName / level / riskLevel
- `/customers/UNKNOWN/profile` 返回 404 + `{"error":"CUSTOMER_NOT_FOUND",...}`
- `/metrics/customer-overview` 返回 200 + JSON，包含 ARPU / VIP3 / 风险计数

## 关键设计决策

### 1. 不直接连数据库
全部通过 Cube.dev。**业务代码不再感知 MySQL/CH/PG 的存在** —— 这就是语义层的价值。

### 2. CubeQuery immutable builder
`CubeQuery` 是 `record`，构造完不可变。多线程安全、可作为缓存 key。

### 3. 异常分层
- `CustomerNotFoundException` → 404
- `RestClientException` → 502（Cube 不可达）
- `IllegalArgumentException` → 400（参数校验）
- 其他 → 500

错误体遵循 RFC 7807 简化版：`{"error":"CODE","message":"...","timestamp":"..."}`

### 4. record DTO
Java 17 record —— 不可变 + 自动 `equals/hashCode/toString`，符合 immutability 原则。

### 5. W2.3 嵌入式治理 —— AOP 切面，业务零侵入

```
请求 → Controller @GetProfile
            │
            ├── DataMaskingAspect  (@Around)       按角色脱敏 phone/idCard/riskScore
            │       ↓
            │   service.findById()  →  CustomerProfileDto（原始）
            │       ↓
            │   构造 new CustomerProfileDto(脱敏字段...)
            │       ↓
            ├── AuditAspect         (@AfterReturning)  AuditEvent → LoggingAuditLogger
            │
            └── LineageAspect       (@AfterReturning)  OpenLineage event → 日志
                                    ↓
                                返回已脱敏 DTO
```

**核心思想**：controller 只加一行 `@GetProfile`，治理策略全部由 AOP 统一切入。改标签（OpenMetadata）→ 改策略 → 改脱敏，**不需要重新编译业务代码**。

### 6. record DTO 与 @Around 配合

Java record 不可变（无 setter），无法用 `@AfterReturning + setter` 模式。
改用 `@Around` 拦截 `proceed()` 返回值，构造新的 masked record 返回。
效果等价，且保持 immutability 原则不破。

### 7. 视图模式驱动脱敏强度

`@GetProfile(view = "brief")` 强制隐藏身份证和风险分，**无视角色**（最小披露原则）。
适合客服快速查询场景；`view = "full"` 按角色策略。

### 8. 审计策略可插拔

`AuditLogger` 接口 + `LoggingAuditLogger` 默认实现（SLF4J + 内存 ring buffer）。
生产环境实现 `ClickHouseAuditLogger`（HTTP 8123 INSERT），见 `clickhouse-init/02-audit-log.sql`。
切换实现零代码改动 controller / service。

## 已知问题

- 暂无认证（Spring Security 待加）—— 设计文档提到 Keycloak OIDC + `@PreAuthorize`，
  PoC 用 HTTP header `X-User-Role` 模拟角色
- 审计日志 PoC 走 SLF4J，生产实现切 `ClickHouseAuditLogger`（schema 已就位）
- 行级过滤（按 risk_level 限制可见行）未实现，留给后续 iteration

## W2.4 Pact 契约测试

**Consumer 端测试**（data-service → Cube.dev）：在 `src/test/java/.../pact/CubeClientPactTest.java`

```
mvn test  # 跑全部测试，包括 Pact consumer
```

跑完后会在 `target/pacts/data-service-cube-dev.json` 生成 Pact 契约文件。

**4 个 Pact 场景**：

| Pact 方法 | 场景 | 验证 |
|---|---|---|
| `customerCountPact` | 单 measure 查询（customerCount） | 解析 `data[0].column` 为 Long |
| `customerProfilePact` | 单客户 360° 画像（custId filter） | 跨源 JOIN 返回的字段对齐 |
| `vip3SearchPact` | VIP3 分群查询（filter + order + limit） | 多行返回 + 中文 custName 解码 |
| `metricsOverviewPact` | 全局指标快照（5 measure） | 多 measure 同时返回 |

**契约的价值**：

1. **Cube 升级保护**：Cube 0.36 → 0.37 时，重跑 Pact 测试即可发现破坏性变更（如响应字段重命名）
2. **文档化**：pact JSON 是机器可读的 API 契约文档
3. **双向验证**：未来可加 Pact broker，让 Cube provider 端验证 pact JSON，闭环

**Provider 端验证**（data-service 作为上游 API 的 provider）：当前用 `CustomerProfileControllerTest` 的 MockMvc 覆盖。如要完整 Pact provider verification，可加 `pact-jvm-provider-spring` + `@PactFolder("pacts")` + `@State` 注解，按需引入。

### 跑 Pact 测试（不依赖 Docker）

```bash
cd poc/data-service
# Pact consumer 测试不需要 Cube 在跑，所有响应由 Pact mock server 提供
mvn test -Dtest=CubeClientPactTest

# 生成的契约
cat target/pacts/data-service-cube-dev.json
```
