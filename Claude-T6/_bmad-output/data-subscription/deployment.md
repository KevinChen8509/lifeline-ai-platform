# IoT 设备数据订阅功能 — 运维部署方案

> 版本: v3.0 | 日期: 2026-04-23 | 状态: 评审中
> v2.0 变更: 适配 Webhook 推送架构，更新监控指标和告警规则
> v2.1 变更: 纳入评审意见 — 补充重试调度监控、运维手册、环境变量更新
> v3.0 变更: 消息中间件从 RabbitMQ 迁移至 Kafka（KRaft 模式），更新部署配置、监控告警、运维手册

---

## 1. Docker Compose 部署架构

```
                    ┌─────────────┐
                    │   Nginx LB  │
                    │ (least_conn)│
                    └──────┬──────┘
               ┌───────────┼───────────┐
               ▼           ▼           ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │  App-1   │ │  App-2   │ │  App-3   │
        │ (Docker) │ │ (Docker) │ │ (Docker) │
        │ API+Push │ │ API+Push │ │ API+Push │
        └────┬─────┘ └────┬─────┘ └────┬─────┘
             │             │             │
             └─────────────┼─────────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼──────┐  ┌──────▼──────┐  ┌─────▼──────┐
    │   Kafka    │  │   Redis     │  │   MySQL    │
    │ ×3 (KRaft) │  │ 主从+Sentinel│  │  主从      │
    └────────────┘  └─────────────┘  └────────────┘
```

**变更说明**：
- App 节点通过 Spring Kafka 连接集群，Consumer Group 内建负载均衡
- App 节点新增 RestTemplate 出站连接池 + Semaphore 隔离，需要调大文件描述符限制
- Kafka 使用 KRaft 模式（无 Zookeeper），3 节点同时充当 Broker 和 Controller

---

## 2. Docker Compose 配置

