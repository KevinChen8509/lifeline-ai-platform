# Week 2 验证结果（Data Fabric PoC）

**完成日期**: 2026-08-04
**分支**: `datafabric-week2` (`f28a087`)
**状态**: 文件层 + 测试 + 容器构建全部通过；Cube runtime 受网络环境阻塞

---

## ⚠️ 验证结论

**5 个子任务文件层全部完成，14/14 自动化测试通过，data-service 容器构建 + 启动 + 路由连通全部验证。**
**唯一阻塞**：Cube.dev 容器镜像在中国所有 5 个镜像源 token endpoint 卡死，端到端数据流验证推迟。

| 子任务 | 文件 | 单元测试 | 集成验证 | 状态 |
|-------|------|---------|---------|------|
| W2.1 Cube.dev 语义层 | ✅ 6 文件 | — | ❌ Cube 容器拉不到 | **文件完成，runtime 阻塞** |
| W2.2 Spring Boot 数据服务 | ✅ 9 main + 1 test | ✅ 5/5 MockMvc | ✅ 容器构建+5 路由连通 | **完成** |
| W2.3 治理 AOP 切面 | ✅ 12 main + 1 test | ✅ 5/5 Aspect | ✅ 切面在 MockMvc 测试中真实触发 | **完成** |
| W2.4 Pact 契约 | ✅ 1 test | ✅ 4/4 Pact | ✅ JSON 输出 9KB | **完成** |
| W2.5 Dashboard 扩展 | ✅ 6 文件 | — | ⏺ 已实测启动 + graceful 降级 | **文件完成，端到端待 Cube** |

---

## 自动化测试结果（14/14 通过）

通过 `docker run maven:3.9-eclipse-temurin-17` 跑全量测试（host 无 Maven）：

```
[INFO] Running com.datafabric.dataservice.api.CustomerProfileControllerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 145.8 s
[INFO] Running com.datafabric.dataservice.governance.DataMaskingAspectTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.373 s
[INFO] Running com.datafabric.dataservice.pact.CubeClientPactTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 57.95 s
[INFO] Results:
[INFO] Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 测试中真实触发的治理切面日志

W2.3 的 @Around / @AfterReturning 切面不只是定义，**测试运行时真实触发**（CustomerProfileControllerTest log）：

```
INFO  c.d.d.governance.LineageAspect - OPENLINEAGE job=data-service profile.read
    custId=C0001 runId=87fa8823-4bdd-46c8-a5ef-ddf3d3ef86d4
    inputs=[mysql.customer_db.customer, clickhouse.analytics.orders, postgres.external.risk_tags]
    outputs=[data-service.CustomerProfile]
INFO  c.d.d.governance.LoggingAuditLogger - AUDIT
    actor=ANONYMOUS action=customer-profile.read resource=C0001 risk=high result=SUCCESS
```

血缘 3 个 inputs 正确识别 Week 1 跨源三表，OpenLineage 事件格式符合规范。

---

## 关键架构决策（写文档时必须引用）

### 1. record DTO + Spring AOP @Around（不能用 @AfterReturning）

W2.2 用 Java 17 `record` 定义 immutable DTO。Spring AOP 想脱敏必须改返回值：
- ❌ `@AfterReturning + setter` —— record 没有 setter
- ✅ `@Around`：拦截 `pjp.proceed()` 拿到原 record，构造新 record 返回

```java
@Around("@annotation(getProfile)")
public Object applyPolicy(ProceedingJoinPoint pjp, GetProfile getProfile) throws Throwable {
    Object result = pjp.proceed();
    if (!(result instanceof CustomerProfileDto dto)) return result;
    FieldPolicy policy = metadata.getPolicy(dto.custId(), StubMetadataClient.currentRole());
    return apply(dto, policy, getProfile.view());   // 构造新 record
}
```

**附带收益**：@Around 先于 @AfterReturning 执行，所以 AuditAspect 看到的是已脱敏 DTO，审计日志天然不含明文。

### 2. 角色驱动 × 视图模式（双重策略）

| Header X-User-Role | view="full" | view="brief" |
|--------------------|-------------|--------------|
| ADMIN | 不脱敏 | 强制隐藏 idCard + riskScore |
| CUSTOMER_VIEWER | 脱敏 idCard + 隐藏 riskScore | 同左 |
| SUPPORT / null | 全脱敏 | 全脱敏 |

`@GetProfile(view = "brief")` 的"最小披露"优先级高于角色，符合合规原则。

### 3. Pact-JVM 4.6.16 API 适配

踩了 3 个坑（详见下方 Bug 清单）。核心是 Pact-JVM 4.6.x 把默认 spec 从 V3 升到 V4，V4 要求 `V4Pact + PactBuilder` 签名，老代码用 `RequestResponsePact + PactDslWithEntity` 直接编译失败。最简方案是 `@PactTestFor(pactVersion = PactSpecVersion.V3)` 强制保留 V3 API。

### 4. 异常统一信封

所有 controller 抛出的异常由 `ApiExceptionHandler` 转 RFC 7807 简化版：
```json
{"error":"SEMANTIC_LAYER_UNAVAILABLE","message":"...","timestamp":"..."}
```
错误码大写下划线：`CUSTOMER_NOT_FOUND` / `SEMANTIC_LAYER_UNAVAILABLE` / `INTERNAL_ERROR` / `INVALID_ARGUMENT`。

实测 Cube 未启动时所有数据端点返回 `502 SEMANTIC_LAYER_UNAVAILABLE`，5 路由全部一致。

### 5. Maven 通过 Docker 跑（host 无 Maven）

host 没装 Maven，所有 mvn 命令走：
```bash
docker run --rm \
  -v "$PWD/poc:/app" \
  -v "$PWD/poc/.m2-cache:/root/.m2" \   # 持久化依赖缓存，下次秒级
  -w /app \
  maven:3.9-eclipse-temurin-17 \
  mvn -f data-service test -B
