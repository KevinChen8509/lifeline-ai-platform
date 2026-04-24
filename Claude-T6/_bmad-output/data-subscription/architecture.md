# IoT 设备数据订阅功能 — 技术架构设计文档

> 版本: v3.0 | 日期: 2026-04-23 | 状态: 评审中
> v2.0 变更: 推送架构从 WebSocket 站内推送重构为 Webhook HTTP POST 第三方对接
> v2.1 变更: 修复重试调度多实例竞争、WebClient .block() 阻塞、SSRF 增强、密钥编码、ADR 补充
> v3.0 变更: 消息中间件从 RabbitMQ 迁移至 Kafka（项目已有 Kafka 基础设施），保留 DB 驱动重试方案

---

## 1. 技术栈

| 层次 | 技术选型 |
|------|---------|
| 后端 | Java Spring Boot 3 + JPA + Spring Kafka |
| 前端 | Vue 3 + TypeScript + Pinia + Element Plus |
| 数据库 | MySQL（关系数据） + Redis（缓存/热数据） |
| 消息队列 | Apache Kafka（KRaft 模式，复用项目已有基础设施） |
| HTTP 推送 | RestTemplate + @Async 独立线程池 + Semaphore 隔离 |
| 密钥存储 | AES-256-GCM 加密，主密钥来自 Nacos/Vault |
| 容器化 | Docker Compose |
| 监控 | Prometheus + Grafana |

---

## 2. 数据模型设计

### 2.1 实体关系

```
Product (产品模板) ──1:N──▶ Device (设备实例)
    │                         │
    │ defines                  │ has
    ▼                          ▼
DataPoint (数据点定义)    DeviceDataPoint (数据点实例)
                              │
                              │ last_value
                              ▼
                          TimeSeries (时序数据, Redis)

DeviceGroup (设备分组) ──M:N──▶ Device
    via DeviceGroupMember

WebhookEndpoint (推送端点) ──1:N──▶ WebhookSubscription (订阅)
                                        │
                                        │ triggers
                                        ▼
                                  WebhookDeliveryLog (推送日志)
```

### 2.2 Product（产品模板）

一个产品对应一类设备，定义物模型（数据点 Schema）。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| tenant_id | BIGINT | 租户 ID |
| product_key | VARCHAR(64) UK | 产品唯一标识，用于 MQTT Topic |
| name | VARCHAR(128) | 产品名称 |
| protocol | ENUM | MQTT / CoAP / HTTP / TCP |
| data_model | JSON | 物模型定义 |
| status | TINYINT | 0=开发中 1=已发布 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**物模型 JSON 结构**：

```json
{
  "properties": [
    {
      "identifier": "temperature",
      "name": "温度",
      "dataType": "float",
      "unit": "°C",
      "accessMode": "r",
      "range": { "min": -40, "max": 125 }
    }
  ],
  "events": [
    {
      "identifier": "temp_alarm",
      "name": "温度告警",
      "type": "alert",
      "outputData": ["temperature"]
    }
  ]
}
```

### 2.3 Device（设备实例）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| tenant_id | BIGINT | 租户 ID |
| product_id | BIGINT FK | 关联产品 |
| device_name | VARCHAR(128) | 设备名称 |
| device_key | VARCHAR(64) UK | 设备唯一标识 |
| status | TINYINT | 0=未激活 1=在线 2=离线 3=禁用 |
| last_active_at | DATETIME | 最后活跃时间 |
| tags | JSON | 设备标签 |
| created_at / updated_at | DATETIME | 时间戳 |

**索引**：`idx_product_id`, `idx_status`, `idx_tenant_status`

### 2.4 DeviceDataPoint（设备数据点实例）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| device_id | BIGINT FK | 关联设备 |
| identifier | VARCHAR(64) | 数据点标识（来自物模型） |
| data_type | VARCHAR(16) | float / int / string / bool / json |
| last_value | VARCHAR(256) | 最新值（字符串化） |
| last_report_at | DATETIME | 最新上报时间 |
| quality | TINYINT | 0=好 1=差 2=未知 |

**索引**：`UNIQUE(device_id, identifier)`

### 2.5 DeviceGroup / DeviceGroupMember（设备分组）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| tenant_id | BIGINT | 租户 ID |
| name | VARCHAR(128) | 分组名称 |
| group_type | TINYINT | 0=静态分组 1=动态分组 |
| created_at / updated_at | DATETIME | 时间戳 |

关联表：`UNIQUE(group_id, device_id)`

### 2.6 WebhookEndpoint（Webhook 推送端点）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| tenant_id | BIGINT | 租户 ID |
| user_id | BIGINT | 创建者 |
| name | VARCHAR(128) | 端点名称（如"企业微信群机器人"） |
| url | VARCHAR(512) | 接收 URL（HTTPS） |
| secret_encrypted | VARCHAR(512) | AES-256-GCM 加密后的签名密钥 |
| secret_iv | VARCHAR(64) | AES 初始化向量 |
| custom_headers | JSON | 自定义 HTTP 请求头 |
| status | TINYINT | 0=启用 1=禁用 |
| consecutive_failures | INT | 连续失败计数 |
| last_push_at | DATETIME | 最后推送时间 |
| last_success_at | DATETIME | 最后成功时间 |
| created_at / updated_at | DATETIME | 时间戳 |

**索引**：`idx_tenant_user`, `idx_status`

### 2.7 WebhookSubscription（Webhook 订阅）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| tenant_id | BIGINT | 租户 ID |
| user_id | BIGINT | 订阅用户 |
| endpoint_id | BIGINT FK | 关联 WebhookEndpoint |
| name | VARCHAR(128) | 订阅名称 |
| subscription_type | TINYINT | 0=设备级 1=设备类型级 2=分组级 |
| target_id | BIGINT | 设备 ID / 产品 ID / 分组 ID |
| data_point_ids | JSON | 数据点 ID 列表（null = 所有点） |
| status | TINYINT | 0=启用 1=暂停 2=已删除 |
| max_retries | INT | 最大重试次数，默认 5 |
| retry_interval_seconds | INT | 首次重试间隔（秒），默认 10 |
| created_at / updated_at | DATETIME | 时间戳 |

**索引**：`idx_user_status`, `idx_target_type`, `idx_tenant_user`, `idx_endpoint`

### 2.8 SubscriptionRule（触发规则）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| subscription_id | BIGINT FK | 关联订阅 |
| rule_type | TINYINT | 0=阈值 1=变化率(V1.1) 2=离线检测 |
| condition_json | JSON | 规则条件 |
| cooldown_seconds | INT | 冷却时间（秒），默认 300 |
| priority | TINYINT | 0=Info 1=Warning 2=Critical |
| enabled | BOOLEAN | 是否启用 |

**condition JSON 示例**：

```json
// 阈值规则
{ "operator": "gt", "threshold": 80, "duration": 60 }
// 离线检测
{ "type": "offline", "timeout": 600 }
```

### 2.9 WebhookDeliveryLog（推送日志）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| config_id | BIGINT | 关联 WebhookEndpoint |
| subscription_id | BIGINT | 关联订阅 |
| rule_id | BIGINT | 关联规则 |
| event | VARCHAR(64) | 事件类型（alert.triggered / device.offline 等） |
| event_id | VARCHAR(64) | 事件唯一 ID（用于去重） |
| device_id | BIGINT | 触发设备 |
| payload | TEXT | 推送 JSON |
| status | VARCHAR(16) | PENDING / SUCCESS / FAILED / RETRYING / DEAD |
| attempt_count | INT | 已重试次数 |
| max_retries | INT | 最大重试次数 |
| response_code | INT | HTTP 响应状态码 |
| response_body | VARCHAR(2048) | 响应体（截断） |
| error_msg | VARCHAR(1024) | 错误信息 |
| next_retry_at | DATETIME | 下次重试时间 |
| delivered_at | DATETIME | 成功投递时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**索引**：`idx_status_retry(status, next_retry_at)`, `idx_config_created(config_id, created_at)`, `idx_event_id(event_id)`, `idx_subscription_created(subscription_id, created_at)`

**分区策略**：按 `created_at` 月分区，保留 3 个月在线数据，历史归档。

### 2.10 Notification（站内通知）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT | 通知接收用户 |
| type | VARCHAR(32) | 通知类型（ENDPOINT_FAILURE / ENDPOINT_RECOVERED / SUBSCRIPTION_CANCELLED） |
| title | VARCHAR(256) | 通知标题 |
| content | TEXT | 通知内容 |
| related_id | BIGINT | 关联资源 ID（端点 ID / 订阅 ID） |
| related_type | VARCHAR(32) | 关联资源类型（ENDPOINT / SUBSCRIPTION） |
| is_read | BOOLEAN | 是否已读 |
| created_at | DATETIME | 创建时间 |

**索引**：`idx_user_read(user_id, is_read)`, `idx_user_created(user_id, created_at)`

### 2.11 NotificationPreference（通知偏好）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| user_id | BIGINT UK | 用户 ID |
| endpoint_failure_enabled | BOOLEAN | 端点失败通知开关，默认 true |
| endpoint_recovered_enabled | BOOLEAN | 端点恢复通知开关，默认 true |
| failure_frequency | VARCHAR(16) | 通知频率：EACH / DAILY_SUMMARY，默认 EACH |
| quiet_hours_start | TIME | 免打扰开始时间 |
| quiet_hours_end | TIME | 免打扰结束时间 |