```yaml
version: '3.8'

services:
  # ── 应用服务 ──
  app:
    image: ${REGISTRY}/iot-subscription:${TAG}
    deploy:
      replicas: 2
      resources:
        limits: { cpus: '2', memory: 4G }
        reservations: { cpus: '1', memory: 2G }
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql-master:3306/iot_subscription
      - SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092
      - SPRING_REDIS_HOST=redis-master
      - WEBHOOK_MASTER_KEY=${WEBHOOK_MASTER_KEY}    # AES 主密钥
      - WEBHOOK_POOL_MAX_CONNECTIONS=200
      - WEBHOOK_TIMEOUT_SECONDS=10
      - WEBHOOK_RETRY_MAX_ATTEMPTS=5
      - WEBHOOK_RETRY_BASE_SECONDS=30
      - WEBHOOK_RETRY_MULTIPLIER=2
      - WEBHOOK_RETRY_MAX_DELAY_SECONDS=1800
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3
    depends_on:
      - mysql-master
      - kafka-1
      - redis-master
    ulimits:
      nofile:
        soft: 65536
        hard: 65536

  # ── Nginx 负载均衡 ──
  nginx:
    image: nginx:1.25-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
    depends_on:
      - app

  # ── Kafka 集群 (3节点 KRaft) ──
  kafka-1:
    image: confluentinc/cp-kafka:7.6.0
    hostname: kafka-1
    ports: ["9092:9092", "9093:9093"]
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093'
      KAFKA_LISTENERS: 'PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka-1:9092'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 3000
      KAFKA_LOG_DIRS: '/var/lib/kafka/data'
      KAFKA_LOG_RETENTION_HOURS: 168
      KAFKA_LOG_SEGMENT_BYTES: 1073741824
      KAFKA_HEAP_OPTS: "-Xmx2g -Xms2g"
      KAFKA_JVM_PERFORMANCE_OPTS: "-XX:+UseG1GC -XX:MaxGCPauseMillis=20"
    volumes:
      - kafka-1-data:/var/lib/kafka/data
    deploy:
      resources:
        limits: { cpus: '2', memory: 3G }

  kafka-2:
    image: confluentinc/cp-kafka:7.6.0
    hostname: kafka-2
    ports: ["9192:9092", "9193:9093"]
    environment:
      KAFKA_NODE_ID: 2
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093'
      KAFKA_LISTENERS: 'PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka-2:9092'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 3000
      KAFKA_LOG_DIRS: '/var/lib/kafka/data'
      KAFKA_LOG_RETENTION_HOURS: 168
      KAFKA_LOG_SEGMENT_BYTES: 1073741824
      KAFKA_HEAP_OPTS: "-Xmx2g -Xms2g"
      KAFKA_JVM_PERFORMANCE_OPTS: "-XX:+UseG1GC -XX:MaxGCPauseMillis=20"
    volumes:
      - kafka-2-data:/var/lib/kafka/data
    deploy:
      resources:
        limits: { cpus: '2', memory: 3G }

  kafka-3:
    image: confluentinc/cp-kafka:7.6.0
    hostname: kafka-3
    ports: ["9292:9092", "9293:9093"]
    environment:
      KAFKA_NODE_ID: 3
      KAFKA_PROCESS_ROLES: 'broker,controller'
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093'
      KAFKA_LISTENERS: 'PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093'
      KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://kafka-3:9092'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT'
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
      KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT'
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 3000
      KAFKA_LOG_DIRS: '/var/lib/kafka/data'
      KAFKA_LOG_RETENTION_HOURS: 168
      KAFKA_LOG_SEGMENT_BYTES: 1073741824
      KAFKA_HEAP_OPTS: "-Xmx2g -Xms2g"
      KAFKA_JVM_PERFORMANCE_OPTS: "-XX:+UseG1GC -XX:MaxGCPauseMillis=20"
    volumes:
      - kafka-3-data:/var/lib/kafka/data
    deploy:
      resources:
        limits: { cpus: '2', memory: 3G }

  # ── Kafka Topic 初始化 ──
  kafka-init:
    image: confluentinc/cp-kafka:7.6.0
    depends_on:
      - kafka-1
      - kafka-2
      - kafka-3
    entrypoint: ['/bin/bash', '-c']
    command: |
      "
      sleep 15
      kafka-topics --bootstrap-server kafka-1:9092 --create --if-not-exists \
        --topic device.raw --partitions 6 --replication-factor 3 \
        --config retention.ms=259200000 --config compression.type=lz4
      kafka-topics --bootstrap-server kafka-1:9092 --create --if-not-exists \
        --topic alert.event --partitions 12 --replication-factor 3 \
        --config retention.ms=604800000 --config compression.type=snappy
      kafka-topics --bootstrap-server kafka-1:9092 --create --if-not-exists \
        --topic alert.event.dlt --partitions 6 --replication-factor 3 \
        --config retention.ms=2592000000
      echo 'Topics created successfully'
      "

  # ── Kafka 监控 ──
  kafka-exporter:
    image: danielqsj/kafka-exporter:latest
    ports: ["9308:9308"]
    command:
      - '--kafka.server=kafka-1:9092'
      - '--kafka.server=kafka-2:9092'
      - '--kafka.server=kafka-3:9092'
    depends_on: [kafka-1, kafka-2, kafka-3]

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    ports: ["8080:8080"]
    environment:
      KAFKA_CLUSTERS_0_NAME: 'iot-kafka'
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: 'kafka-1:9092,kafka-2:9092,kafka-3:9092'
    depends_on: [kafka-1, kafka-2, kafka-3]

  # ── Redis 主从 + Sentinel ──
  redis-master:
    image: redis:7-alpine
    command: redis-server /usr/local/etc/redis/redis.conf
    volumes:
      - redis-master-data:/data
      - ./redis/redis-master.conf:/usr/local/etc/redis/redis.conf:ro

  redis-slave:
    image: redis:7-alpine
    command: redis-server /usr/local/etc/redis/redis.conf --replicaof redis-master 6379
    volumes:
      - ./redis/redis-slave.conf:/usr/local/etc/redis/redis.conf:ro
    depends_on: [redis-master]

  redis-sentinel:
    image: redis:7-alpine
    command: redis-sentinel /usr/local/etc/redis/sentinel.conf
    deploy:
      replicas: 3
    volumes:
      - ./redis/sentinel.conf:/usr/local/etc/redis/sentinel.conf:ro
    depends_on: [redis-master]

  # ── MySQL 主从 ──
  mysql-master:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASS}
      MYSQL_DATABASE: iot_subscription
    volumes:
      - mysql-master-data:/var/lib/mysql
      - ./mysql/init.sql:/docker-entrypoint-initdb.d/init.sql:ro

  # ── 监控 ──
  prometheus:
    image: prom/prometheus:v2.51.0
    ports: ["9090:9090"]
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus

  grafana:
    image: grafana/grafana:10.4
    ports: ["3000:3000"]
    volumes:
      - grafana-data:/var/lib/grafana
    depends_on: [prometheus]

volumes:
  kafka-1-data:
  kafka-2-data:
  kafka-3-data:
  redis-master-data:
  mysql-master-data:
  prometheus-data:
  grafana-data:
```

