# IoT 设备数据订阅功能 — 测试策略文档

> 版本: v3.0 | 日期: 2026-04-23 | 状态: 评审中
> v3.0 变更: 新增 v1.0 测试缺口(10)、Step 1 重构保障(9)、Step 2 消费组集成(13)、灰度验证矩阵(11)、回滚测试(6)、兼容性矩阵(14)；总计 ~274 条
> v2.1 变更: 新增事务回滚(6)、StormGuard 降级(4)、订阅缓存一致性(5)、数据类型转换(6) 测试；扩充 Staging 容灾(4) + Nightly k6 脚本；修复性能测试 ID 冲突；总计 ~199 条
> v2.0 变更: 纳入 Party Mode 评审意见 — 修复 KRaft 配置、补充 Lua 匹配引擎/API 权限/配额/数据边界测试、前端扩充至 35 条、新增性能测试和 CI/CD Pipeline 定义

---

## 1. 测试策略概述

### 1.1 测试分层

| 层级 | 范围 | 工具 | 目标覆盖率 |
|------|------|------|-----------|
| 单元测试 | Service / Repository / Rule / Lua 脚本 | JUnit 5 + Mockito | ≥ 80% |
| 集成测试 | Kafka + Redis + MySQL | Testcontainers (KRaft) + SpringBootTest | 关键路径 100% |
| API 测试 | REST API 端到端 | MockMvc + REST Assured | 所有端点 |
| 前端组件测试 | 组件 + Store + Composable | Vitest + Vue Test Utils | 核心组件 ≥ 70% |
| 前端 E2E 测试 | 用户核心路径 | Playwright | P0 场景 100% |
| 性能测试 | 吞吐量 / 延迟 / 稳定性 | k6 + Kafka plugin | 定期执行 |

### 1.2 Testcontainers 配置

> **重要**: 使用 KRaft 模式（非 embedded ZK），与生产环境一致。

```java
@Testcontainers
@SpringBootTest
public abstract class BaseIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
        .withKRaft();  // KRaft 模式，与生产一致

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
        DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("iot_subscription_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
    }
}
```

### 1.3 Mock 策略矩阵

| 外部依赖 | 隔离方式 | 说明 |
|---------|---------|------|
| Webhook 目标端点 HTTP | **WireMock / MockWebServer** | 模拟 200/4xx/5xx/超时/连接拒绝 |
| 定时任务调度 | `@MockBean` + `await().atMost()` | 不用 Thread.sleep |
| Redis 分布式锁 | **真实 Redis 容器** | 锁原子性 mock 不出来 |
| Kafka 本身 | **真实 Kafka 容器**（KRaft） | 不用 EmbeddedKafka |
| 第三方时间源 | 注入可控 `Clock` Bean | 退避时间验证 |
| 密钥主密钥 | 测试专用固定值 | 不依赖 Vault |

---

## 2. 数据接入边界条件测试（8 条）

> 评审补充：数据层最易出 bug 的边界场景，原方案零覆盖。

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| D01 | 数据点 identifier 不在物模型中 | DeviceMessage 含未知 identifier | 忽略该数据点，其他正常处理 |
| D02 | 数据类型不匹配 | 物模型定义 float，实际收到 string "abc" | Schema 校验拒绝，不入库不匹配 |
| D03 | 数据点值为 null / NaN / Infinity | float 类型特殊值 | 明确拒绝或记录 quality=差 |
| D04 | 时间戳为未来时间 | ts = now + 1h | 接受但标记，不影响冷却期计算 |
| D05 | 时间戳为极旧时间 | ts = now - 7d | 接受但不影响冷却期 |
| D06 | payload 超大（> 1MB） | 单条消息体超限 | Kafka 拒绝或截断，不崩溃 |
| D07 | 批量设备数据并发接入 | 100 条不同设备数据 | 全部正确入库，无数据错乱 |
| D08 | 同一设备高频上报 | 1 秒内 50 条同设备数据 | 按 key 分区有序，无丢失 |

---

## 3. Kafka 集成测试（26 条）

### 3.1 数据接入（ingestion-group）

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| K01 | 正常设备数据接入 | DeviceMessage（temperature=25.6） | DB 更新 last_value，Redis 时序写入，offset 提交 |
| K02 | 规则匹配命中 → alert.event 发布 | DeviceMessage（temperature=85） | alert.event Topic 收到消息，key=subscriptionId |
| K03 | 规则匹配未命中 | DeviceMessage（temperature=25） | alert.event Topic 无新消息 |
| K04 | 推送疲劳控制过滤 | 连续 3 次同规则触发 | 仅第 1 次发送到 alert.event，后 2 次被 stormGuard 过滤 |
| K05 | 冷却期过滤 | 冷却期内重复触发 | 冷却期内不推送 |
| K06 | 数据处理异常 → DLT | 抛出 RuntimeException | DefaultErrorHandler 将消息发到 DLT **并提交 offset**（跳过毒消息） |
| K07 | offset 手动提交验证 | 处理成功后检查 ConsumerGroup offset | offset 已提交 |
| K08 | 处理中宕机 → offset 未提交 | JVM 强杀（不抛异常） | offset 未提交，重启后重新消费 |
| K09 | DB 写入成功 + Kafka 发送失败 | DB 事务提交后 kafkaTemplate 抛异常 | 接受最终一致（数据已入库但告警未发送），人工补偿 |

### 3.2 Webhook 推送（dispatch-group）

> 使用 WireMock 模拟第三方端点。

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| K10 | 正常推送 → 200 OK | AlertEvent → WireMock 200 | delivery_log SUCCESS，endpoint consecutive_failures 重置 |
| K11 | 推送签名验证 | 检查 WireMock 收到的 Headers | X-Webhook-Signature 正确，timestamp 秒级 |
| K12 | 推送 → 4xx 响应 | AlertEvent → WireMock 400 | 不重试，delivery_log 标记 DEAD |
| K13 | 推送 → 5xx 响应 | AlertEvent → WireMock 500 | 触发重试，delivery_log 标记 RETRYING |
| K14 | 推送超时 | AlertEvent → WireMock 延迟 15s | 触发重试，delivery_log 标记 RETRYING |
| K15 | 端点不存在 → DLT | endpointId 无效 | 消息进入 alert.event.dlt |
| K16 | 推送 payload 格式验证 | 检查 WireMock 收到的 body | 包含 event_id, event_type, subscription, device, data_point |
| K17 | 幂等消费验证 | 相同 event_id 重复推送 | 第二次跳过（delivery_log 唯一索引），不重复推送 |

### 3.3 死信处理（dlt-monitor-group）

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| K18 | 死信入库 | DLT 消息 | delivery_log 记录 DEAD 状态 |
| K19 | 死信通知 | DLT 消息 | 发送站内通知给订阅用户 |
| K20 | 手动重试死信 | 调用 retry API | 重新推送，成功则状态更新 |