---

## 3. RESTful API 设计

### 3.1 Webhook 端点管理

```
POST   /api/v1/webhook-endpoints                    # 创建端点
GET    /api/v1/webhook-endpoints                    # 端点列表
GET    /api/v1/webhook-endpoints/{id}               # 端点详情
PUT    /api/v1/webhook-endpoints/{id}               # 更新端点
DELETE /api/v1/webhook-endpoints/{id}               # 删除端点
POST   /api/v1/webhook-endpoints/{id}/test          # 发送测试推送
GET    /api/v1/webhook-endpoints/{id}/health        # 端点健康状态
PATCH  /api/v1/webhook-endpoints/{id}/rotate-secret # 轮换签名密钥
```

### 3.2 设备管理（只读）

```
GET    /api/v1/devices                              # 设备列表
GET    /api/v1/devices/{id}                         # 设备详情
GET    /api/v1/devices/{id}/datapoints              # 设备数据点
GET    /api/v1/device-groups/tree                   # 设备分组树
```

### 3.3 订阅管理

```
POST   /api/v1/subscriptions                        # 创建订阅
GET    /api/v1/subscriptions                        # 我的订阅列表
GET    /api/v1/subscriptions/{id}                   # 订阅详情
PUT    /api/v1/subscriptions/{id}                   # 更新订阅
DELETE /api/v1/subscriptions/{id}                   # 删除订阅
PATCH  /api/v1/subscriptions/{id}/status            # 启用/暂停
POST   /api/v1/subscriptions/{id}/rules             # 添加规则
PUT    /api/v1/subscriptions/{id}/rules/{ruleId}    # 更新规则
DELETE /api/v1/subscriptions/{id}/rules/{ruleId}    # 删除规则
```

### 3.4 推送日志

```
GET    /api/v1/webhook-logs                         # 推送日志列表
GET    /api/v1/webhook-logs/{id}                    # 日志详情（含请求/响应）
POST   /api/v1/webhook-logs/{id}/retry              # 手动重试
GET    /api/v1/webhook-logs/stats                   # 推送统计（成功率/延迟分布）
```

### 3.5 创建端点请求/响应示例

**POST /api/v1/webhook-endpoints**

```json
// Request
{
  "name": "企业微信群机器人",
  "url": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx",
  "customHeaders": {
    "Content-Type": "application/json"
  }
}

// Response 201
{
  "id": 1,
  "name": "企业微信群机器人",
  "url": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx",
  "secret": "whsec_a1b2c3d4e5f6...",   // 仅创建时返回，后续不可查
  "status": "ACTIVE",
  "createdAt": "2026-04-22T10:00:00Z"
}
```

### 3.6 通知管理 API

```
GET    /api/v1/notifications                          # 通知列表
GET    /api/v1/notifications/unread-count             # 未读数量
PATCH  /api/v1/notifications/{id}/read                # 标记已读
PATCH  /api/v1/notifications/read-all                 # 全部已读
GET    /api/v1/notification-preferences               # 获取通知偏好
PUT    /api/v1/notification-preferences               # 更新通知偏好
```

### 3.6 创建订阅请求/响应示例

**POST /api/v1/subscriptions**

```json
// Request
{
  "name": "车间温度监控",
  "endpointId": 1,
  "subscriptionType": "DEVICE",
  "targetId": 10086,
  "dataPointIds": null,
  "maxRetries": 5,
  "rules": [
    {
      "ruleType": "THRESHOLD",
      "condition": { "operator": "gt", "threshold": 80 },
      "cooldownSeconds": 300,
      "priority": 2
    }
  ]
}

// Response 201
{
  "id": 42,
  "name": "车间温度监控",
  "endpoint": { "id": 1, "name": "企业微信群机器人" },
  "subscriptionType": "DEVICE",
  "target": {
    "id": 10086,
    "deviceName": "温湿度传感器-A01",
    "productKey": "temp_sensor_v2"
  },
  "status": "ACTIVE",
  "rules": [
    {
      "id": 7,
      "ruleType": "THRESHOLD",
      "condition": { "operator": "gt", "threshold": 80 },
      "cooldownSeconds": 300,
      "priority": "CRITICAL"
    }
  ],
  "createdAt": "2026-04-22T10:00:00Z"
}
```

---

## 4. 数据流架构

### 4.1 端到端链路

```
IoT设备 ──MQTT/HTTP──▶ 协议接入层 ──▶ device.raw (Kafka) ──▶ DataIngestionService
                                                      │
                                        ┌─────────────┤
                                        │             │
                                        ▼             ▼
                                  更新DB+Redis    规则匹配(Redis)
                                                      │
                                                命中则发布到
                                                      │
                                                      ▼
                                              alert.event (Kafka)
                                                      │
                                                      ▼
                                              WebhookDispatcher
                                                      │
                                            ┌─────────┤
                                            │         │
                                     签名 + POST   记录日志
                                            │         │
                                            ▼         ▼
                                      第三方系统   DB(delivery_log)
                                            │
                                      ┌──────┤
                                      │      │
                                   200 OK  失败
                                      │      │
                                      ▼      ▼
                                   标记成功  重试调度
```

### 4.2 Kafka Topic 与消费组架构

```
段1: device.raw (Kafka Topic)
  partitions: 6, replication-factor: 3, retention: 72h
  key: deviceId（保证同一设备数据在同一分区有序）
  └─▶ Consumer Group: ingestion-group (2-4 实例)
        职责: 数据入库 + 规则匹配
        offset: manual_immediate

段2: alert.event (Kafka Topic)
  partitions: 12, replication-factor: 3, retention: 168h
  key: subscriptionId（保证同一订阅事件有序）
  └─▶ Consumer Group: dispatch-group (4-8 实例)
        职责: 签名 + HTTP POST + 日志
        offset: manual_immediate

段3: alert.event.dlt (Kafka Topic, Dead Letter)
  partitions: 6, replication-factor: 3, retention: 720h (30天)
  └─▶ Consumer Group: dlt-monitor-group (1 实例)
        职责: 死信入库 + 告警通知

  注: 重试采用 DB 轮询 + Redis 分布式锁方案（不使用 Kafka retry topics）。
  理由: Kafka 没有原生延迟消息，retry topics 本质是 hack，DB 方案已验证可靠。
```

### 4.3 详细步骤

**Step 1: 设备数据上报**

```
MQTT Topic: /product/{productKey}/device/{deviceKey}/data/post
Payload: { "id": "msg-123", "params": { "temperature": 25.6 }, "ts": 1713765600000 }
```

协议接入层通过 KafkaTemplate 发送到 `device.raw` Topic（key=deviceId，保证同设备有序）。

**Step 2: 协议接入层（DeviceGateway）**

设备数据通过协议接入层（MQTT/HTTP）接入，转换为统一 DeviceMessage 格式后，通过 KafkaTemplate 发送到 `device.raw` Topic（key=deviceId）。

**Step 3: DataIngestionService（段1 消费者）**

```java
@KafkaListener(topics = "device.raw", groupId = "ingestion-group")
public void onDeviceMessage(ConsumerRecord<String, DeviceMessage> record, Acknowledgment ack) {
    DeviceMessage msg = record.value();
    try {
        // 1. 物模型 Schema 校验
        // 2. 更新 DeviceDataPoint.last_value (MySQL)
        // 3. 写入时序数据 (Redis sorted set)
        // 4. 更新设备在线状态
        // 5. 规则匹配（Lua 脚本，Redis 单次 RTT）
        List<AlertEvent> hits = subscriptionEngine.match(msg);
        // 6. 推送疲劳控制过滤 + 发送到下游
        hits.stream()
            .filter(e -> !stormGuard.shouldSuppress(e))
            .forEach(e -> kafkaTemplate.send("alert.event", e.getSubscriptionId().toString(), e));
        // 7. 手动提交 offset
        ack.acknowledge();
    } catch (Exception e) {
        log.error("设备数据处理异常, deviceId={}", msg.getDeviceId(), e);
        throw e; // 触发 DefaultErrorHandler → DLT
    }
}
```

**Step 4: WebhookDispatcher（段2 消费者 — ADR-021 ack 时序修复）**

```java
@KafkaListener(topics = "alert.event", groupId = "dispatch-group")
public void dispatch(ConsumerRecord<String, AlertEvent> record, Acknowledgment ack) {
    AlertEvent event = record.value();
    try {
        WebhookEndpoint endpoint = endpointRepo.findById(event.getEndpointId())
            .orElseThrow(() -> new NotFoundException("端点不存在: " + event.getEndpointId()));
        String payload = buildPayload(event);
        long timestampSec = event.getTimestamp() / 1000;
        String signature = webhookSigner.sign(payload, timestampSec, endpoint.getSecret());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-ID", event.getEventId());
        headers.set("X-Webhook-Event", event.getEventType());
        headers.set("X-Webhook-Timestamp", String.valueOf(timestampSec));
        headers.set("X-Webhook-Signature", "sha256=" + signature);

        // 1. 先持久化 deliveryLog(PENDING)，确保落盘
        WebhookDeliveryLog deliveryLog = saveLog(event, payload, "PENDING");

        // 2. 提交 offset — 消息已持久化到 DB，不会丢
        ack.acknowledge();

        // 3. 异步推送（从 DB 状态驱动，不依赖内存 payload）
        webhookPushService.pushAsync(endpoint, headers, payload, deliveryLog);
    } catch (Exception e) {
        log.error("Webhook dispatch 异常, eventId={}", event.getEventId(), e);
        throw e; // 触发 DefaultErrorHandler → DLT
    }
}
```