---

## 3. Nginx 配置

**变更说明**: 移除 WebSocket proxy 配置，改为纯 API 反向代理 + `least_conn`。

```nginx
upstream api_backend {
    least_conn;

    server app-1:8080 max_fails=3 fail_timeout=30s;
    server app-2:8080 max_fails=3 fail_timeout=30s;

    keepalive 64;
}

server {
    listen 443 ssl;
    server_name iot.example.com;

    # API
    location /api/ {
        proxy_pass http://api_backend;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Host $host;
    }

    # Actuator (内部)
    location /actuator/ {
        allow 10.0.0.0/8;
        allow 172.16.0.0/12;
        allow 192.168.0.0/16;
        deny all;
        proxy_pass http://api_backend;
    }
}
```

---

## 4. Kafka 配置

### Topic 配置

| Topic | 分区数 | 副本数 | Retention | 压缩 | 说明 |
|-------|--------|--------|-----------|------|------|
| `device.raw` | 6 | 3 | 72h (delete) | lz4 | 设备原始数据，key=deviceId |
| `alert.event` | 12 | 3 | 168h (delete) | snappy | 告警事件，key=subscriptionId |
| `alert.event.dlt` | 6 | 3 | 720h (delete) | - | 死信，供排查和手动重发 |

### Spring Kafka 应用配置

```yaml
spring:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      enable-idempotence: true
      batch-size: 16384
      buffer-memory: 33554432
      linger-ms: 5
    consumer:
      group-id: iot-platform
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 100
      properties:
        spring.json.trusted.packages: "com.iot.*"
    listener:
      ack-mode: manual_immediate
      concurrency: 3
```

---

## 5. Redis 配置

### redis-master.conf

```ini
bind 0.0.0.0
port 6379
requirepass ${REDIS_PASS}

# AOF 持久化
appendonly yes
appendfsync everysec

# RDB 备份
save 900 1
save 300 10

# 内存
maxmemory 2gb
maxmemory-policy allkeys-lru

# 慢查询
slowlog-log-slower-than 10000
slowlog-max-len 128
```

### sentinel.conf

```ini
sentinel monitor mymaster redis-master 6379 2
sentinel auth-pass mymaster ${REDIS_PASS}
sentinel down-after-milliseconds mymaster 10000
sentinel failover-timeout mymaster 30000
sentinel parallel-syncs mymaster 1
```

---

## 6. 监控体系

### 6.1 指标分层

| 层次 | 来源 | 核心指标 |
|------|------|---------|
| L1 基础设施 | Node Exporter + cAdvisor | CPU/内存/磁盘/网络/容器资源 |
| L2 中间件 | Kafka Exporter + JMX Exporter + Redis Exporter | Consumer Lag/Broker 状态/命中率 |
| L3 应用 | Micrometer + Actuator | 推送延迟/规则匹配耗时/HTTP 出站连接池 |
| L4 业务 | 自定义埋点 | 推送成功率/重试率/端点健康度/签名耗时 |

### 6.2 应用埋点