### 3.4 Kafka 基础设施

| # | 用例名称 | 输入 | 预期 | 执行环境 |
|---|---------|------|------|---------|
| K21 | Consumer Group rebalance | 新增消费者实例 | 分区重新分配，无消息丢失 | CI（Testcontainers） |
| K22 | Broker 宕机恢复 | 杀掉 1 个 Kafka 节点 | 消费继续（RF=3 容忍 1 节点故障） | **Staging（手动）** |
| K23 | Topic 分区有序 | 同一 deviceId 发送 10 条 | 消费顺序与发送顺序一致 | CI |
| K24 | 压缩配置验证 | 检查 Topic 配置 | device.raw=lz4, alert.event=snappy | CI |
| K25 | 消费组 Lag 告警 | 产生大量消息 | Prometheus 告警规则验证 | **promtool test rules** |
| K26 | 多实例 Redis 锁竞争 | 2 个实例同时执行重试扫描 | 仅 1 个实例执行，另 1 个跳过 | CI |
| K27 | Redis 锁过期恢复 | 锁持有者宕机（模拟 TTL 过期） | 30s 后锁过期，其他实例接管 | CI |

> **注**: K22 需要至少 3 个 Kafka Broker 才能测试 RF=3 容灾，Testcontainers 单节点无法覆盖，标为 Staging 手动测试。K25 告警规则用 `promtool test rules` 验证，不占 Java 集成测试资源。

---

## 4. Lua 匹配引擎测试（8 条）

> 评审补充：核心计算逻辑原方案零覆盖。

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| L01 | 设备级订阅命中 | deviceId 匹配 + 阈值满足 | 返回对应规则 |
| L02 | 产品级订阅命中 | productId 匹配 + 阈值满足 | 返回对应规则 |
| L03 | 设备级 + 产品级同时命中 | 同一设备同时被两种订阅覆盖 | 合并去重，不重复推送 |
| L04 | 订阅规则 disabled 但缓存未刷新 | Redis 中存在已停用订阅的缓存 | 匹配到后发送，由 dispatch 阶段二次校验状态 |
| L05 | data_point_ids=null（所有点） | 订阅不限数据点 | 任意数据点变更均触发 |
| L06 | data_point_ids=指定列表 | 仅订阅 temperature | humidity 变更不触发 |
| L07 | 多实例同时收到同设备消息 | stormGuard Redis TTL 原子操作 | EXPIRE NX 保证只有一个实例推送 |
| L08 | Redis 连接中断 | Lua 脚本执行失败 | 降级：不推送 + 记录错误日志（不丢弃数据） |

---

## 5. at-least-once 关键路径测试（4 条）

> 评审补充：验证 at-least-once 语义成立的核心条件。

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| R01 | dispatch-group 消费成功后宕机 | 消费成功 + offset 未提交 + 进程重启 | 重启后重复消费，幂等保护跳过 |
| R02 | rebalance 期间同消息被两实例拉取 | partition 重分配 | 第二个实例幂等跳过（delivery_log 唯一索引） |
| R03 | 重试推送成功但 log 状态更新失败 | DB 写 status=SUCCESS 抛异常 | 下次扫描重复推送一次（接收方 event_id 去重） |
| R04 | endpoint 被删除后重试扫到关联日志 | endpointRepo.findById 返回 empty | 跳过该日志，标记 DEAD |

---

## 6. 失败场景测试（15 条）

### 6.1 重试场景

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| F01 | 首次推送失败 → 重试 | 5xx 响应 | nextRetryAt = now + 30s |
| F02 | 指数退避验证（单调递增） | 连续失败 | 每次重试间隔 > 上一次（不卡死具体秒数） |
| F03 | 最大重试次数 | 5 次全部失败 | 状态标记 DEAD |
| F04 | 重试窗口过期 | 超过 2 小时 | 状态标记 DEAD，不再重试 |
| F05 | 重试成功 → 恢复 | 第 3 次重试成功 | 状态标记 SUCCESS，重置 consecutive_failures |

### 6.2 熔断器场景

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| F06 | CLOSED → OPEN | 连续 5 次失败 | 熔断器打开，后续推送直接跳过 |
| F07 | OPEN → HALF_OPEN | 等待 1 分钟（注入可控 Clock） | 允许 1 次试探推送 |
| F08 | HALF_OPEN → CLOSED（恢复） | 试探推送成功 | 熔断器关闭，恢复正常推送 |
| F09 | HALF_OPEN → OPEN（继续失败） | 试探推送失败 | 熔断器重新打开 |
| F10 | 熔断触发通知 | CLOSED → OPEN | 发送 ENDPOINT_FAILURE 站内通知 |
| F11 | 恢复触发通知 | OPEN → CLOSED | 发送 ENDPOINT_RECOVERED 站内通知 |

### 6.3 Semaphore 隔离

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| F12 | 并发限流 | 单端点 6 个并发推送 | 5 个执行，第 6 个等待 2s 后进入重试队列 |
| F13 | Semaphore 释放验证 | 推送完成（含异常路径） | Semaphore 许可释放，finally 块保障 |
| F14 | 慢端点不影响快端点 | 端点 A 慢 + 端点 B 快 | 端点 B 不受端点 A 影响（per-endpoint 隔离） |
| F15 | Semaphore tryAcquire 超时 | 2s 内无许可 | 进入重试队列，不阻塞线程 |

---

## 7. Semaphore + CircuitBreaker 单元测试（13 条）

| # | 用例名称 | 验证内容 |
|---|---------|---------|
| SC01 | 新端点 Semaphore 默认 5 许可 | `getSemaphore(id).availablePermits() == 5` |
| SC02 | acquire → release 后许可恢复 | 可用许可回到 5 |
| SC03 | 5 次 acquire 后无许可 | `tryAcquire(0) == false` |
| SC04 | 熔断器初始状态 CLOSED | `isCircuitOpen(id) == false` |
| SC05 | 5 次失败后熔断打开 | `isCircuitOpen(id) == true` |
| SC06 | 成功重置 HALF_OPEN | `recordSuccess` 后状态 → CLOSED |
| SC07 | HALF_OPEN 失败立即 OPEN | 试探失败 → 立即 OPEN |
| SC08 | OPEN 超过 1 分钟进入 HALF_OPEN | `isCircuitOpen` 返回 false（允许试探） |
| SC09 | 多端点独立状态 | 端点 A OPEN 不影响端点 B |
| SC10 | concurrent access 无竞态 | 100 线程并发 recordFailure → 状态正确，**耗时 < 500ms** |
| SC11 | CircuitState computeIfAbsent 线程安全 | 并发获取同一端点状态 → 返回同一实例 |
| SC12 | EndpointId 不存在时安全返回 | 不存在的 ID → 返回默认 Semaphore, isCircuitOpen=false |
| SC13 | 已删除端点资源清理 | 端点删除后 Semaphore/CircuitState 可 GC | 考虑 WeakHashMap 或定期清理 |