**补偿任务（处理"已 ack 但推送未完成"的情况）**：

```java
@Scheduled(fixedRate = 30000)
public void compensatePendingDeliveries() {
    List<WebhookDeliveryLog> pending = logRepo
        .findByStatusAndCreatedAtBefore("PENDING", LocalDateTime.now().minusMinutes(2));
    for (WebhookDeliveryLog log : pending) {
        // 乐观锁：仅当 version 未变时更新
        int updated = logRepo.updateStatusWithVersion(
            log.getId(), "PENDING", "PUSHING", log.getVersion());
        if (updated == 0) continue; // 被其他实例抢走

        WebhookEndpoint endpoint = endpointRepo.findById(log.getConfigId()).orElse(null);
        if (endpoint == null) {
            log.setStatus("DEAD");
            log.setErrorMsg("endpoint deleted");
            logRepo.save(log);
            continue;
        }
        webhookPushService.pushAsync(endpoint, buildRetryHeaders(log), log.getPayload(), log);
    }
}
```

**WebhookPushService（异步推送 — ADR-020 SemaphoreGuard + 全面 catch）**：

```java
@Service
public class WebhookPushService {

    private final RestTemplate restTemplate;
    private final EndpointIsolationRegistry isolationRegistry;
    private static final int CONNECT_TIMEOUT = 3000;
    private static final int READ_TIMEOUT = 5000;

    public WebhookPushService(EndpointIsolationRegistry isolationRegistry) {
        this.isolationRegistry = isolationRegistry;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restTemplate = new RestTemplate(factory);
    }

    @Async("webhookPushExecutor")
    public void pushAsync(WebhookEndpoint endpoint, HttpHeaders headers,
                          String payload, WebhookDeliveryLog deliveryLog) {
        Long endpointId = endpoint.getId();

        // 第1关：熔断检查
        if (isolationRegistry.isCircuitOpen(endpointId)) {
            markFailed(deliveryLog, "circuit_open");
            scheduleRetry(deliveryLog);
            return;
        }

        // 第2关 + 第3关：SemaphoreGuard 封装推送
        try (SemaphoreGuard guard = new SemaphoreGuard(isolationRegistry.getSemaphore(endpointId))) {
            if (!guard.tryAcquire(2, TimeUnit.SECONDS)) {
                markFailed(deliveryLog, "semaphore_timeout");
                scheduleRetry(deliveryLog);
                return;
            }

            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = restTemplate.postForEntity(
                endpoint.getUrl(), entity, String.class);

            if (resp.getStatusCode().is2xxSuccessful()) {
                isolationRegistry.recordSuccess(endpointId);
                deliveryLog.setStatus("SUCCESS");
                deliveryLog.setResponseCode(resp.getStatusCode().value());
                deliveryLog.setDeliveredAt(LocalDateTime.now());
                endpointService.resetConsecutiveFailures(endpointId);
            } else {
                isolationRegistry.recordFailure(endpointId);
                deliveryLog.setStatus("RETRYING");
                deliveryLog.setResponseCode(resp.getStatusCode().value());
                deliveryLog.setResponseBody(truncate(resp.getBody(), 2048));
                scheduleRetry(deliveryLog);
            }
        } catch (HttpClientErrorException e) {
            // 4xx 不重试
            deliveryLog.setStatus("DEAD");
            deliveryLog.setResponseCode(e.getStatusCode().value());
            deliveryLog.setErrorMsg(truncate(e.getMessage(), 500));
        } catch (ResourceAccessException | HttpServerErrorException e) {
            isolationRegistry.recordFailure(endpointId);
            markFailed(deliveryLog, "push_error: " + e.getMessage());
            scheduleRetry(deliveryLog);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(deliveryLog, "interrupted");
            scheduleRetry(deliveryLog);
        } catch (Exception e) {
            markFailed(deliveryLog, "unexpected: " + e.getMessage());
            scheduleRetry(deliveryLog);
        } finally {
            // 保证日志持久化（SemaphoreGuard 已处理许可释放）
            try { logRepo.save(deliveryLog); }
            catch (DataAccessException e) { log.error("保存推送日志失败, logId={}", deliveryLog.getId(), e); }
        }
    }
}

// SemaphoreGuard（ADR-020）
public class SemaphoreGuard implements AutoCloseable {
    private final Semaphore semaphore;
    private boolean acquired = false;

    public SemaphoreGuard(Semaphore sem) { this.semaphore = sem; }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        acquired = semaphore.tryAcquire(timeout, unit);
        return acquired;
    }

    @Override
    public void close() {
        if (acquired) semaphore.release();
    }
}

// 线程池配置（ADR-018 自定义 RejectPolicy）
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("webhookPushExecutor")
    public Executor webhookPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("wh-push-");
        // 满载时降级到 DB 重试，不阻塞 Kafka 消费线程
        executor.setRejectedExecutionHandler((r, exec) -> {
            if (r instanceof WebhookPushTask task) {
                task.scheduleRetry();
                log.warn("推送队列满，转入重试: endpointId={}", task.getEndpointId());
            }
        });
        executor.initialize();
        return executor;
    }
}

// Endpoint 隔离注册表（事件驱动清理 + 定时兜底）
@Component
public class EndpointIsolationRegistry {
    private final ConcurrentHashMap<Long, Semaphore> semaphores = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CircuitState> circuits = new ConcurrentHashMap<>();
    private static final int DEFAULT_PERMITS = 5;
    private static final int FAILURE_THRESHOLD = 5;
    private static final Duration HALF_OPEN_INTERVAL = Duration.ofMinutes(1);

    // 端点删除事件驱动清理
    @EventListener
    public void onEndpointDeleted(EndpointDeletedEvent event) {
        semaphores.remove(event.getEndpointId());
        circuits.remove(event.getEndpointId());
        log.info("清理已删除端点的隔离状态, endpointId={}", event.getEndpointId());
    }

    // 定时兜底清理（每小时）
    @Scheduled(fixedRate = 3600_000)
    public void cleanupStaleEndpoints() {
        Set<Long> activeIds = endpointRepository.findAllActiveIds();
        semaphores.keySet().retainAll(activeIds);
        circuits.keySet().retainAll(activeIds);
    }

    public Semaphore getSemaphore(Long endpointId) {
        return semaphores.computeIfAbsent(endpointId, id -> new Semaphore(DEFAULT_PERMITS));
    }

    public boolean isCircuitOpen(Long endpointId) {
        CircuitState state = circuits.get(endpointId);
        if (state == null) return false;
        if (state.status == CircuitStatus.OPEN) {
            if (Instant.now().isAfter(state.openedAt.plus(HALF_OPEN_INTERVAL))) {
                state.status = CircuitStatus.HALF_OPEN;
                state.failureCount.set(0);
                return false;
            }
            return true;
        }
        return false;
    }

    public void recordSuccess(Long endpointId) {
        CircuitState state = circuits.get(endpointId);
        if (state != null && state.status == CircuitStatus.HALF_OPEN) {
            state.status = CircuitStatus.CLOSED;
            state.failureCount.set(0);
        }
    }

    public void recordFailure(Long endpointId) {
        CircuitState state = circuits.computeIfAbsent(endpointId, id -> new CircuitState());
        int count = state.failureCount.incrementAndGet();
        if (state.status == CircuitStatus.HALF_OPEN || count >= FAILURE_THRESHOLD) {
            state.status = CircuitStatus.OPEN;
            state.openedAt = Instant.now();
        }
    }
}
```
```

**Step 5: 重试调度（v2.1 Redis 锁方案）**

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookRetryScheduler {

    private final StringRedisTemplate redis;
    private final WebhookDeliveryLogRepository logRepo;
    private final WebhookPushService pushService;
    private final WebhookEndpointService endpointService;

    private static final String LOCK_KEY = "lock:webhook:retry-scan";
    private static final int LOCK_TTL_SECONDS = 30;
    private static final int PAGE_SIZE = 100;
    private static final long RETRY_WINDOW_MS = 2 * 3600 * 1000L;  // 2 小时总窗口

    @Scheduled(fixedRate = 5000)  // fixedRate：不受上次执行时长影响
    public void scanRetryableLogs() {
        // 1. Redis 分布式锁：同一时刻只有一个实例执行
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue()
            .setIfAbsent(LOCK_KEY, lockValue, Duration.ofSeconds(LOCK_TTL_SECONDS));
        if (!Boolean.TRUE.equals(acquired)) {
            return;  // 其他实例持有锁，跳过
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            int offset = 0;

            while (true) {
                // 2. 分页查询，避免全量拉取 OOM
                Page<WebhookDeliveryLog> page = logRepo.findByStatusAndNextRetryAtBefore(
                    "RETRYING", now, PageRequest.of(offset, PAGE_SIZE, Sort.by("id")));

                List<WebhookDeliveryLog> pending = page.getContent();
                if (pending.isEmpty()) break;

                for (WebhookDeliveryLog log : pending) {
                    processRetryLog(log);
                }

                if (!page.hasNext()) break;
                offset++;
            }
        } finally {
            // 3. 释放锁（Lua 脚本保证原子性，防止误删其他实例的锁）
            releaseLock(lockValue);
        }
    }

    private void processRetryLog(WebhookDeliveryLog log) {
        // 检查是否超过重试窗口（2小时）
        if (Duration.between(log.getCreatedAt(), LocalDateTime.now()).toMillis()
                > RETRY_WINDOW_MS) {
            log.setStatus("DEAD");
            log.setErrorMsg("超过重试窗口(2h)");
            logRepo.save(log);
            endpointService.incrementConsecutiveFailures(log.getConfigId());
            return;
        }

        if (log.getAttemptCount() >= log.getMaxRetries()) {
            log.setStatus("DEAD");
            logRepo.save(log);
            endpointService.incrementConsecutiveFailures(log.getConfigId());
            return;
        }

        // 推送成功时 resetConsecutiveFailures 由 PushService 处理
        pushService.pushAsync(
            endpointRepo.findById(log.getConfigId()).orElseThrow(),
            buildRetryHeaders(log),
            log.getPayload(),
            log
        );
    }

    private void scheduleRetry(WebhookDeliveryLog log) {
        // 指数退避 2x：30s → 60s → 120s → 240s → 480s，上限 30min
        long delay = (long) (30 * Math.pow(2, log.getAttemptCount()));
        delay = Math.min(delay, 1800);
        log.setNextRetryAt(LocalDateTime.now().plusSeconds(delay));
        log.setAttemptCount(log.getAttemptCount() + 1);
        log.setStatus("RETRYING");
        logRepo.save(log);
    }

    /** Lua 脚本安全释放锁 */
    private void releaseLock(String lockValue) {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] " +
                        "then return redis.call('del', KEYS[1]) else return 0 end";
        redis.execute(new DefaultRedisScript<>(script, Long.class),
            List.of(LOCK_KEY), lockValue);
    }
}
```