```java
// Webhook 推送延迟（从数据产生到 HTTP 响应）
Timer pushLatency = Timer.builder("webhook.push.latency")
    .tag("status", "success|fail|timeout")
    .tag("endpoint", endpointId)
    .register(registry);

// 推送速率
Counter pushRate = Counter.builder("webhook.push.rate")
    .tag("status", "success|fail")
    .register(registry);

// 重试队列深度
Gauge retryQueueSize = Gauge.builder("webhook.retry.queue.size",
    retryScheduler::getPendingRetryCount)
    .register(registry);

// RestTemplate 线程池使用率
Gauge activeConnections = Gauge.builder("webhook.pool.active",
    threadPoolExecutor::getActiveCount)
    .register(registry);

// 规则匹配耗时
Timer ruleMatchLatency = Timer.builder("rule.engine.match.latency")
    .register(registry);

// 签名耗时
Timer signLatency = Timer.builder("webhook.sign.latency")
    .register(registry);
```

### 6.3 Prometheus 告警规则

```yaml
groups:
  - name: webhook-subscription-alerts
    rules:
      # ── Webhook 推送相关 ──

      # 推送成功率下降
      - alert: WebhookPushSuccessRateLow
        expr: |
          rate(webhook_push_rate_total{status="success"}[5m])
          /
          rate(webhook_push_rate_total[5m]) < 0.95
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "Webhook 推送成功率低于 95%"

      # 推送延迟 P95 过高
      - alert: WebhookPushLatencyHigh
        expr: histogram_quantile(0.95, webhook_push_latency_seconds_bucket) > 5
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "Webhook 推送延迟 P95 超过 5 秒"

      # 重试队列积压
      - alert: WebhookRetryQueueBacklog
        expr: webhook_retry_queue_size > 500
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "Webhook 重试队列积压超过 500 条"

      # 端点连续失败
      - alert: WebhookEndpointConsecutiveFailures
        expr: webhook_endpoint_consecutive_failures > 20
        for: 2m
        labels: { severity: critical }
        annotations:
          summary: "端点 {{ $labels.endpoint }} 连续失败超过 20 次"

      # 推送风暴检测
      - alert: WebhookPushStorm
        expr: rate(webhook_push_rate_total[1m]) > 500
        for: 30s
        labels: { severity: critical }
        annotations:
          summary: "Webhook 推送速率超过 500 次/分钟"

      # ── Kafka 相关 ──

      # Broker 宕机
      - alert: KafkaBrokerDown
        expr: up{job="kafka-exporter"} == 0
        for: 1m
        labels: { severity: critical }
        annotations:
          summary: "Kafka Broker {{ $labels.instance }} 宕机"

      # Consumer Group Lag 过高
      - alert: KafkaConsumerGroupLagHigh
        expr: kafka_consumergroup_group_lag > 10000
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "Consumer Group {{ $labels.consumergroup }} Lag 过高: {{ $value }}"

      # Consumer Group Lag 极高
      - alert: KafkaConsumerGroupLagCritical
        expr: kafka_consumergroup_group_lag > 100000
        for: 3m
        labels: { severity: critical }
        annotations:
          summary: "Consumer Group {{ $labels.consumergroup }} Lag 极其严重: {{ $value }}"

      # Consumer 无活跃成员但有 Lag
      - alert: KafkaConsumerGroupNoMembers
        expr: kafka_consumergroup_group_members == 0 and on (consumergroup) kafka_consumergroup_group_lag > 0
        for: 5m
        labels: { severity: critical }
        annotations:
          summary: "Consumer Group {{ $labels.consumergroup }} 无成员但有积压"

      # Under-Replicated Partitions
      - alert: KafkaUnderReplicatedPartitions
        expr: kafka_server_replicamanager_underreplicatedpartitions > 0
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "Kafka 有 {{ $value }} 个 Under-Replicated Partitions"

      # ── 基础设施 ──

      # Redis 内存
      - alert: RedisMemoryHigh
        expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.8
        for: 3m
        labels: { severity: warning }

      # RestTemplate 线程池使用率
      - alert: WebhookPushPoolNearLimit
        expr: webhook_push_pool_active / 100 > 0.8
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "Webhook 推送线程池使用率超过 80%"

      # ── 重试调度相关（v2.1 新增）──

      # 重试积压超过阈值
      - alert: IoTRetryBacklogCritical
        expr: iot_retry_scan_found > 500
        for: 3m
        labels: { severity: critical }
        annotations:
          summary: "重试队列积压 {{ $value }} 条"

      # 重试推送失败率 > 30%
      - alert: IoTRetryHighFailureRate
        expr: >
          rate(iot_retry_push_result{status!="success"}[5m])
          / rate(iot_retry_push_result[5m]) > 0.3
        for: 5m
        labels: { severity: warning }
        annotations:
          summary: "重试推送失败率超过 30%"

      # 某端点持续无法推送
      - alert: IoTWebhookEndpointDown
        expr: >
          increase(iot_retry_push_result{status="http_error"}[10m]) > 20
        for: 10m
        labels: { severity: warning }
        annotations:
          summary: "端点持续推送失败，疑似不可用"

      # 重试推送延迟中位数超过 2 分钟
      - alert: IoTRetryPushLatencyHigh
        expr: histogram_quantile(0.5, iot_retry_push_delay_seconds_bucket) > 120
        for: 5m
        labels: { severity: info }
        annotations:
          summary: "重试推送延迟中位数超过 2 分钟"
```