---

## 8. API 端点测试（42 条）

### 8.1 Webhook 端点管理

| # | 用例 | 方法 | 路径 | 预期 |
|---|------|------|------|------|
| A01 | 创建端点 | POST | /api/v1/webhook-endpoints | 201, 返回 secret |
| A02 | 创建端点 — URL 非法 | POST | /api/v1/webhook-endpoints | 400 |
| A03 | 创建端点 — 配额超限（50端点/租户） | POST | /api/v1/webhook-endpoints | 429, "已达上限 (50/50)" |
| A04 | 更新端点 | PUT | /api/v1/webhook-endpoints/{id} | 200 |
| A05 | 删除端点 — 有关联订阅 | DELETE | /api/v1/webhook-endpoints/{id} | 409, 提示关联订阅数 |
| A06 | 删除端点 — 无关联 | DELETE | /api/v1/webhook-endpoints/{id} | 204 |
| A07 | 测试推送 | POST | /api/v1/webhook-endpoints/{id}/test | 200, 返回推送结果 |
| A08 | 测试推送 — 频率超限 | POST | /api/v1/webhook-endpoints/{id}/test | 429 |
| A09 | 密钥轮换 | PATCH | /api/v1/webhook-endpoints/{id}/rotate-secret | 200, 返回新密钥 |
| A10 | 健康指标 | GET | /api/v1/webhook-endpoints/{id}/health | 200, 返回健康数据 |
| A11 | 删除端点后配额释放 | DELETE + POST | /api/v1/webhook-endpoints | 先删除再创建，不触发 429 |

### 8.2 订阅管理

| # | 用例 | 方法 | 路径 | 预期 |
|---|------|------|------|------|
| A12 | 创建订阅 — 设备级 | POST | /api/v1/subscriptions | 201 |
| A13 | 创建订阅 — 设备类型级 | POST | /api/v1/subscriptions | 201 |
| A14 | 创建订阅 — 无效规则 | POST | /api/v1/subscriptions | 400 |
| A15 | 创建订阅 — 配额超限（100订阅/用户） | POST | /api/v1/subscriptions | 429, "已达上限 (100/100)" |
| A16 | 获取订阅列表 | GET | /api/v1/subscriptions | 200, 分页 |
| A17 | 启用/暂停订阅 | PATCH | /api/v1/subscriptions/{id}/status | 200 |
| A18 | 更新订阅规则 | PUT | /api/v1/subscriptions/{id} | 200 |
| A19 | 删除订阅 | DELETE | /api/v1/subscriptions/{id} | 204 |
| A20 | 订阅无权限设备 | POST | /api/v1/subscriptions | 403, 设备数据权限校验 |

### 8.3 推送日志

| # | 用例 | 方法 | 路径 | 预期 |
|---|------|------|------|------|
| A21 | 推送日志列表 | GET | /api/v1/webhook-logs | 200, 分页 |
| A22 | 日志筛选 — 按状态 | GET | /api/v1/webhook-logs?status=FAILED | 200, 仅失败记录 |
| A23 | 日志筛选 — 按时间范围 | GET | /api/v1/webhook-logs?startTime=...&endTime=... | 200 |
| A24 | 日志详情 | GET | /api/v1/webhook-logs/{id} | 200, 含请求/响应 |
| A25 | 手动重试 | POST | /api/v1/webhook-logs/{id}/retry | 200 |
| A26 | 推送统计 | GET | /api/v1/webhook-logs/stats | 200, 含成功率/延迟 |

### 8.4 通知管理

| # | 用例 | 方法 | 路径 | 预期 |
|---|------|------|------|------|
| A27 | 通知列表 | GET | /api/v1/notifications | 200, 分页 |
| A28 | 未读数量 | GET | /api/v1/notifications/unread-count | 200, 数值 |
| A29 | 标记已读 | PATCH | /api/v1/notifications/{id}/read | 200 |
| A30 | 全部已读 | PATCH | /api/v1/notifications/read-all | 200 |
| A31 | 获取通知偏好 | GET | /api/v1/notification-preferences | 200 |
| A32 | 更新通知偏好 | PUT | /api/v1/notification-preferences | 200 |

### 8.5 设备管理（只读）

| # | 用例 | 方法 | 路径 | 预期 |
|---|------|------|------|------|
| A33 | 设备数据点列表 | GET | /api/v1/devices/{id}/datapoints | 200 |

### 8.6 权限与数据隔离（评审补充）

| # | 用例 | 方法 | 路径 | 预期 |
|---|------|------|------|------|
| A34 | 跨租户访问端点 | GET | /api/v1/webhook-endpoints/{otherTenantId} | 404（无权限不暴露存在性） |
| A35 | 跨租户修改订阅 | PUT | /api/v1/subscriptions/{otherTenantSubId} | 404 |
| A36 | 跨用户访问端点（同租户） | GET | /api/v1/webhook-endpoints/{otherUserId} | 404（普通用户） |
| A37 | 管理员查看所有订阅 | GET | /api/v1/subscriptions?viewAll=true | 200（仅 subscription:view-all 角色） |
| A38 | 普通用户无 view-all 权限 | GET | /api/v1/subscriptions?viewAll=true | 403 |
| A39 | 订阅无数据权限设备 | POST | /api/v1/subscriptions | 403 |
| A40 | 设备数据权限变更 → 订阅自动停用 | PATCH | 设备权限变更 | 关联订阅 status → PAUSED |
| A41 | 配额展示准确性 | GET | /api/v1/webhook-endpoints | 响应 header 含 X-Quota-Used / X-Quota-Limit |
| A42 | 租户推送限流（100 req/s） | 批量触发推送 | 超出部分排队不丢弃 | 排队延迟增加，不返回错误 |

---

## 9. 安全测试（14 条）