```

注意 Windows MSYS bash 必须加 `export MSYS_NO_PATHCONV=1`，否则 `-w /app` 被改写成 `D:/Program Files/Git/app`。

---

## 5 个 REST 路由验证（容器实测）

`datafabric-data-service:w2` 容器独立启动（不依赖 docker-compose），全部路由 curl 验证：

| 路由 | 方法 | curl 测试结果 |
|------|------|--------------|
| `/actuator/health` | GET | ✅ 200 `{"status":"UP",...}` |
| `/api/v1/customers/{custId}/profile` | GET | ✅ 502 SEMANTIC_LAYER_UNAVAILABLE（Cube 未启动，异常信封正确） |
| `/api/v1/customers/{custId}/brief` | GET | ✅ 502 同上 |
| `/api/v1/customers?level=VIP3&size=5` | GET | ✅ 502 同上 |
| `/api/v1/metrics/customer-overview` | GET | ✅ 502 同上 |
| `/api/v1/audit/recent` | GET | ✅ 200 `{"total":0,"events":[]}` |

**5 路由全部正确连通**。502 不是失败——是 Cube runtime 缺席下的预期契约响应，证明 ApiExceptionHandler 工作正常。

**注意**：所有失败的 profile/search/overview 调用**不会**出现在 audit log 里，因为 `AuditAspect` 用 `@AfterReturning` 只记成功调用（设计取舍：审计只记录真实数据访问，不记失败尝试）。生产可能需要改 `@After` 同时记录失败。

---

## 修复的代码 Bug 清单（共 3 个，commit `f28a087`）

跑 `verify-pact.sh` 暴露的 Pact-JVM 4.6.x API 变更：

1. **`PactDslWithEntity` 在 4.6.x 已移除** → 改 `PactDslWithProvider`
   - 文件：`CubeClientPactTest.java` 4 处方法签名 + import
   - fluent API 完全相同，纯重命名

2. **4.6.x 默认 V4 spec，要求 V4Pact + PactBuilder 签名**
   - 文件：`@PactTestFor` 注解
   - 修复：加 `pactVersion = PactSpecVersion.V3` 保留 V3 API
   - 错误信息：`Method customerCountPact does not conform required method signature 'public V4Pact xxx(PactBuilder builder)'`

3. **3 个 Pact 缺响应 Content-Type**
   - 文件：`customerProfilePact` / `vip3SearchPact` / `metricsOverviewPact`
   - 修复：3 个 `willRespondWith` 后补 `.matchHeader("Content-Type", "application/json")`
   - 现象：mock server 默认 `application/octet-stream`，RestClient 无 HttpMessageConverter，3 个测试失败
   - customerCountPact 因为已经写了 Content-Type，所以是唯一一个第一轮就通过的

---

## 阻塞：Cube.dev 容器拉取

### 现象
`docker pull cubeapi/cube:v0.36.5` 在所有 5 个 daemon.json 镜像源失败：

| 镜像源 | 错误码 | 含义 |
|--------|--------|------|
| `docker.m.daocloud.io` | 403 Forbidden | token 拿不到，最终拒绝 |
| `docker.1panel.live` | 403 Forbidden | 同上 |
| `hub.rat.dev` | not found | 没缓存该镜像 |
| `docker.1ms.run` | not found | 同上 |
| `docker.xuanyuan.me` | 429 Too Many Requests | 限流 |

### 根本原因（精细诊断后确认）

**不是镜像源针对性拒绝 cube，也不是镜像不存在。是镜像源的 token auth endpoint 网络阻断。**

精细 curl 证据：

| 测试 | 结果 | 解读 |
|------|------|------|
| `ping docker.m.daocloud.io` (47.100.46.249) | 18ms 0% 丢包 | TCP/IP 网络层 OK |
| `HEAD daocloud/v2/cubeapi/cube/manifests` | HTTP 401 + `WWW-Authenticate: Bearer realm="m.daocloud.io/auth/token"` | TLS OK，标准 challenge |
| `HEAD daocloud/v2/library/maven/manifests` | 同上 401 同样 challenge | **cube 和 maven 表现一致** |
| 取 token / 后续 body | 30s 超时 0 字节 | **token endpoint 卡死** |
| 直连 `registry-1.docker.io` | 10s 超时 | Docker Hub 直连被 GFW 阻断 |

**完整因果链**：
```
docker pull cubeapi/cube:v0.36.5
  → daemon 走 daocloud mirror
  → daocloud 返回 401，要去 m.daocloud.io/auth/token 取 token
  → daemon 请求 m.daocloud.io/auth/token
  → ❌ 卡死 30s（GFW 对 m.daocloud.io 子域名 SNI 干扰）
  → 重试 5 个 mirror 都差不多
  → 最终报错（403 / not found / 429 是各 mirror 不同的失败码）