### 6.4 Grafana Dashboard 布局

```
┌─────────────────────┬─────────────────────┐
│ 推送成功率 (实时)     │ 推送延迟 P50/P95/P99 │
│ [时间线 - 2xx/5xx]  │ [时间线]             │
├─────────────────────┼─────────────────────┤
│ Kafka Consumer Lag  │ 重试队列深度         │
│ [时间线 - 按Group]  │ [时间线 + 阈值线]    │
├─────────────────────┼─────────────────────┤
│ 推送量/分钟 (风暴检测)│ 线程池使用率         │
│ [时间线 + 告警线]    │ [仪表盘 0-100]       │
├─────────────────────┼─────────────────────┤
│ 端点健康度分布       │ 签名耗时 P99        │
│ [柱状图 - 按端点]    │ [时间线]             │
└─────────────────────┴─────────────────────┘
```

---

## 7. 容量规划

### 7.1 三档资源配置

| 规模 | 推送 QPS | App 节点 | Kafka | Redis | MySQL | 月成本 |
|------|---------|---------|-------|-------|-------|--------|
| **100** | 100 req/s | 2核4G × 2 | 2核3G × 3 (已有) | 2核4G 主从 | 2核4G | ~3,000元 |
| **500** | 500 req/s | 2核4G × 3 | 2核3G × 3 (已有) | 4核8G 主从 | 4核8G | ~5,000元 |
| **2000** | 2000 req/s | 4核8G × 4 | 4核4G × 3 (扩容) | 4核16G 主从 | 4核16G | ~9,000元 |

### 7.2 单节点容量估算

| 组件 | 理论极限 | 安全线 |
|------|---------|--------|
| App Webhook 并发推送 (4核8G) | 2,000 req/s | 500 req/s |
| @Async 线程池 (core=50, max=100) | 100 并发 | 80 并发 |
| Kafka 单分区吞吐 | 10,000 msg/s | 5,000 msg/s |
| Redis 单实例 OPS | 100,000/s | 50,000/s |
| 推送日志写入 MySQL | 5,000 INSERT/s | 2,000 INSERT/s |

### 7.3 扩容触发条件

- @Async 线程池使用率 > 80% → 增加 App replica
- Kafka Consumer Lag > 10000 持续 5 分钟 → 排查消费者或扩分区
- 推送成功率 < 95% 持续 5 分钟 → 排查目标端点
- 重试队列积压 > 500 → 排查第三方可用性

**建议先按 100 QPS 部署，跑两周监控数据后再做容量规划。**

---

## 8. 数据库迁移 (Flyway)