| # | 用例名称 | 验证内容 | 测试方式 |
|---|---------|---------|---------|
| S01 | HMAC-SHA256 签名正确性 | 签名字符串 = timestamp + "\n" + payload | 自动化 |
| S02 | 签名时间戳单位一致性 | 全链路使用秒级时间戳 | 自动化 |
| S03 | 防重放 — 过期时间戳 | 超过 5 分钟的签名验证失败 | 自动化 |
| S04 | 防重放 — 有效时间戳 | 5 分钟内的签名验证成功 | 自动化 |
| S05 | 验签使用常量时间比较 | WebhookSigner.verify 使用 MessageDigest.isEqual | **ArchUnit 规则** |
| S06 | AES-256-GCM 加密解密一致性 | 加密后解密得到原文 | 自动化 |
| S07 | 主密钥长度校验 | 非 32 字节主密钥 → 启动失败 | 自动化 |
| S08 | 密钥轮换宽限期 | 宽限期内新旧密钥均可验签 | 自动化 |
| S09 | 密钥轮换宽限期过期 | 旧密钥验签失败 | 自动化 |
| S10 | SSRF — 内网地址拒绝 | 10.x / 192.168.x URL 被拒绝 | 自动化 |
| S11 | SSRF — DNS Rebinding 防护 | 请求时二次校验解析后 IP | 自动化 |
| S12 | SSRF — HTTP Header 注入 | Headers 包含 \r\n 被过滤 | 自动化 |
| S13 | HTTPS 强制（生产） | 非 HTTPS URL 被拒绝 | 自动化 |
| S14 | 推送幂等性 | 相同 event_id 重复推送 → 跳过 | 自动化 |

---

## 10. 事务回滚测试（6 条）

> 评审补充：验证事务边界正确性，防止部分提交导致数据不一致。

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| TX-01 | DB 写入成功 + Kafka 发送失败 | DB 事务提交后 kafkaTemplate 抛异常 | 接受最终一致，数据已入库，补偿任务补发告警 |
| TX-02 | 订阅创建 + 缓存同步原子性 | 创建订阅后 Redis 写入失败 | DB 回滚或标记缓存同步待定，定时补偿同步 |
| TX-03 | 推送日志与推送结果一致性 | 推送异常 + 日志状态更新 | deliveryLog 状态正确反映实际推送结果，finally 块保障 |
| TX-04 | 乐观锁冲突补偿 | 补偿任务并发扫描同一 PENDING 记录（version 不匹配） | 仅一个实例成功更新 status，另一个跳过（version+1） |
| TX-05 | 补偿任务分布式锁竞争 | 2 实例同时执行补偿扫描 | Redis 分布式锁保证仅 1 个实例处理，另 1 个跳过 |
| TX-06 | 端点删除级联一致性 | 删除有关联订阅的端点 | 订阅 PAUSED，CircuitState 清理，Semaphore 释放，缓存失效 |

---

## 11. StormGuard 降级测试（4 条）

> 评审补充：Redis 故障时 StormGuard 降级策略验证（ADR-019）。

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| SG-01 | Redis 不可用 → 内存限流降级 | 断开 Redis 连接 | 降级到本地内存滑动窗口（60/min），日志标记 `stormGuardDegraded=true` |
| SG-02 | 内存窗口限流准确性 | 1 分钟内触发同规则 70 次 | 前 60 次正常推送，后 10 次被限流 |
| SG-03 | Redis 恢复 → 自动切回 | 重新连接 Redis | 自动切回 Redis 模式，内存窗口计数器清零 |
| SG-04 | 降级期间不丢数据 | Redis 不可用 + 正常数据流 | 所有数据正常入库，仅推送频率被降级限流 |

---

## 12. 订阅缓存一致性测试（5 条）

> 评审补充：验证 dual-write invalidation + Lua 状态检查（ADR-022），确保缓存 SLA ≤5s。

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| CACHE-01 | 订阅状态变更 → 缓存失效 | ACTIVE → PAUSED | Redis 缓存 5s 内失效，后续 Lua 匹配不再命中 |
| CACHE-02 | 双写一致性 | 更新订阅规则 | DB 和 Redis 数据一致（dual-write invalidation） |
| CACHE-03 | Lua 脚本 status 过滤 | 缓存中包含 DISABLED 订阅 | Lua 匹配到后检查 `status=="ACTIVE"`，非 ACTIVE 跳过推送 |
| CACHE-04 | 订阅级缓存 Key 格式 | 创建/更新订阅 | `sub:rules:{subscriptionId}` 格式正确，TTL 合理 |
| CACHE-05 | 缓存 miss 重建无幽灵推送 | 清空 Redis 后发送设备数据 | 从 DB 重建缓存，重建期间数据不丢、不产生幽灵推送 |

---

## 13. 数据类型转换测试（6 条）

> 评审补充：数据类型转换是规则匹配错误的常见来源，需明确覆盖。

| # | 用例名称 | 输入 | 预期 |
|---|---------|------|------|
| CONV-01 | Float 数据点整数值 | 物模型 float，实际值 25（int） | 自动转 float，阈值比较正确 |
| CONV-02 | String 值与数值阈值比较 | 物模型 float，实际值 "abc" | 类型不匹配，拒绝入库，不触发规则 |
| CONV-03 | Bool 数据点阈值规则 | 物模型 bool，规则为 > 30 | 创建时拒绝（bool 仅支持 eq/neq） |
| CONV-04 | JSON 数据点比较 | 物模型 json，规则为 > 30 | 拒绝或仅支持 eq/contains 运算符 |
| CONV-05 | Null 数据点值 | 数据点值为 null | 跳过匹配，不抛 NPE |
| CONV-06 | 数值溢出 | Double.MAX_VALUE 作为阈值 | 不崩溃，合理处理（拒绝或裁剪至安全范围） |

---

## 14. 前端测试（35 条）

### 10.1 组件测试（20 条）

| # | 用例 | 组件 | 验证内容 |
|---|------|------|---------|
| E01 | RuleEditor — 阈值规则 | RuleEditor.vue | 选择运算符 + 输入阈值 → 生成正确 form 数据 |
| E02 | RuleEditor — 数据类型驱动 | RuleEditor.vue | float 显示全部运算符，bool 仅 eq，json 无阈值选项 |
| E03 | RuleEditor — 离线规则 | RuleEditor.vue | 切换到离线规则 → 显示超时分钟数输入 |
| E04 | RuleEditor — 多规则添加/删除 | RuleEditor.vue | 添加 2 条规则 → 删除第 1 条 → 剩余 1 条 |
| E05 | KeyValueEditor — 添加/删除 | KeyValueEditor.vue | 添加 3 行 → 删除 1 行 → 剩余 2 行 |
| E06 | KeyValueEditor — 大小写去重 | KeyValueEditor.vue | 输入 Content-Type 后提示已存在 |
| E07 | KeyValueEditor — 防注入 | KeyValueEditor.vue | 输入含 \r\n 的值 → 被过滤 |
| E08 | DeviceTree — 懒加载 | DeviceTree.vue | 点击展开 → 触发 API 调用 |
| E09 | DeviceTree — 搜索防抖 | DeviceTree.vue | 快速输入 3 字符 → 仅触发 1 次 API（300ms 防抖） |
| E10 | StatusStepper — 向导模式 | StatusStepper.vue | 3 步显示，当前步高亮 |
| E11 | StatusStepper — 时间线模式 | StatusStepper.vue | 垂直时间线，每步展示时间和结果 |
| E12 | TestPushDialog — 固定/自定义切换 | TestPushDialog.vue | Tab 切换 → payload 编辑区内容变化 |
| E13 | TestPushDialog — 频率超限 | TestPushDialog.vue | 连续点击 4 次 → 按钮禁用 + 倒计时 |
| E14 | RotateSecretConfirm — 名称匹配 | RotateSecretConfirmDialog.vue | 输入错误名称 → 确认按钮禁用 |
| E15 | EndpointHealthDot — 四色逻辑 | EndpointHealthDot.vue | 传入不同 successRate → 渲染对应颜色 |
| E16 | SecretRevealDialog — 倒计时 | SecretRevealDialog.vue | 5 分钟倒计时显示，到时自动关闭 |
| E17 | EndpointFormDialog — SSRF 提示 | EndpointFormDialog.vue | 输入内网 IP → 红色提示 |
| E18 | EndpointFormDialog — 配额展示 | EndpointFormDialog.vue | 弹窗顶部显示 "已使用 12/50 个端点" |
| E19 | NotificationBadge — 未读计数 | NotificationBadge.vue | unreadCount=5 → 显示红色数字 |
| E20 | JsonViewer — 大 JSON 折叠 | JsonViewer.vue | 传入 1000+ 行 JSON → 仅展开第一层 |