---

## 5. v1.0 新增后端服务

### 5.1 SubscriptionTestService（测试推送）

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionTestService {

    private final WebhookPushService pushService;
    private final WebhookEndpointRepository endpointRepo;
    private final StringRedisTemplate redis;

    private static final String RATE_LIMIT_PREFIX = "test:ratelimit:";
    private static final int MAX_PER_MINUTE = 3;

    /**
     * 发送测试推送（绕过 Kafka，直接推送）
     */
    public TestPushResult testEndpoint(Long endpointId, String customPayload) {
        // 1. 频率限制：每端点每分钟最多 3 次
        String key = RATE_LIMIT_PREFIX + endpointId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, 60, TimeUnit.SECONDS);
        }
        if (count != null && count > MAX_PER_MINUTE) {
            throw new RateLimitException("测试推送频率超限（每分钟最多 " + MAX_PER_MINUTE + " 次）");
        }

        // 2. 构建测试 payload
        WebhookEndpoint endpoint = endpointRepo.findById(endpointId)
            .orElseThrow(() -> new NotFoundException("端点不存在"));
        String payload = customPayload != null ? customPayload : buildDefaultTestPayload(endpoint);
        long timestampSec = Instant.now().getEpochSecond();

        // 3. 调用 PushService 直接推送（不经过 Kafka）
        return pushService.sendDirectly(endpoint, payload, timestampSec);
    }

    private String buildDefaultTestPayload(WebhookEndpoint endpoint) {
        return """
        {
          "event_id": "test_%s",
          "event_type": "test.push",
          "timestamp": "%s",
          "subscription": { "id": 0, "name": "测试推送" },
          "isTest": true,
          "message": "这是一条测试推送，来自端点「%s」"
        }
        """.formatted(UUID.randomUUID().toString().substring(0, 8),
                      Instant.now().toString(), endpoint.getName());
    }
}
```

### 5.2 SecretRotationService（密钥轮换）

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SecretRotationService {

    private final WebhookEndpointRepository endpointRepo;
    private final SecretEncryptor encryptor;
    private final AuditLogService auditLogService;

    private static final Duration GRACE_PERIOD = Duration.ofMinutes(5);

    /**
     * 轮换端点签名密钥
     * @return 新密钥明文（仅此一次返回）
     */
    public String rotateSecret(Long endpointId, Long userId) {
        WebhookEndpoint endpoint = endpointRepo.findById(endpointId)
            .orElseThrow(() -> new NotFoundException("端点不存在"));

        // 1. 保留旧密钥用于宽限期验签
        String oldSecretEncrypted = endpoint.getSecretEncrypted();
        String oldSecretIv = endpoint.getSecretIv();
        endpoint.setPreviousSecret(oldSecretEncrypted);
        endpoint.setPreviousSecretIv(oldSecretIv);
        endpoint.setGracePeriodEnd(LocalDateTime.now().plus(GRACE_PERIOD));

        // 2. 生成新密钥
        String newSecret = "whsec_" + generateSecureRandom(32);
        EncryptedSecret encrypted = encryptor.encrypt(newSecret);
        endpoint.setSecretEncrypted(encrypted.ciphertext());
        endpoint.setSecretIv(encrypted.iv());

        endpointRepo.save(endpoint);

        // 3. 审计日志
        auditLogService.log("SECRET_ROTATED", userId, "ENDPOINT", endpointId);

        log.info("密钥已轮换, endpointId={}, gracePeriodEnd={}", endpointId, endpoint.getGracePeriodEnd());
        return newSecret;
    }

    /**
     * 宽限期双密钥验签
     */
    public boolean verifyWithGracePeriod(WebhookEndpoint endpoint, String payload,
                                          long timestamp, String signature) {
        // 优先用新密钥验签
        String currentSecret = encryptor.decrypt(endpoint.getSecretEncrypted(), endpoint.getSecretIv());
        if (webhookSigner.verify(payload, timestamp, currentSecret, signature)) {
            return true;
        }
        // 宽限期内尝试旧密钥
        if (endpoint.getGracePeriodEnd() != null
                && LocalDateTime.now().isBefore(endpoint.getGracePeriodEnd())) {
            String oldSecret = encryptor.decrypt(endpoint.getPreviousSecret(), endpoint.getPreviousSecretIv());
            return webhookSigner.verify(payload, timestamp, oldSecret, signature);
        }
        return false;
    }

    @Scheduled(fixedRate = 60000)  // 每分钟清理过期宽限期
    public void cleanupGracePeriods() {
        List<WebhookEndpoint> expired = endpointRepo.findByGracePeriodEndBefore(LocalDateTime.now());
        expired.forEach(e -> {
            e.setPreviousSecret(null);
            e.setPreviousSecretIv(null);
            e.setGracePeriodEnd(null);
        });
        endpointRepo.saveAll(expired);
    }
}
```

### 5.3 EndpointHealthService（端点健康指标）

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EndpointHealthService {

    private final WebhookDeliveryLogRepository logRepo;
    private final EndpointIsolationRegistry isolationRegistry;
    private final StringRedisTemplate redis;

    private static final String CACHE_PREFIX = "endpoint:health:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    /**
     * 获取端点健康指标（Redis 缓存 1 分钟）
     */
    public EndpointHealth getHealth(Long endpointId) {
        String cacheKey = CACHE_PREFIX + endpointId;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return JsonUtils.fromJson(cached, EndpointHealth.class);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last1h = now.minusHours(1);
        LocalDateTime last24h = now.minusHours(24);

        // DB 聚合查询
        long success24h = logRepo.countByConfigIdAndStatusAndCreatedAtAfter(endpointId, "SUCCESS", last24h);
        long total24h = logRepo.countByConfigIdAndCreatedAtAfter(endpointId, last24h);
        long success1h = logRepo.countByConfigIdAndStatusAndCreatedAtAfter(endpointId, "SUCCESS", last1h);
        long total1h = logRepo.countByConfigIdAndCreatedAtAfter(endpointId, last1h);

        Double avgLatencyMs = logRepo.avgLatencyByConfigIdAndCreatedAtAfter(endpointId, last24h);

        // 熔断器状态
        EndpointIsolationRegistry.CircuitState circuitState = isolationRegistry.getCircuitState(endpointId);

        EndpointHealth health = new EndpointHealth(
            endpointId,
            total24h == 0 ? null : (double) success24h / total24h,
            total1h == 0 ? null : (double) success1h / total1h,
            avgLatencyMs,
            circuitState != null ? circuitState.status.name() : "CLOSED",
            circuitState != null ? circuitState.failureCount.get() : 0,
            now
        );

        // 缓存
        redis.opsForValue().set(cacheKey, JsonUtils.toJson(health), CACHE_TTL);
        return health;
    }
}