```sql
-- V1.0__init_iot_subscription.sql

-- 产品模板
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    product_key VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    protocol ENUM('MQTT','COAP','HTTP','TCP') DEFAULT 'MQTT',
    data_model JSON,
    status TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_key (product_key),
    KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 设备实例
CREATE TABLE device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    device_key VARCHAR(64) NOT NULL,
    device_secret VARCHAR(128),
    status TINYINT DEFAULT 0,
    last_active_at DATETIME,
    tags JSON,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_key (device_key),
    KEY idx_product (product_id),
    KEY idx_tenant_status (tenant_id, status),
    FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 设备数据点
CREATE TABLE device_data_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    identifier VARCHAR(64) NOT NULL,
    data_type VARCHAR(16) NOT NULL DEFAULT 'float',
    last_value VARCHAR(256),
    last_report_at DATETIME,
    quality TINYINT DEFAULT 0,
    UNIQUE KEY uk_device_dp (device_id, identifier),
    FOREIGN KEY (device_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 设备分组
CREATE TABLE device_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    parent_id BIGINT,
    group_type TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_tenant (tenant_id),
    FOREIGN KEY (parent_id) REFERENCES device_group(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE device_group_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    added_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_device (group_id, device_id),
    FOREIGN KEY (group_id) REFERENCES device_group(id),
    FOREIGN KEY (device_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Webhook 推送端点
CREATE TABLE webhook_endpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    url VARCHAR(512) NOT NULL,
    secret_encrypted VARCHAR(512) NOT NULL,
    secret_iv VARCHAR(64) NOT NULL,
    custom_headers JSON,
    status TINYINT DEFAULT 0 COMMENT '0=启用 1=禁用',
    consecutive_failures INT DEFAULT 0,
    last_push_at DATETIME,
    last_success_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_tenant_user (tenant_id, user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Webhook 订阅
CREATE TABLE webhook_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    endpoint_id BIGINT NOT NULL,
    name VARCHAR(128),
    subscription_type TINYINT NOT NULL COMMENT '0=设备级 1=设备类型级 2=分组级',
    target_id BIGINT NOT NULL,
    data_point_ids JSON,
    status TINYINT DEFAULT 0 COMMENT '0=启用 1=暂停 2=已删除',
    max_retries INT DEFAULT 5,
    retry_interval_seconds INT DEFAULT 10,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_status (user_id, status),
    KEY idx_target_type (target_id, subscription_type),
    KEY idx_tenant_user (tenant_id, user_id),
    KEY idx_endpoint (endpoint_id),
    FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 订阅规则
CREATE TABLE subscription_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id BIGINT NOT NULL,
    rule_type TINYINT NOT NULL COMMENT '0=阈值 1=变化率 2=离线检测',
    condition_json JSON NOT NULL,
    cooldown_seconds INT DEFAULT 300,
    priority TINYINT DEFAULT 1,
    enabled BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (subscription_id) REFERENCES webhook_subscription(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Webhook 推送日志
CREATE TABLE webhook_delivery_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_id BIGINT NOT NULL COMMENT '关联 webhook_endpoint.id',
    subscription_id BIGINT,
    rule_id BIGINT,
    event VARCHAR(64) NOT NULL COMMENT '事件类型',
    event_id VARCHAR(64) NOT NULL COMMENT '事件唯一ID',
    device_id BIGINT,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/RETRYING/DEAD',
    attempt_count INT DEFAULT 0,
    max_retries INT DEFAULT 5,
    response_code INT,
    response_body VARCHAR(2048),
    error_msg VARCHAR(1024),
    next_retry_at DATETIME,
    delivered_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status_retry (status, next_retry_at),
    INDEX idx_config_created (config_id, created_at),
    INDEX idx_event_id (event_id),
    INDEX idx_subscription_created (subscription_id, created_at),
    UNIQUE INDEX uk_event_endpoint (event_id, config_id)  -- 推送幂等性保障
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
    PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
    PARTITION p202606 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

---

## 9. 环境变量清单

| 变量 | 说明 | 示例值 |
|------|------|--------|
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka 集群地址 | `kafka-1:9092,kafka-2:9092,kafka-3:9092` |
| `WEBHOOK_MASTER_KEY` | AES-256 密钥加密主密钥（Base64 编码，32 字节） | `Base64(random 32 bytes)` |
| `WEBHOOK_TIMEOUT_SECONDS` | 单次推送超时（RestTemplate） | `10` |
| `WEBHOOK_RETRY_MAX_ATTEMPTS` | 默认最大重试次数 | `5` |
| `WEBHOOK_RETRY_BASE_SECONDS` | 首次重试间隔（秒） | `30` |
| `WEBHOOK_RETRY_MULTIPLIER` | 退避倍数 | `2` |
| `WEBHOOK_RETRY_MAX_DELAY_SECONDS` | 最大单次重试间隔（秒） | `1800` |
| `WEBHOOK_RETRY_WINDOW_MS` | 重试总窗口（毫秒） | `7200000`（2 小时） |
| `WEBHOOK_SSRF_ALLOW_HTTP` | 开发环境允许 HTTP | `false` |
| `WEBHOOK_SSRF_ALLOW_PRIVATE` | 开发环境允许内网 | `false` |

**安全提醒**: `WEBHOOK_MASTER_KEY` 必须通过 Vault/Nacos 注入，不得写入 docker-compose.yml 或镜像。启动时校验解码后长度必须为 32 字节。

---

## 10. 运维手册（Runbook）

### 10.1 重试积压排查

```sql
-- 查哪些端点在持续失败
SELECT config_id,
       COUNT(*) AS pending_count,
       MIN(created_at) AS oldest,
       MAX(attempt_count) AS max_attempts