### 10.2 Store 测试（8 条）

| # | 用例 | Store | 验证内容 |
|---|------|-------|---------|
| E21 | 创建端点流程 | useEndpointStore | create → secretOnceVisible 有值，关闭后清空 |
| E22 | Stepper 状态持久化 | useSubscriptionStore | createFlowState 写入 sessionStorage，刷新后恢复 |
| E23 | 订阅列表分页 | useSubscriptionStore | 翻页 → API 调用参数正确 |
| E24 | 推送日志轮询 | useWebhookLogStore | 10s 定时刷新，切换筛选条件后停止 |
| E25 | 设备树加载 | useDeviceStore | fetchDeviceTree → 树结构正确，loading 状态变化 |
| E26 | 设备列表分页联动 | useDeviceStore | 选择树节点 → deviceList 按 groupId 过滤 |
| E27 | 通知未读计数 | useNotificationStore | fetchUnreadCount → badge 数字更新 |
| E28 | 通知偏好加载 | useNotificationStore | fetchPreferences → 偏好表单正确填充 |

### 10.3 Composable 测试（7 条）

| # | 用例 | Composable | 验证内容 |
|---|------|-----------|---------|
| E29 | useDraftRecovery | useDraftRecovery | 数据变化 300ms 后 → sessionStorage 更新 |
| E30 | useDraftRecovery — 清理 | useDraftRecovery | clearDraft → sessionStorage 清空 |
| E31 | useOffsetPagination | useOffsetPagination | 翻页 → 触发 fetchFn(page, size) |
| E32 | useCursorPagination | useCursorPagination | cursor 传递正确，翻页不丢失 |
| E33 | useRuleForm — 运算符过滤 | useRuleForm | float 类型返回 6 个运算符，bool 仅 eq |
| E34 | useRuleForm — API 转换 | useRuleForm | form → apiRule：priority "CRITICAL" → 2 |
| E35 | useCopyToClipboard | useCopyToClipboard | 调用 navigator.clipboard.writeText |

---

## 15. 前端 E2E 测试（6 条）

> 使用 Playwright，覆盖用户核心路径。

| # | 优先级 | 场景 | 覆盖的关键交互 |
|---|--------|------|---------------|
| P01 | P0 | 创建端点 → 测试推送 → 查看列表 | 端点全生命周期 + 密钥展示 + 配额展示 |
| P02 | P0 | 创建订阅三步流程 | Stepper 状态流转（选设备→设规则→确认）+ 草稿持久化 |
| P03 | P0 | 推送日志查看 → 筛选 → 详情 → 重试 | 列表筛选分页 + 日志详情 + 重试操作 |
| P04 | P1 | 密钥轮换完整流程 | 二次确认（输入端点名）→ 新密钥展示 → 旧密钥失效 |
| P05 | P1 | 通知接收 → 查看列表 → 标记已读 | 通知徽章 + 列表 + 标记已读 |
| P06 | P2 | 设备树展开 → 选中过滤 → 列表分页 | 树表联动 + 搜索 + 分页 |

---

## 16. 性能测试（4 条）

> 使用 k6，定期 nightly 执行，不进 CI 主流水线。

| # | 场景 | 工具 | 核心指标 | 基线目标 |
|---|------|------|---------|---------|
| PERF-01 | Kafka 消费吞吐 | k6 + Kafka plugin | messages/s | ≥ 1000 msg/s（ingestion-group） |
| PERF-02 | Webhook 推送延迟 | k6 + WireMock | P50 / P95 / P99 | P95 < 2s, P99 < 5s |
| PERF-03 | 100 端点并发推送 | k6 + 多 WireMock 实例 | 成功率 | ≥ 99.9%（含重试） |
| PERF-04 | 重试风暴（5 端点同时熔断恢复） | 自定义 k6 脚本 | CPU/内存 | 不超过 70%，无 OOM |

**执行频率**: 每周一次 nightly build + 版本发布前必须跑。

---

## 17. 端到端集成测试（3 条）

> 跨模块全链路验证。

| # | 用例 | 范围 |
|---|------|------|
| E2E-01 | 设备上报温度 85°C → 规则匹配 → Webhook 推送成功 → 日志记录 | 全链路正向 |
| E2E-02 | 设备上报温度 85°C → 推送失败 → 重试 → 最终成功 | 重试路径 |
| E2E-03 | 设备上报温度 85°C → 推送连续失败 5 次 → 熔断 → 通知 → 恢复 | 熔断+通知路径 |

---

## 18. v1.0 测试缺口（10 条）

> 评审补充：文档完善但实现层面容易遗漏的高风险场景。