record EndpointHealth(
    Long endpointId,
    Double successRate24h,
    Double successRate1h,
    Double avgLatencyMs,
    String circuitBreakerStatus,
    int consecutiveFailures,
    LocalDateTime calculatedAt
) {}
```

### 5.4 CircuitBreakerEventListener（失败通知）

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerEventListener {

    private final NotificationService notificationService;
    private final WebhookEndpointRepository endpointRepo;

    /**
     * 监听熔断器状态变更，发送站内通知
     */
    @Async
    @EventListener
    public void onCircuitStateChange(CircuitStateChangeEvent event) {
        WebhookEndpoint endpoint = endpointRepo.findById(event.getEndpointId()).orElse(null);
        if (endpoint == null) return;

        if (event.getNewState() == CircuitStatus.OPEN) {
            // 端点熔断 → 发送失败通知
            notificationService.createNotification(
                endpoint.getUserId(),
                "ENDPOINT_FAILURE",
                "端点推送连续失败",
                "端点「" + endpoint.getName() + "」连续推送失败 " + event.getFailureCount()
                    + " 次，已触发熔断保护。请检查端点服务是否正常。",
                endpoint.getId(),
                "ENDPOINT"
            );
        } else if (event.getNewState() == CircuitStatus.CLOSED) {
            // 端点恢复 → 发送恢复通知
            notificationService.createNotification(
                endpoint.getUserId(),
                "ENDPOINT_RECOVERED",
                "端点推送已恢复",
                "端点「" + endpoint.getName() + "」推送已恢复正常。",
                endpoint.getId(),
                "ENDPOINT"
            );
        }
    }
}
```

---

## 6. v1.1 架构演进路线图

> **评审修订（v2.1）**：经 Party Mode 多角色评审，原方案做了以下调整：
> - Pipeline 从 3 段改为 2 段（ingestion+matching 进程内，dispatch 独立）
> - 线程池从 4 级简化为 2 级
> - 扩展接口从 5 个精简为 3 个（移除 MessageChannel、MatchingEngine）
> - Endpoint Sharding 降为 P2 优先级，v1.2 再实施
> - 新增 ADR-024（Pipeline 2 段拆分方案）和 ADR-025（灰度与回滚策略）

### 6.1 Pipeline 2 段拆分

v1.0 的 `DataIngestionService` 同时承担入库和匹配（God Service）。v1.1 采用 **2 段方案**（非 3 段）：

```
Stage 1 (Ingestion+Matching)          Stage 2 (Dispatch)
┌──────────────────────────────┐      ┌──────────────────────┐
│ ingestion-group              │      │ dispatch-group       │
│ consume device.raw           │      │ consume alert.event  │
│  1. parse payload (inline)   │      │  1. lookup endpoint  │
│  2. write TSDB               │─────→│  2. HTTP POST        │
│  3. match rules (进程内)      │      │  3. retry + DLQ      │
│  4. produce alert.event      │      │  4. circuit breaker  │
└──────────────────────────────┘      └──────────────────────┘
```

**选择 2 段而非 3 段的理由**：

| 维度 | 2 段（采纳） | 3 段（否决） |
|------|-------------|-------------|
| 阶段间延迟 | 进程内调用 <5ms | 多一次 Kafka 往返 +10-30ms |
| 资源互补 | 入库 IO-bound + 匹配 CPU-bound 互补 | 匹配纯 CPU，入库 CPU 闲置 |
| 运维复杂度 | 2 服务 + 2 Topic | 3 服务 + 3 Topic，监控点 +50% |
| 实现成本 | ~3 人日 | ~6 人日 |
| 变更频率 | 入库 schema 和匹配规则通常同步变更 | 独立变更收益低 |

**新增 Topic**：

| Topic | 分区 | RF | 压缩 | 消费组 | 说明 |
|-------|------|-----|------|--------|------|
| `dispatch.retry` | 6 | 2 | lz4 | retry-group | 重试事件（替代 DB 轮询） |
| `subscription.dead-letter` | 3 | 2 | snappy | — | 死信队列 |

**迁移策略**：分两步渐进式——Step 1 纯代码重构（不动消息拓扑），Step 2 消费组拆分（feature flag 灰度切换）。

### 6.2 两级线程池

v1.0 单一 `@Async` 线程池 → v1.1 按端点健康度分 **2 级**（非 4 级）：

| 级别 | 条件 | 线程池配置 | 拒绝策略 |
|------|------|-----------|---------|
| Healthy | CircuitBreaker CLOSED | core=8, max=16, queue=200 | WriteDbRejectionHandler |
| Degraded | CircuitBreaker OPEN/HALF_OPEN | core=2, max=4, queue=50 | WriteDbRejectionHandler |

**路由逻辑**：
```java
public ThreadPoolExecutor selectPool(CircuitBreaker.State state) {
    return state == CLOSED ? healthyPool : degradedPool;
}
```

**不采用 4 级的理由**：
- DeadLetter 不需要线程池——已放弃的消息只需写 DB 记录
- Recovered（HALF_OPEN）可归入 Degraded 池做试探，无需单独线程池
- 4 级管理复杂度：监控×4、调优×4、溢出处理×4

### 6.3 保留的扩展接口（3 个）

移除 `MessageChannel`（单实现，直接用 KafkaTemplate）和 `MatchingEngine`（单实现，直接用 MatchingService）。保留 3 个有实际替换场景的接口：

```java
/** 订阅规则查询 — 两级缓存：Caffeine → Redis → DB */
public interface SubscriptionQueryService {
    List<Subscription> findActiveSubscriptions(String deviceId, String dataType);
    void refreshCache();
}

/** 推送客户端 — HTTP/gRPC/Mock 可替换 */
public interface DispatchClient {
    DispatchResult notify(Endpoint endpoint, AlertRequest alert);
    boolean healthCheck(Endpoint endpoint);
}

/** 端点解析器 — DB/配置中心可替换 */
public interface EndpointResolver {
    Endpoint resolve(String endpointId);
    void refreshCache();
}
```

### 6.4 Endpoint Sharding（v1.2 规划）

> **优先级调整**：经 PM 评审，Endpoint Sharding 对用户无直接感知，降为 v1.2。需 v1.1 压测数据论证瓶颈后再投入。

v1.1 的 dispatch-group 不区分端点。v1.2 按端点 ID 哈希到不同分区：

```java
// 生产者：按 endpointId 分区
kafkaTemplate.send("alert.event", endpointId.toString(), alertEvent);
```

扩容 `alert.event` 从 12 分区到 32 分区（2 的幂，负载均衡更均匀）。配合 Sharding + 分区内二级 SemaphoreGuard 隔离。

### 6.5 alert.event 消息格式演进（v1.1）

v1.1 新增字段（向后兼容，v1.0 忽略未知属性）：

```json
{
  "eventId": "evt-uuid-001",
  "version": "v1.1",
  "timestamp": 1713849600123,
  "deviceId": "sensor-001",
  "subscriptionId": "sub-123",
  "matchedCondition": { "field": "temperature", "operator": ">", "threshold": 40.0, "actualValue": 42.5 },
  "endpointId": "ep-webhook-001",
  "metadata": { "ingestionLatencyMs": 4, "matchLatencyMs": 2 }
}
```

| 新增字段 | 类型 | 说明 |
|---------|------|------|
| `eventId` | UUID | delivery_log 关联 + 幂等 |
| `version` | string | 消息格式版本号 |
| `endpointId` | string | 目标 endpoint（避免 dispatch 回查 DB） |
| `metadata.*LatencyMs` | long | 各阶段延迟，SLA 监控 |

### 6.6 迁移分步计划

**Step 1：纯代码重构（不动运行时，~7 文件 500 行）**

| 任务 | 说明 |
|------|------|
| 移除 MessageChannel | 内联到 KafkaTemplate |
| 移除 MatchingEngine | 重命名为 MatchingService |
| 线程池 4→2 | parse 内联，match+publish 合并为 businessExecutor |
| alert.event 新增字段 | 向后兼容 |
| DDL 变更 | delivery_log 新列 + pipeline_config 表 |

验证：全量单元/集成测试通过 + alert.event 新字段可被 v1.0 Dispatcher 忽略。

**Step 2：消费组拆分（~8 新文件 770 行，feature flag 灰度）**

| 任务 | 说明 |
|------|------|
| Dispatcher 独立消费组 | group.id → dispatch-group |
| 独立部署配置 | 独立 Helm chart / Docker Compose |
| 重试迁移 | DB 轮询 → dispatch.retry Topic（双写 + feature flag） |
| 监控埋点 | 两个消费组独立 lag 指标 |
| 灰度配置接入 | endpointId hash % 100 路由 |

验证：端到端延迟 ≤ v1.0 基线 +10%，两个消费组可独立重启扩缩容。

### 6.7 delivery_log 表变更

```sql
ALTER TABLE delivery_log
    ADD COLUMN event_id VARCHAR(36),
    ADD COLUMN message_version VARCHAR(8),
    ADD COLUMN stage VARCHAR(20) DEFAULT 'dispatch',
    ADD COLUMN dispatch_group VARCHAR(64),
    ADD COLUMN latency_ms INT;

CREATE INDEX idx_delivery_log_event_id ON delivery_log(event_id);
```

新增灰度配置表：

```sql
CREATE TABLE pipeline_config (
    config_key VARCHAR(128) NOT NULL UNIQUE,
    config_val JSONB NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW(),
    updated_by VARCHAR(64)
);
```