FROM webhook_delivery_log
WHERE status IN ('RETRYING', 'PENDING')
  AND next_retry_at < NOW()
GROUP BY config_id
ORDER BY pending_count DESC;

-- 对已确认下线的端点，标记放弃（不要 DELETE，留审计记录）
UPDATE webhook_delivery_log
SET status = 'ABANDONED',
    error_msg = 'manual_ops_cleanup'
WHERE config_id = ? AND status = 'RETRYING' AND attempt_count >= 5;
```

### 10.2 紧急扩容

```bash
# 扩 App 实例（Consumer Group 自动 rebalance 分配分区）
docker compose up -d --scale app=5

# 如需提高 Kafka 消费并行度，增加分区数
kafka-topics --bootstrap-server kafka-1:9092 \
  --alter --topic alert.event --partitions 24
```

### 10.3 分区维护

```sql
-- 每月 1 号执行：创建下月分区 + 归档 3 个月前的分区
ALTER TABLE webhook_delivery_log ADD PARTITION (
    PARTITION p202607 VALUES LESS THAN (TO_DAYS('2026-08-01'))
);

-- 归档旧分区数据后删除
ALTER TABLE webhook_delivery_log DROP PARTITION p202604;
```

建议使用 `EVENT` 或应用启动时自动检查并创建未来 3 个月的分区。

### 10.4 端点连续失败重置

```sql
-- 手动重置端点连续失败计数（端点恢复后）
UPDATE webhook_endpoint
SET consecutive_failures = 0, status = 0
WHERE id = ?;
```

### 10.5 Kafka 运维命令

```bash
# 查看 Consumer Group 状态和 Lag
kafka-consumer-groups --bootstrap-server kafka-1:9092 --describe --all-groups

# 查看指定 Group 的 Lag
kafka-consumer-groups --bootstrap-server kafka-1:9092 \
  --describe --group dispatch-group

# 查看 Topic 详情
kafka-topics --bootstrap-server kafka-1:9092 --describe --topic alert.event

# 重置 Consumer Group Offset（慎用！用于数据回放）
kafka-consumer-groups --bootstrap-server kafka-1:9092 \
  --group dispatch-group --topic alert.event \
  --reset-offsets --to-earliest --execute

# 增加分区（只能增不能减）
kafka-topics --bootstrap-server kafka-1:9092 \
  --alter --topic alert.event --partitions 24

# 查看 DLT 中的消息
kafka-console-consumer --bootstrap-server kafka-1:9092 \
  --topic alert.event.dlt --from-beginning --max-messages 10
```