| # | 遗漏场景 | 优先级 | 说明 |
|---|---------|--------|------|
| GAP-01 | 订阅规则删除后 Pipeline 中残留消息 | **P0** | 删除是异步生效，Pipeline 中残留消息应明确处理策略（丢弃/继续执行） |
| GAP-02 | MQTT 连接闪断重连期间的消息补偿 | **P0** | 断连 30s 重连后消息补偿、顺序和去重保证 |
| GAP-03 | 同一设备同时命中多条规则的优先级与去重 | **P0** | 同一设备数据命中多条规则推送同一 Endpoint 时是否去重 |
| GAP-04 | KeyValueEditor 模板注入攻击 | P1 | value 含表达式时后端模板引擎沙箱隔离 |
| GAP-05 | 批量设备订阅 RuleEditor 性能 | P1 | 1000+ 设备选择时前端组件渲染和后端规则编译 P99 |
| GAP-06 | 跨消费组消息顺序性保证 | P1 | v1.1 拆分后同一设备消息跨组有序性 |
| GAP-07 | Nacos 配置变更竞态条件 | P1 | 两人同时修改订阅配置，CAS/乐观锁测试 |
| GAP-08 | Endpoint 不可用时退避策略完整性 | P1 | 503 响应的指数退避 + 最大重试耗尽后死信处理 |
| GAP-09 | 大 Payload(>1MB) 处理链路 | P2 | Kafka 消息大小限制、反序列化性能、内存占用 |
| GAP-10 | 时区相关定时订阅规则 | P2 | 北京时间 8:00-20:00 规则在 UTC 服务器的边界转换 |

---

## 19. Step 1 重构保障测试（9 条）

> Pipeline 纯代码重构（拆类、线程池 4→2）的行为不变性验证。

### 19.1 重构前：Characterization Tests（建立基线）

| # | 测试内容 | 优先级 |
|---|---------|--------|
| REF-001 | SubscriptionMatcher 所有公开方法的输入输出快照 | P0 |
| REF-002 | 当前单一线程池在混合负载下的延迟分布/拒绝率快照 | P0 |
| REF-003 | 端到端消息流转 Golden Path 录制（设备→Kafka→匹配→Endpoint） | P0 |
| REF-004 | Endpoint 超时/失败场景的重试+降级行为快照 | P1 |

### 19.2 重构后：Snapshot Diff 验证

| # | 测试内容 | 优先级 |
|---|---------|--------|
| REF-005 | 拆分后 HealthyPool/DegradedPool 独立单元测试 | P0 |
| REF-006 | 拆类后各职责类单元测试（Router/Matcher/Dispatcher） | P0 |
| REF-007 | 端到端输出与基线快照 JSON diff 对比（零差异） | P0 |

### 19.3 性能基线对比

| # | 测试内容 | 优先级 |
|---|---------|--------|
| REF-008 | 重构前后 P50/P95/P99 延迟对比（允许 ±5%） | P1 |
| REF-009 | 重构前后内存占用和 GC 行为对比 | P2 |

---

## 20. Step 2 消费组拆分集成测试（13 条）

> Testcontainers (KRaft) + WireMock，验证消费组拆分后的核心行为。

### 20.1 消费组隔离性（5 条）

| # | 测试内容 | 优先级 |
|---|---------|--------|
| CGT-001 | 消费组 A/B 各自消费对应 Topic，消息无串漏 | P0 |
| CGT-002 | 消费组 A 的 broker 宕机，消费组 B 不受影响 | P0 |
| CGT-003 | 新增 Consumer 实例触发 Rebalance，消息不丢不重 | P0 |
| CGT-004 | 消息处理完成后 Offset 正确提交，重启从上次位置继续 | P0 |
| CGT-005 | 跨消费组的同一设备消息有序性验证 | P1 |

### 20.2 两级线程池（5 条）

| # | 测试内容 | 优先级 |
|---|---------|--------|
| TPT-001 | 所有 Endpoint 健康 → 全部走 HealthyPool，延迟符合预期 | P0 |
| TPT-002 | Endpoint 连续失败 → 自动降级到 DegradedPool | P0 |
| TPT-003 | DegradedPool 中 Endpoint 恢复 → 自动升级回 HealthyPool | P0 |
| TPT-004 | 大量 Endpoint 降级导致 DegradedPool 饱和 → 触发拒绝策略 | P1 |
| TPT-005 | 两个线程池指标（activeThreads/queueSize/completedTasks）正确暴露 | P2 |

### 20.3 扩展接口（3 条）

| # | 测试内容 | 优先级 |
|---|---------|--------|
| EXT-001 | SubscriptionQueryService 默认实现正确 + SPI 自定义实现可加载 | P1 |
| EXT-002 | DispatchClient 默认 HTTP 实现超时/重试处理 + SPI 可替换 | P1 |
| EXT-003 | EndpointResolver 缓存过期后重新拉取 + 不可用时本地缓存降级 | P0 |

---

## 21. 灰度验证矩阵（11 条）

> v1.1 Pipeline 拆分灰度阶段验证，每阶段通过后方可推进。

| # | 阶段 | 验证内容 | 通过标准 | 优先级 |
|---|------|---------|---------|--------|
| GRAY-001 | 5% | 新 Pipeline 消息处理成功率 vs 旧 Pipeline | 差异 < 0.1% | P0 |
| GRAY-002 | 5% | 新 Pipeline 消息延迟 P99 vs 旧 Pipeline | 差异 < 10ms | P0 |
| GRAY-003 | 5% | 新 Pipeline 错误日志分类，无新增 ERROR | 错误率 < 0.01% | P0 |
| GRAY-004 | 5% | endpointId hash % 100 落在 0-5 的设备走新 Pipeline | 100% 命中 | P0 |
| GRAY-005 | 20% | 重复 GRAY-001~004 全部检查 | 同上 | P0 |
| GRAY-006 | 20% | Consumer Group Rebalance 频率 | < 3 次/h | P1 |
| GRAY-007 | 20% | 两级线程池利用率，无死锁/线程泄漏 | activeThreads < 80% max | P1 |
| GRAY-008 | 50% | 峰值压测（日均 2 倍流量），新 Pipeline 不崩溃 | 无 OOM/无消息丢失 | P0 |
| GRAY-009 | 50% | 新旧 Pipeline 并行运行消息一致性抽查（100 条） | 一致率 100% | P0 |
| GRAY-010 | 100% | 旧 Pipeline 完全下线后全量监控 | 30min 无异常 | P0 |
| GRAY-011 | 100% | 旧 Pipeline 资源释放（Consumer/线程池/连接池） | 资源归零 | P1 |

**灰度推进前置条件**：
- 5% → 20%：GRAY-001~004 PASS + 观察 24h 无异常
- 20% → 50%：GRAY-001~007 PASS + 观察 48h 无异常
- 50% → 100%：GRAY-001~009 PASS + 压测通过 + 观察 72h 无异常

---

## 22. 回滚测试（6 条）

> 验证 ADR-025 回滚策略的正确性。