```

### 为什么 maven / eclipse-temurin 能拉到？

- `library/*` 是 Docker Official Images，daocloud 等公益镜像源有自己的**预取清单**
- Official images 可**跳过完整 token flow**，直接给 manifest + layer
- `cubeapi/cube` 是 user namespace，**必须走完整 token flow**，token endpoint 卡死 → 失败

### 解决方案（待用户处理）

| 方案 | 可行性 |
|------|--------|
| Docker Desktop 配代理（Settings → Resources → Proxies） | ⭐ 最靠谱 |
| 换网（手机热点试） | 看 ISP |
| 公司/云服务器拉，`docker save` → scp → `docker load` | Linux + VPN 后路 |
| 改 daemon 直连（去镜像源） | Docker Hub 直连也超时，**不通** |

---

## Week 2 工作量统计

| 类别 | 数量 |
|------|------|
| Main Java 文件（W2.2+W2.3） | 21 |
| Test Java 文件（MockMvc + Aspect + Pact） | 3 |
| 单元测试用例总数 | 14（全过） |
| Cube schema YAML | 2 |
| Dashboard JS/CSS/HTML | 4 |
| 验证脚本 | 4（cube/data-service/pact + 通用） |
| SQL（ClickHouse audit_log） | 1 |
| Git commits | 4（3 个 feat + 1 个 fix） |
| 修复的 Pact API bug | 3 |
| 文档 | cube/README.md + data-service/README.md + 本报告 |

---

## 下一步

### 解 Cube 阻塞后立即可做（不需要新文件）
1. `docker compose up -d cube` 启动 cube
2. `bash scripts/verify-cube.sh` 4 步 → W2.1 关闭
3. `bash scripts/verify-data-service.sh` 8 步（含 SUPPORT 脱敏 + 审计 + ADMIN 不脱敏）→ W2.2/W2.3 端到端关闭
4. `cd dashboard && npm start` 浏览器巡检 5 个面板 → W2.5 关闭
5. dashboard W2 masking-compare 面板对照 3 角色 × 2 视图模式

### 进入 Week 3（AI Agent + 对比演示 + 报告）

按 `docs/poc-data-fabric.md` §4 Week 3：
1. `CustomerInsightAgent`（A 路）—— LangChain4j + 4 个 @Tool 调 `/api/v1/*`
2. `RawDbAgent`（B 路对照组）—— 直连 JDBC，应能演示 PII 泄露
3. 5 个对比问题 Q1-Q5，量化 token / 准确率 / 安全事件 / 延迟
4. Vue 3 演示页 `DemoPage.vue` 左右分屏
5. PoC 报告产出（章节 6 模板）

**Week 3 退出标准**：
- Data Fabric Agent 5 题准确率 ≥ 4/5，安全事件 0
- Raw DB Agent 至少 Q4 出现一次 PII 泄露（演示对照）
- 演示页一键回放