### 6.8 部署拓扑（2 段方案）

```
Pod-A (Ingestion + Matching): 2 实例, 4C8Gi, JVM -Xms3g -Xmx4g
  consume device.raw → persist + match → produce alert.event

Pod-B (Dispatch): 2 实例, 2C4Gi, JVM -Xms1g -Xmx1500m
  consume alert.event → HTTP POST + retry + circuit breaker

对比 v1.0: 2×4C8G = 8C16G
v1.1 新增: 2×2C4G = 4C8G（dispatch 独立部署）
月度新增成本: ~¥600/月
```

**2 段 vs 原 3 段方案资源对比**：节省 25% 计算资源（减少一组应用实例 + 一次跨进程序列化）。

### 6.9 ADR-016: Pipeline 拆分策略（v1.1 修订）

**背景**: v1.0 的 `ingestion-group` 同时做入库和匹配，无法独立扩缩。原方案为 3 段拆分。**决策（修订）**: 改为 **2 段拆分**——`ingestion-group`（入库+匹配进程内）+ `dispatch-group`（独立消费组）。理由：入库 IO-bound + 匹配 CPU-bound 在同一进程内天然互补；3 段多一次 Kafka 往返增加延迟且浪费资源。详见 ADR-024。

### 6.10 ADR-017: Endpoint Sharding 策略（降级为 v1.2）

**背景**: v1.0 所有端点共享同一消费组，慢端点拖慢整体消费进度。**决策**: v1.1 不实施 Endpoint Sharding，推迟到 v1.2。v1.1 通过两级线程池 + SemaphoreGuard 隔离慢端点。v1.2 按 `endpointId % 32` 分区，配合分区内的二级 SemaphoreGuard。需 v1.1 压测数据论证性能瓶颈后决策。

---

## 7. Webhook 签名与安全

### 7.1 HMAC-SHA256 签名实现

```java
@Component
public class WebhookSigner {

    /**
     * 生成签名
     * @param timestamp 秒级 Unix 时间戳（非毫秒）
     */
    public String sign(String payload, long timestamp, String secret) {
        String signStr = timestamp + "\n" + payload;
        byte[] hash = HmacUtils.hmacSha256(secret.getBytes(StandardCharsets.UTF_8), signStr);
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 验证签名（常量时间比较，防止时序攻击）
     * @param timestamp 秒级 Unix 时间戳
     */
    public boolean verify(String payload, long timestamp, String secret, String expectedSig) {
        // 5 分钟窗口防重放（秒级时间戳比较）
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - timestamp) > 300) {
            return false;
        }
        String computed = sign(payload, timestamp, secret);
        return MessageDigest.isEqual(
            computed.getBytes(StandardCharsets.UTF_8),
            expectedSig.getBytes(StandardCharsets.UTF_8)
        );
    }
}
```

### 7.2 密钥加密存储

```java
@Component
public class SecretEncryptor {

    @Value("${webhook.master-key}")  // Base64 编码的 32 字节密钥，来自 Nacos/Vault
    private String masterKeyBase64;

    @PostConstruct
    public void validateKey() {
        byte[] key = Base64.getDecoder().decode(masterKeyBase64);
        if (key.length != 32) {
            throw new IllegalStateException(
                "AES-256 主密钥必须为 32 字节，当前 " + key.length + " 字节");
        }
    }

    private SecretKeySpec deriveKey() {
        byte[] key = Base64.getDecoder().decode(masterKeyBase64);  // 显式 Base64 解码
        return new SecretKeySpec(key, "AES");
    }

    public EncryptedSecret encrypt(String plaintext) {
        byte[] iv = new byte[12];  // GCM 推荐 12 字节 IV
        SecureRandom.getInstanceStrong().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return new EncryptedSecret(
            Base64.getEncoder().encodeToString(ciphertext),
            Base64.getEncoder().encodeToString(iv)
        );
    }

    public String decrypt(String encryptedBase64, String ivBase64) {
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        byte[] ciphertext = Base64.getDecoder().decode(encryptedBase64);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
```

### 7.3 SSRF 防护

```java
@Component
public class SsrfValidator {

    /**
     * 创建/编辑端点时的 URL 校验（首次验证）
     * 注意：DNS Rebinding 攻击下，首次解析到公网 IP 但实际请求时指向内网
     * 因此还需要在 WebClient 层做二次校验（见下方 resolvedIp 黑名单拦截器）
     */
    public void validate(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (!"https".equals(scheme) && !"http".equals(scheme)) {
                throw new SsrfException("仅允许 HTTP/HTTPS 协议");
            }
            // 拒绝 file://, gopher:// 等危险协议
            InetAddress addr = InetAddress.getByName(uri.getHost());
            checkIp(addr);
        } catch (URISyntaxException | UnknownHostException e) {
            throw new SsrfException("URL 格式无效或无法解析");
        }
    }

    /**
     * RestTemplate 请求拦截器：在实际发起请求时二次校验解析后的 IP
     * 防止 DNS Rebinding 攻击
     */
    @Component
    public static class SsrfRequestInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                             ClientHttpRequestExecution execution) throws IOException {
            try {
                InetAddress addr = InetAddress.getByName(request.getURI().getHost());
                checkIp(addr);
            } catch (SsrfException e) {
                throw new IOException("SSRF 防护拦截: " + e.getMessage());
            }
            return execution.execute(request, body);
        }
    }

    private static void checkIp(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
            || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()) {
            throw new SsrfException("禁止访问内网地址: " + addr.getHostAddress());
        }
        // IPv6 映射的内网地址
        if (addr instanceof Inet6Address) {
            byte[] bytes = addr.getAddress();
            // 检查 IPv6 映射的 IPv4 内网地址 (::ffff:10.x.x.x 等)
            // 简化处理：通过 InetAddress 的内置方法已覆盖
        }
    }
}
```

---

## 8. RestTemplate 配置（HTTP 推送）

> v2.1 变更：从 WebClient 切换至 RestTemplate + 独立线程池。
> 原因：WebClient `.block()` 在 MQ 消费者线程上阻塞，高并发下消费者线程池耗尽。
> RestTemplate 配合 `@Async` 独立线程池，MVP 阶段更可控。

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate webhookRestTemplate(
            SsrfValidator.SsrfRequestInterceptor ssrfInterceptor) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);   // 连接超时 5s
        factory.setReadTimeout(10000);     // 读取超时 10s

        RestTemplate restTemplate = new RestTemplate(factory);
        // SSRF 二次校验拦截器（防 DNS Rebinding）
        restTemplate.setInterceptors(List.of(ssrfInterceptor));
        // 响应体截断（防止大响应打爆内存）
        restTemplate.getMessageConverters().stream()
            .filter(c -> c instanceof StringHttpMessageConverter)
            .forEach(c -> ((StringHttpMessageConverter) c).setMaxInMemorySize(256 * 1024));
        return restTemplate;
    }
}
```

---

## 9. 订阅匹配引擎

### 9.1 Redis 缓存结构

```
# 设备级订阅缓存
sub:device:{deviceId}         → Hash { subscriptionId → ruleSummaryJSON }

# 数据点级订阅缓存
sub:dp:{deviceId}:{dpId}      → Hash { subscriptionId → ruleSummaryJSON }

# 设备类型级订阅缓存
sub:product:{productId}       → Hash { subscriptionId → ruleSummaryJSON }

# 冷却去重
cooldown:rule:{ruleId}        → String "1" TTL=cooldownSeconds

# 推送疲劳控制
notify:guard:{endpointId}:{ruleId}:{deviceId}  → String "1" TTL=30s
```

### 9.2 匹配流程

```
1. 收到 DeviceMessage
2. Lua 脚本原子操作:
   a. HGETALL sub:device:{deviceId}          → 设备级订阅
   b. HGETALL sub:dp:{deviceId}:{dpId}       → 数据点级订阅
   c. HGETALL sub:product:{productId}        → 设备类型级订阅
   d. 合并去重
   e. 遍历规则，阈值比较
   f. EXPIRE cooldown:rule:{ruleId} NX       → 冷却检查