| # | 测试场景 | 期望结果 | 优先级 |
|---|---------|---------|--------|
| ROLL-001 | 灰度 50% 时回滚到 0%（Nacos 配置切换） | 3 分钟内所有消息回到旧 Pipeline，新 Consumer 停止 | P0 |
| ROLL-002 | 回滚切换瞬间的消息完整性 | 零丢失，重复率 < 1% | P0 |
| ROLL-003 | Nacos 不可用时回滚 | 本地缓存配置继续生效，Nacos 恢复后正确推送 | P0 |
| ROLL-004 | 部分节点回滚不一致（网络分区） | 消息不丢，短暂路由到错误 Pipeline 后自动恢复 | P1 |
| ROLL-005 | 回滚后新 Pipeline 资源清理 | 线程/连接/Consumer Group 正确释放，无泄漏 | P1 |
| ROLL-006 | 反复切换稳定性（0%→50%→0%→100%→0%） | 每次切换后系统稳定，无内存泄漏 | P1 |

**Staging 演练方案**：每两周一次，灰度推进（20min）+ 故障注入回滚（15min）+ 消息完整性校验（10min）。

---

## 23. 兼容性测试矩阵（14 条）

### 23.1 客户端兼容性（6 条）

| # | 组合 | 测试内容 | 优先级 |
|---|------|---------|--------|
| COMPAT-001 | v1.0 前端 + v1.0 后端 | 基线：所有现有功能正常 | P0 |
| COMPAT-002 | v1.0 前端 + v1.1 Step1 后端 | 前端无感知后端重构，API 响应格式不变 | P0 |
| COMPAT-003 | v1.0 前端 + v1.1 Step2 后端 | 消费组拆分对前端透明 | P0 |
| COMPAT-004 | v1.1 前端 + v1.0 后端 | 新前端在旧后端上的降级表现 | P1 |
| COMPAT-005 | v1.1 前端 + v1.1 Step1 后端 | 新旧前端功能与重构后后端完整交互 | P0 |
| COMPAT-006 | v1.1 前端 + v1.1 Step2 后端 | 完整功能验证 | P0 |

### 23.2 数据兼容性（4 条）

| # | 组合 | 测试内容 | 优先级 |
|---|------|---------|--------|
| COMPAT-010 | v1.0 创建的规则 + v1.1 Step1 引擎 | 已有规则在新引擎上行为一致 | P0 |
| COMPAT-011 | v1.0 创建的规则 + v1.1 Step2 消费组 | 已有规则在拆分后正确路由 | P0 |
| COMPAT-012 | v1.1 Step1 创建的规则 + v1.0 引擎（回滚） | 新引擎规则回滚到旧引擎可正确解析 | P0 |
| COMPAT-013 | v1.1 新增字段 + v1.0 数据库 schema | 新字段在旧 schema 上的兼容处理 | P1 |

### 23.3 协议兼容性（4 条）

| # | 组合 | 测试内容 | 优先级 |
|---|------|---------|--------|
| COMPAT-020 | HTTP API v1 + v1.1 后端 | REST API 向后兼容，无 Breaking Change | P0 |
| COMPAT-021 | Webhook 签名 v1 + v1.1 后端 | 签名算法不变，验签通过 | P0 |
| COMPAT-022 | Kafka Consumer v1.0 + v1.1 Broker | 消费端协议兼容 | P1 |
| COMPAT-023 | SSE 连接 + v1.1 后端 | 健康面板 SSE 流正确推送 | P1 |

---

## 24. 用例统计

| 分类 | 数量 | 占比 |
|------|------|------|
| 数据接入边界条件 | 8 | 3% |
| Kafka 集成测试 | 26 | 10% |
| Lua 匹配引擎 | 8 | 3% |
| at-least-once 关键路径 | 4 | 1% |
| 失败场景测试 | 15 | 5% |
| Semaphore + CircuitBreaker | 13 | 5% |
| API 端点测试 | 42 | 15% |
| 安全测试 | 14 | 5% |
| 事务回滚测试 | 6 | 2% |
| StormGuard 降级测试 | 4 | 1% |
| 订阅缓存一致性测试 | 5 | 2% |
| 数据类型转换测试 | 6 | 2% |
| 前端组件/Store/Composable | 35 | 13% |
| 前端 E2E | 6 | 2% |
| 性能测试 | 4 | 1% |
| 端到端集成 | 3 | 1% |
| Staging 容灾 | 4 | 1% |
| v1.0 测试缺口 | 10 | 4% |
| Step 1 重构保障 | 9 | 3% |
| Step 2 消费组集成 | 13 | 5% |
| 灰度验证矩阵 | 11 | 4% |
| 回滚测试 | 6 | 2% |
| 兼容性测试矩阵 | 14 | 5% |
| **总计** | **~274** | 100% |

---

## 25. CI/CD Pipeline 定义

### 25.1 Pipeline 阶段

```
[Lint] → [Unit Test] → [Integration Test] → [API Test] → [Frontend Test] → [E2E Test]
  30s      < 2min          < 8min            < 3min        < 3min         < 5min
                                                                              ↓
                                                                    [Nightly: Perf Test]
```

### 25.2 各阶段配置

| 阶段 | 触发 | 工具 | 超时 | 失败策略 |
|------|------|------|------|---------|
| Lint | 每次 PR | Checkstyle / ESLint | 30s | 阻断 |
| Unit Test | 每次 PR | Maven Surefire | 2min | 阻断 |
| Integration Test | 每次 PR（合并后） | Maven Failsafe + Testcontainers | 8min | 阻断 |
| API Test | 每次 PR | MockMvc | 3min | 阻断 |
| Frontend Test | 每次 PR | Vitest | 3min | 阻断 |
| E2E Test | 每次 PR（合并后） | Playwright | 5min | 阻断 |
| Perf Test | 每周 nightly + 发版前 | k6 | 15min | 告警（不阻断） |

### 25.3 覆盖率门禁

- **JaCoCo** 生成覆盖率报告，上传到 CI
- PR 合并门槛：行覆盖率 ≥ 80%
- 新增代码覆盖率 ≥ 90%（diff coverage check）
- 前端 Vitest 覆盖率 ≥ 70%（核心组件）

### 25.4 资源要求

- **CI Runner 最低 12G 内存**（Kafka KRaft ~2G + MySQL ~800M + Redis ~100M + App ~1G + Build JVM ~1.5G）
- Testcontainers reuse 开启（`testcontainers.reuse.enable=true`），本地开发复用容器
- 集成测试独占 runner，不与其他 job 并行（避免容器端口冲突）

---

## 26. 测试数据策略

### 26.1 按需加载（非全局预置）

每个测试方法通过 `@Sql` 注解按需加载最小数据集：

```java
@Test
@Sql("/test-data/single-device-with-threshold-rule.sql")
void shouldTriggerAlertWhenThresholdExceeded() { ... }
```

### 26.2 Test Data Builder 模式

```java
DeviceMessageBuilder.tempMessage()
    .deviceId(10086L)
    .temperature(85.0)
    .build();
```

### 20.3 基础数据集

仅用于 E2E 测试和开发环境初始化：

- 3 个产品模板（温湿度传感器 / 烟雾探测器 / 门磁传感器）
- 10 个设备实例（非 50 个）
- 3 个 Webhook 端点
- 5 条订阅规则

---

## 27. 测试环境分层

### 27.1 环境矩阵

| 环境 | 用途 | Kafka | Redis | 测试类型 |
|------|------|-------|-------|---------|
| CI（Testcontainers） | 自动化测试 | 单节点 KRaft | 单节点 | 单元 + 集成 + API + 前端 |
| Staging | 手动/探索性 + 基础设施容灾 | 3 节点 KRaft | 主从 Sentinel | E2E + 多 Broker 容灾 + Sentinel 故障转移 |
| Nightly | 性能基线 + 压力测试 | 3 节点 KRaft | 主从 Sentinel | 性能基线 + 长时间稳定性 |

### 27.2 Staging 容灾测试场景（4 条）

> 手动执行，验证生产级基础设施容灾能力。

| # | 场景 | 操作步骤 | 验证要点 |
|---|------|---------|---------|
| STG-01 | Kafka Broker 宕机恢复 | ① `docker exec kafka-2 kill -9 1` ② 等待 30s ③ 验证消费继续 ④ `docker restart kafka-2` ⑤ 等待 ISR 同步完成 | 消费不中断（RF=3），重启后 ISR 恢复，无数据丢失 |
| STG-02 | Redis Sentinel 故障转移 | ① `docker exec redis-master redis-cli DEBUG SLEEP 60` ② 观察 Sentinel 日志 ③ 等待故障转移完成 ④ 验证新 Master 写入 | ≤30s 完成转移，应用自动重连，推送限流无中断 |
| STG-03 | 多实例部署并发安全 | ① 部署 2 个 App 实例 ② 并发发送 100 条设备消息 ③ 观察 delivery_log | 无重复推送（幂等保护），Semaphore 隔离正常，分布式锁竞争正确 |
| STG-04 | Topic 分区运维 | ① `kafka-topics --alter --partitions 6` ② 观察消费者 rebalance ③ 验证新分区数据分布 | rebalance 期间无消息丢失，新分区正常读写，消费者 lag 不异常增长 |

**Staging 前置检查**:
```bash
# 1. 确认 3 节点 Kafka 集群健康
for i in 1 2 3; do
  docker exec kafka-$i kafka-broker-api-versions --bootstrap-server localhost:9092 | head -1
done

# 2. 确认 Redis Sentinel 集群状态
docker exec redis-sentinel-1 redis-cli -p 26379 SENTINEL master mymaster

# 3. 确认 App 实例注册
curl -s http://app-1:8080/actuator/health | jq .status
curl -s http://app-2:8080/actuator/health | jq .status
```

### 27.3 Nightly 性能测试场景（4 条）

> 使用 k6 脚本，每周执行 + 发版前必须通过。

| # | 场景 | 工具 | 核心指标 | 基线目标 |
|---|------|------|---------|---------|
| PERF-01 | Kafka 消费吞吐（持续 30 分钟） | k6 + Kafka plugin | messages/s | ≥ 1000 msg/s（ingestion-group），CPU < 70% |
| PERF-02 | Webhook 推送延迟 | k6 + WireMock | P50 / P95 / P99 | P95 < 2s, P99 < 5s |
| PERF-03 | 100 端点并发推送 | k6 + 多 WireMock 实例 | 成功率 | ≥ 99.9%（含重试） |
| PERF-04 | 重试风暴（5 端点同时熔断恢复） | 自定义 k6 脚本 | CPU / 内存 | CPU < 70%，无 OOM |

**k6 脚本框架**:

```javascript
// PERF-01: Kafka 消费吞吐
import { Kafka } from 'k6/x/kafka';
import { Trend } from 'k6/metrics';

const writeDuration = new Trend('write_duration');
const kafka = new Kafka({
  brokers: ['kafka-1:9092', 'kafka-2:9092', 'kafka-3:9092'],
  topic: 'device.raw',
});

export const options = {
  scenarios: {
    sustained: {
      executor: 'constant-arrival-rate',
      rate: 1000,            // 1000 msg/s
      timeUnit: '1s',
      duration: '30m',
      preAllocatedVUs: 50,
    },
  },
  thresholds: {
    kafka_writer_error_rate: ['rate<0.001'],  // 错误率 < 0.1%
    write_duration: ['p(95)<100'],             // 写入 P95 < 100ms
  },
};

export default function () {
  const start = Date.now();
  kafka.produce({
    messages: [{
      key: `device-${__VU}-${__ITER}`,
      value: JSON.stringify({
        deviceId: 10000 + (__VU % 100),
        productId: 100,
        ts: Date.now(),
        dataPoints: { temperature: 25 + Math.random() * 60 },
      }),
    }],
  });
  writeDuration.add(Date.now() - start);
}
```

```javascript
// PERF-02: Webhook 推送延迟
import http from 'k6/http';
import { Trend, Rate } from 'k6/metrics';

const pushLatency = new Trend('push_latency');
const pushSuccess = new Rate('push_success');

export const options = {
  scenarios: {
    burst: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      stages: [
        { duration: '2m', target: 200 },
        { duration: '5m', target: 200 },
        { duration: '2m', target: 50 },
      ],
      preAllocatedVUs: 100,
    },
  },
  thresholds: {
    push_latency: ['p(95)<2000', 'p(99)<5000'],
    push_success: ['rate>0.999'],
  },
};

export default function () {
  // 触发推送（通过 API 创建匹配规则的数据）
  const start = Date.now();
  const res = http.post('http://app:8080/api/v1/test/trigger-push', JSON.stringify({
    deviceId: 10086,
    temperature: 85.0,
  }), { headers: { 'Content-Type': 'application/json' } });
  pushLatency.add(Date.now() - start);
  pushSuccess.add(res.status === 200);
}
```

```javascript
// PERF-04: 重试风暴
import http from 'k6/http';
import { Gauge } from 'k6/metrics';

const cpuUsage = new Gauge('app_cpu_percent');
const memUsage = new Gauge('app_memory_mb');

export const options = {
  scenarios: {
    retry_storm: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '10m',
      preAllocatedVUs: 50,
    },
  },
};

export default function () {
  // 模拟 5 个端点从熔断恢复后的重试风暴
  for (let ep = 1; ep <= 5; ep++) {
    http.post(`http://app:8080/api/v1/test/simulate-recovery`,
      JSON.stringify({ endpointId: `ep-storm-${ep}` }),
      { headers: { 'Content-Type': 'application/json' } });
  }
  // 采集指标
  const metrics = http.get('http://app:8080/actuator/metrics');
  // 解析并上报 CPU/内存
}
```

**执行频率**: 每周一次 nightly build + 版本发布前必须跑。