3. 返回命中规则列表 → 通过 KafkaTemplate 发送到 alert.event Topic
```

---

## 10. ADR（架构决策记录）

### ADR-001: 消息队列选型 → Apache Kafka

**背景**: 需要支持设备数据路由和 Webhook 推送解耦。项目已有 Kafka 基础设施。**决策**: 选 Kafka（复用现有集群）。理由：项目已有 Kafka 集群和运维经验、消费组模型天然支持多实例水平扩展、commit log 模型支持消息回放（数据恢复/重处理）、v1.1 Pipeline 拆分时通过独立消费组蓝绿切换。Topic 设计：`device.raw`(6分区, key=deviceId) + `alert.event`(12分区, key=subscriptionId) + `alert.event.dlt`(死信)。Spring Kafka 原生集成，offset 手动提交（manual_immediate）保证 at-least-once。不使用 Kafka retry topics 做重试——保留 DB 轮询 + Redis 锁方案（见 ADR-014）。

### ADR-002: 规则引擎范围 → V1 单条件阈值

**背景**: 规则复杂度直接影响开发周期和系统稳定性。**决策**: V1 只支持单条件阈值判断（≥/≤/==/!=）和离线检测。规则持久化为 JSON，运行时反序列化为 `SubscriptionRule` 接口实现，预留扩展能力。不引入 Drools。

### ADR-003: HTTP 推送客户端 → RestTemplate + 独立线程池 + Semaphore 隔离

**背景**: 需要 HTTP 推送到第三方。v2.0 使用 WebClient `.block()` 在 MQ 消费者线程上阻塞，高并发时消费者线程池耗尽。**决策**: 改用 RestTemplate + `@Async` 独立线程池 + per-endpoint Semaphore + CircuitBreaker。理由：阻塞模型在 `@Async` 线程池上可控、避免 Netty EventLoop 与 Kafka 消费者线程冲突。Semaphore 防止单个慢端点耗尽线程池，CircuitBreaker 在连续失败时跳过推送不浪费资源。v1.1 演进为 4-tier 线程池（Healthy/Recovered/Degraded/DeadLetter）。

### ADR-004: 消息可靠性策略 → at-least-once + 接收方去重

**背景**: Webhook 推送不能丢失，但 exactly-once 成本过高。**决策**: Kafka manual_immediate offset 提交保证至少一次投递（先处理业务后提交），每条推送带唯一 `event_id`（`X-Webhook-ID`），接收方通过 event_id 去重。推送日志持久化到 DB，`(event_id, config_id)` 唯一索引防重复推送。消费端用 Redis SETNX 做幂等检查。

### ADR-005: 重试策略 → 指数退避 2x，2 小时窗口

**背景**: 第三方服务可能暂时不可用。**决策**: 指数退避 2 倍增长（30s → 60s → 120s → 240s → 480s），最多 5 次，总窗口 2 小时。4xx 不重试（接收方拒绝），5xx/超时重试。超过最大重试或总窗口进入 DEAD 状态，支持手动重试。

### ADR-006: 签名算法 → HMAC-SHA256

**背景**: 需要防止伪造推送请求。**决策**: HMAC-SHA256，签名字符串 = `timestamp + "\n" + payload`，签名放在 `X-Webhook-Signature` 头。理由：行业标准（参考 Stripe/Volcengine）、实现简单、性能好。不做 RSA 签名（复杂度高，收益低）。

### ADR-007: 密钥存储 → AES-256-GCM

**背景**: Webhook 签名密钥需要安全存储。**决策**: AES-256-GCM 对称加密存储密钥，主密钥从 Nacos/Vault 注入。理由：GCM 模式提供认证加密（防篡改）、IV 随机生成并存库、主密钥不入库。

### ADR-008: 订阅匹配缓存 → Redis Hash + Lua

**决策**: 订阅关系缓存到 Redis Hash，匹配用 Lua 脚本保证原子性（单次 RTT）。订阅变更时主动刷新缓存。

### ADR-009: 数据源抽象 → 统一 DataSource 接口

IoT 设备数据作为 `source_type="iot"` 接入统一 DataSource 抽象，通过 `IoTDataSource` 实现类做数据标准化。未来其他类型数据源可复用同一订阅引擎。

### ADR-010: IoT 时序数据存储 → Redis + MySQL

**决策**: MVP 用 Redis（热数据 sorted set）+ MySQL（关系数据），不引入 TDengine/InfluxDB。理由：MVP 部署资源有限（2核4G），减少运维组件。V1.1 引入 TDengine 处理海量时序数据。

### ADR-011: 推送幂等性 → delivery_log 唯一索引

**背景**: MQ 消息可能重复投递，多实例竞争修复前也可能重复推送。**决策**: 在 `webhook_delivery_log` 表上建 `(event_id, config_id)` 唯一索引。Dispatcher 推送前先查是否已存在，存在则跳过。接收方通过 `X-Webhook-ID` 去重。

### ADR-012: KEK 存储方案 → 环境变量 + Base64

**背景**: AES-256-GCM 加密 Webhook 密钥，但 KEK（主密钥）本身需要安全存储。**决策**: 主密钥通过环境变量 `WEBHOOK_MASTER_KEY` 注入（Base64 编码的 32 字节随机值），由 Nacos/Vault 分发。不入库、不入镜像。启动时校验长度（`@PostConstruct`），不合法拒绝启动。

### ADR-013: 重试调度并发控制 → Redis 分布式锁

**背景**: 多实例部署下 `@Scheduled` 会重复扫描。**决策**: v2.1 采用 Redis `SET NX EX` 分布式锁，同一时刻只有一个实例执行重试扫描。锁 30 秒 TTL 自动过期防死锁，Lua 脚本安全释放防误删。查询分页 `LIMIT 100` 防止 OOM，`fixedRate` 替代 `fixedDelay` 防止调度间隔拉长。

### ADR-014: 重试调度方案 → DB 轮询 + Redis 分布式锁

**背景**: Webhook 推送失败需要重试。Kafka 没有原生延迟消息，retry topics 方案本质是 hack（多 topic + 多 consumer + Thread.sleep 占用线程）。**决策**: 采用 DB 轮询 + Redis 分布式锁方案。理由：方案已验证可靠、与 Kafka 解耦（重试逻辑不依赖 MQ 特性）、运维简单（无需管理 retry topics）。v1.1 视性能需求考虑引入 MQ 延迟消息方案。

### ADR-015: 签名时间戳单位 → 秒级

**背景**: 签名和验签的时间戳单位必须统一，否则验证失败。**决策**: `X-Webhook-Timestamp` 和签名计算统一使用 **秒级 Unix 时间戳**（`Instant.now().getEpochSecond()`），防重放窗口 300 秒。

### ADR-016: v1.1 Pipeline 拆分（修订为 2 段方案）

**背景**: v1.0 的 `ingestion-group` 同时做入库和规则匹配，无法独立扩缩。原方案为 3 段拆分（ingestion/matching/dispatch 独立消费组）。**决策（修订）**: 改为 **2 段拆分**——`ingestion-group`（入库+匹配进程内）+ `dispatch-group`（推送独立消费组）。理由：入库 IO-bound + 匹配 CPU-bound 在同一进程内天然互补，避免额外 Kafka 往返（+10-30ms）和一组应用实例。分两步迁移：Step 1 纯代码重构（不动运行时），Step 2 消费组拆分（feature flag 灰度）。详见 §6.1 和 ADR-024。

### ADR-017: Endpoint Sharding（推迟到 v1.2）

**背景**: v1.0 所有端点共享同一消费组，慢端点拖慢整体消费进度。**决策**: v1.1 不实施 Endpoint Sharding。两级线程池 + SemaphoreGuard 已提供 per-endpoint 隔离。推迟到 v1.2，按 `endpointId % 32` 分区 + 分区内二级 SemaphoreGuard，需 v1.1 压测数据论证性能瓶颈。

### ADR-018: 线程池拒绝策略 → 自定义 RejectPolicy

**背景**: `CallerRunsPolicy` 在队列满时让 Kafka 消费者线程同步执行 HTTP 推送，阻塞 poll 循环导致 consumer 被判离线、触发 rebalance 雪崩。**决策**: 改用自定义 `WebhookRejectedExecutionHandler`，拒绝时将推送任务写入 DB 重试表（`status=RETRY_PENDING`），由现有 DB 轮询机制兜底。Kafka 消费线程永远不做 HTTP 调用。

### ADR-019: StormGuard Redis 降级 → 内存滑动窗口限速

**背景**: Redis 不可用时推送疲劳控制完全失效，可能导致下游 Webhook 端点被轰炸。**决策**: 降级为内存滑动窗口限速（默认 60 条/分钟，可配置），非完全放行。实现：`StormGuard` 内部维护两级策略——Redis 可用时走 Lua 精确控频，不可用时 fallback 到 `ConcurrentHashMap<String, LinkedList<Long>>` 内存窗口。连续 3 次 Redis 异常切换到内存模式，后台探活恢复后自动切回。

### ADR-020: Semaphore 封装 → SemaphoreGuard (AutoCloseable)

**背景**: `pushAsync` 中 finally 块依赖 `acquired` 变量条件释放，每个调用点都要写模板代码，copy-paste 易出错。**决策**: 封装为 `SemaphoreGuard implements AutoCloseable`，编译器保证 `close()` 调用。内部 `acquired` 标记防止没拿到 permit 却 release。

```java
public class SemaphoreGuard implements AutoCloseable {
    private final Semaphore semaphore;
    private boolean acquired = false;

    public SemaphoreGuard(Semaphore sem) { this.semaphore = sem; }

    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        acquired = semaphore.tryAcquire(timeout, unit);
        return acquired;
    }

    @Override
    public void close() {
        if (acquired) semaphore.release();
    }
}

// 使用方式
try (SemaphoreGuard guard = new SemaphoreGuard(getSemaphore(endpointId))) {
    if (!guard.tryAcquire(2, TimeUnit.SECONDS)) {
        scheduleRetry(log, RetryReason.CONCURRENCY_LIMIT);
        return;
    }
    doHttpPush(endpoint, headers, payload, log);
}
```

### ADR-021: Ack 时序 → 先持久化 DB 再提交 offset

**背景**: 原 `WebhookDispatcher` 在 `pushAsync` 入队后立即 `ack.acknowledge()`，应用重启时队列中未执行的任务丢失，违反 at-least-once 语义。**决策**: 改为先持久化 `deliveryLog(PENDING)` 到 DB → 事务提交后 `ack.acknowledge()` → 异步推送。配合补偿任务扫描 `status=PENDING + createdAt < now-2min` 的记录重试推送。补偿任务使用乐观锁（version 字段）防重复推送。

### ADR-022: 订阅缓存一致性 → 双写失效 + Lua status 校验

**背景**: 管理端将订阅 disabled 后 Redis 缓存未及时刷新，Lua 脚本仍匹配到已禁用规则，产生"幽灵推送"。**决策**: 三重保障：(1) 管理端操作时立即删除对应 Redis 缓存 key；(2) Lua 脚本内增加 `status == "ACTIVE"` 校验；(3) 按 `subscription:{id}` 缓存单条规则（禁用时只删一条，MGET 批量读取）。状态变更至多 5 秒内生效。

### ADR-023: Lua 匹配降级策略 → 跳过匹配 + 补偿标记

**背景**: Redis 连接中断时 Lua 脚本执行失败，需决定数据是丢弃还是保留。**决策**: 降级时只入库、跳过匹配、不丢弃消息。入库记录标记 `ruleMatchSkipped=true`，Redis 恢复后由定时任务补偿执行。理由：延迟告警优于假告警。不推荐内存副本（可能用过时规则产生误报）。

### ADR-024: Pipeline 2 段拆分方案

| 字段 | 值 |
|------|-----|
| 状态 | 提议 |
| 前置 ADR | ADR-016(修订), ADR-018~023 |

**背景**: v1.0 `DataIngestionService` 是 God Service（入库+匹配+发布），职责耦合、扩缩容不对等、背压传导。原 3 段方案增加额外 Kafka 往返和资源浪费。

**决策**: 采用 2 段方案。

```
Stage 1: ingestion-group (device.raw → persist + match → alert.event)
Stage 2: dispatch-group (alert.event → HTTP POST + retry + DLQ)
```

**2 段 vs 3 段 trade-off**：
- 延迟：进程内 <5ms vs 额外 Kafka +10-30ms
- 资源：入库 IO-wait + 匹配 CPU 互补 vs 各自独立浪费
- 运维：2 服务 + 2 Topic vs 3 服务 + 3 Topic
- 成本：~3 人日 vs ~6 人日

**附加决策**：
- 线程池 4→2 级（Healthy + Degraded），移除 parse pool 和 publish pool
- 移除 `MessageChannel`（直接用 KafkaTemplate）和 `MatchingEngine`（直接用 MatchingService）
- 保留 SubscriptionQueryService、DispatchClient、EndpointResolver 3 个接口
- alert.event 新增 eventId、version、endpointId、metadata 字段（向后兼容）
- 重试从 DB 轮询迁移到 dispatch.retry Topic（双写 + feature flag 灰度）

**迁移分步**：
1. Step 1 纯代码重构（~7 文件 500 行，不动运行时）→ 全量测试通过后部署
2. Step 2 消费组拆分（~8 新文件 770 行，feature flag 灰度）→ 端到端延迟 ≤ 基线 +10%

**部署拓扑**：Ingestion+Matching 2×(4C8Gi) + Dispatch 2×(2C4Gi) = 月度新增 ~¥600

### ADR-025: v1.1 灰度与回滚策略

| 字段 | 值 |
|------|-----|
| 状态 | 提议 |
| 前置 ADR | ADR-024 |

**灰度维度**: endpointId MurmurHash3 % 100，同一 endpoint 始终走同一路径，保证行为一致。rolloutPercent 单调递增（5→20→50→100）。

**灰度阶段**：

| 阶段 | 比例 | 持续 | 准入指标 |
|------|------|------|---------|
| P0 内部验证 | 0%（测试 endpoint） | 2天 | Step 2 部署完成 |
| P1 小流量 | 5% | 3天 | P0 指标达标 |
| P2 中流量 | 20% | 3天 | P1 指标达标 |
| P3 大流量 | 50% | 2天 | P2 指标达标 |
| P4 全量 | 100% | 持续 | P3 指标达标 |

**核心准入/回滚阈值**：

| 指标 | 准入（达标） | 回滚（触发） |
|------|-------------|-------------|
| 端到端 P99 | ≤ 基线 +15% | > 基线 +30% |
| dispatch 错误率 | < 0.5% | > 2% |
| dispatch-group lag | < 500 条/1h | > 5000 条/5min |
| ingestion-group lag | < 200 条/1h | > 2000 条/5min |

**灰度配置**: 通过 Nacos `pipeline.gray.rollout-pct` 控制，Java 端 30s 轮询刷新。

```java
public boolean hitGray(String endpointId, int rolloutPercent) {
    int bucket = Math.abs(Murmur3.hash32(endpointId.getBytes(UTF_8))) % 100;
    return bucket < rolloutPercent;
}
```

**回滚策略（3 种场景）**：

1. **灰度期回滚（10min）**: Nacos rollout_pct→0 → 等 lag 消费完 → 部署 v1.0 → 停止 dispatch-group
2. **全量后回滚（15min）**: 记录 dispatch-group offset → 部署 v1.0 → 停止 v1.1 → 删除 dispatch-group
3. **紧急回滚（5min）**: `rollback-v11-emergency.sh`（kubectl rollout undo + scale v1.1 to 0）

**回滚 SLA**: 从触发到流量切回 < 3 分钟（Nacos 配置推送 + 应用刷新）。

**v1.0 兼容性保证**：

| 维度 | 保证方式 |
|------|---------|
| 消息格式 | 新增字段可选，v1.0 Jackson FAIL_ON_UNKNOWN_PROPERTIES=false |
| 数据库 | 新增列有默认值，v1.0 SQL 不引用 |
| 消费组 | 双消费组期间 delivery_log.event_id 唯一索引幂等 |
| 回滚 | v1.0 Dispatcher 从上次 ingestion-group offset 继续 |

**v1.0 预检要求**: alert.event 扩分区不可逆，必须提前在 Staging 做 24 分区兼容测试（8 项指标全通过）。v1.0 consumer concurrency 调到 4（24/4=6 分区/线程），清除硬编码分区数。

**时间线**: D-7 Step1 开始 → D0 Step2 部署 P0 → D+10 全量 → D+17 稳定确认。

---

## 11. 后端模块结构

```
com.project.subscription/
├── config/
│   ├── KafkaConfig.java              # Kafka Producer/Consumer 配置
│   ├── KafkaTopicConfig.java         # Topic 自动创建配置
│   ├── RedisConfig.java              # Redis 序列化配置
│   ├── RestTemplateConfig.java       # RestTemplate + SSRF 拦截器
│   ├── AsyncConfig.java              # @Async 线程池配置
│   └── SchedulingConfig.java         # @EnableScheduling
├── controller/
│   ├── WebhookEndpointController.java  # Webhook 端点管理 API
│   ├── DeviceController.java           # 设备管理 API（只读）
│   ├── SubscriptionController.java     # 订阅管理 API
│   ├── WebhookLogController.java       # 推送日志 API
│   └── NotificationController.java     # 通知管理 API
├── service/
│   ├── DataIngestionService.java     # Kafka 消费者，数据接入 + 规则匹配
│   ├── WebhookDispatcher.java        # Kafka 消费者，委托 PushService
│   ├── SubscriptionEngine.java       # 订阅匹配引擎（Lua）
│   ├── WebhookPushService.java       # 异步 HTTP 推送（@Async + Semaphore 隔离）
│   ├── EndpointIsolationRegistry.java # per-endpoint Semaphore + CircuitBreaker
│   ├── WebhookRetryScheduler.java    # 重试调度（Redis 锁 + 分页）
│   ├── WebhookSigner.java            # HMAC-SHA256 签名（秒级时间戳）
│   ├── SecretEncryptor.java          # AES-256-GCM 密钥加密（Base64 KEK）
│   ├── SecretRotationService.java    # 密钥轮换（宽限期双密钥验签）
│   ├── SubscriptionTestService.java  # 测试推送（绕过 Kafka，直接推送）
│   ├── EndpointHealthService.java    # 端点健康指标聚合（Redis 缓存）
│   ├── NotificationService.java      # 站内通知创建与查询
│   ├── CircuitBreakerEventListener.java # 熔断器事件监听 → 通知
│   ├── SsrfValidator.java            # SSRF 防护（创建校验 + 请求拦截器）
│   ├── StormGuard.java              # 推送疲劳控制
│   └── DeviceStatusChecker.java      # 设备离线检测（定时任务）
├── model/
│   ├── entity/                       # JPA 实体
│   ├── dto/                          # 请求/响应 DTO
│   └── event/                        # 领域事件（AlertEvent 等）
├── repository/
│   ├── DeviceRepository.java
│   ├── WebhookEndpointRepository.java
│   ├── SubscriptionRepository.java
│   └── WebhookDeliveryLogRepository.java
└── rule/
    ├── SubscriptionRule.java         # 规则接口
    ├── ThresholdRule.java            # 阈值规则实现
    └── OfflineRule.java             # 离线检测规则实现
```
