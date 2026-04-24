-- IoT 设备数据订阅功能 — 数据库初始化脚本
-- MySQL 8.0+

CREATE DATABASE IF NOT EXISTS iot_subscription
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE iot_subscription;

-- ============================================================
-- 基础设施表（设备/产品，v1.0 由外部系统提供数据，此处为只读视图）
-- ============================================================

-- 产品模板
CREATE TABLE IF NOT EXISTS product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT                          NOT NULL,
    product_key     VARCHAR(64)                     NOT NULL,
    name            VARCHAR(128)                    NOT NULL,
    protocol        ENUM('MQTT','COAP','HTTP','TCP') NOT NULL DEFAULT 'MQTT',
    data_model      JSON                            NULL,
    status          TINYINT                         NOT NULL DEFAULT 0 COMMENT '0=开发中 1=已发布',
    created_at      DATETIME                        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME                        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_key (product_key),
    KEY idx_tenant_id (tenant_id)
) ENGINE=InnoDB COMMENT='产品模板';

-- 设备实例
CREATE TABLE IF NOT EXISTS device (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT                          NOT NULL,
    product_id      BIGINT                          NOT NULL,
    device_name     VARCHAR(128)                    NOT NULL,
    device_key      VARCHAR(64)                     NOT NULL,
    status          TINYINT                         NOT NULL DEFAULT 0 COMMENT '0=未激活 1=在线 2=离线 3=禁用',
    last_active_at  DATETIME                        NULL,
    tags            JSON                            NULL,
    created_at      DATETIME                        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME                        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_device_key (device_key),
    KEY idx_product_id (product_id),
    KEY idx_status (status),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB COMMENT='设备实例';

-- 设备数据点实例
CREATE TABLE IF NOT EXISTS device_data_point (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id       BIGINT                          NOT NULL,
    identifier      VARCHAR(64)                     NOT NULL,
    data_type       VARCHAR(16)                     NOT NULL DEFAULT 'float' COMMENT 'float/int/string/bool/json',
    last_value      VARCHAR(256)                    NULL,
    last_report_at  DATETIME                        NULL,
    quality         TINYINT                         NOT NULL DEFAULT 0 COMMENT '0=好 1=差 2=未知',
    UNIQUE KEY uk_device_identifier (device_id, identifier),
    KEY idx_device_id (device_id)
) ENGINE=InnoDB COMMENT='设备数据点实例';

-- 设备分组
CREATE TABLE IF NOT EXISTS device_group (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id       BIGINT                          NOT NULL,
    name            VARCHAR(128)                    NOT NULL,
    parent_id       BIGINT                          NULL COMMENT '父分组ID，支持多级嵌套',
    group_type      TINYINT                         NOT NULL DEFAULT 0 COMMENT '0=静态 1=动态',
    created_at      DATETIME                        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME                        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_tenant_id (tenant_id),
    KEY idx_parent_id (parent_id)
) ENGINE=InnoDB COMMENT='设备分组';

-- 设备-分组关联
CREATE TABLE IF NOT EXISTS device_group_member (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id        BIGINT                          NOT NULL,
    device_id       BIGINT                          NOT NULL,
    UNIQUE KEY uk_group_device (group_id, device_id),
    KEY idx_device_id (device_id)
) ENGINE=InnoDB COMMENT='设备-分组关联';

-- ============================================================
-- 订阅核心表
-- ============================================================

-- Webhook 推送端点
CREATE TABLE IF NOT EXISTS webhook_endpoint (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id           BIGINT                          NOT NULL,
    user_id             BIGINT                          NOT NULL,
    name                VARCHAR(128)                    NOT NULL,
    url                 VARCHAR(512)                    NOT NULL,
    secret_encrypted    VARCHAR(512)                    NOT NULL COMMENT 'AES-256-GCM 加密后的签名密钥',
    secret_iv           VARCHAR(64)                     NOT NULL COMMENT 'AES 初始化向量',
    custom_headers      JSON                            NULL COMMENT '自定义HTTP请求头',
    status              TINYINT                         NOT NULL DEFAULT 0 COMMENT '0=启用 1=禁用',
    consecutive_failures INT                            NOT NULL DEFAULT 0,
    last_push_at        DATETIME                        NULL,
    last_success_at     DATETIME                        NULL,
    created_at          DATETIME                        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME                        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_tenant_user (tenant_id, user_id),
    KEY idx_status (status)
) ENGINE=InnoDB COMMENT='Webhook推送端点';

-- Webhook 订阅
CREATE TABLE IF NOT EXISTS webhook_subscription (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id               BIGINT                      NOT NULL,
    user_id                 BIGINT                      NOT NULL,
    endpoint_id             BIGINT                      NOT NULL,
    name                    VARCHAR(128)                NOT NULL,
    subscription_type       TINYINT                     NOT NULL DEFAULT 0 COMMENT '0=设备级 1=设备类型级 2=分组级',
    target_id               BIGINT                      NOT NULL COMMENT '设备ID/产品ID/分组ID',
    data_point_ids          JSON                        NULL COMMENT '数据点ID列表，null=所有点',
    status                  TINYINT                     NOT NULL DEFAULT 0 COMMENT '0=启用 1=暂停 2=已删除',
    max_retries             INT                         NOT NULL DEFAULT 5,
    retry_interval_seconds  INT                         NOT NULL DEFAULT 10,
    created_at              DATETIME                    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME                    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_user_status (user_id, status),
    KEY idx_target_type (target_id, subscription_type),
    KEY idx_tenant_user (tenant_id, user_id),
    KEY idx_endpoint (endpoint_id),
    CONSTRAINT fk_subscription_endpoint FOREIGN KEY (endpoint_id) REFERENCES webhook_endpoint(id)
) ENGINE=InnoDB COMMENT='Webhook订阅';

-- 触发规则
CREATE TABLE IF NOT EXISTS subscription_rule (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    subscription_id     BIGINT                      NOT NULL,
    data_point_id       BIGINT                      NULL COMMENT '关联数据点，离线检测时为null',
    rule_type           TINYINT                     NOT NULL COMMENT '0=阈值 1=变化率(v1.1) 2=离线检测',
    condition_json      JSON                        NOT NULL COMMENT '规则条件',
    cooldown_seconds    INT                         NOT NULL DEFAULT 300,
    priority            TINYINT                     NOT NULL DEFAULT 1 COMMENT '0=Info 1=Warning 2=Critical',
    enabled             BOOLEAN                     NOT NULL DEFAULT TRUE,
    last_triggered_at   DATETIME                    NULL,
    CONSTRAINT fk_rule_subscription FOREIGN KEY (subscription_id) REFERENCES webhook_subscription(id)
) ENGINE=InnoDB COMMENT='触发规则';

-- 推送日志
CREATE TABLE IF NOT EXISTS webhook_delivery_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_id           BIGINT                      NOT NULL COMMENT '关联WebhookEndpoint',
    subscription_id     BIGINT                      NOT NULL,
    rule_id             BIGINT                      NULL,
    event               VARCHAR(64)                 NOT NULL COMMENT '事件类型',
    event_id            VARCHAR(64)                 NOT NULL COMMENT '事件唯一ID(去重)',
    device_id           BIGINT                      NOT NULL,
    payload             TEXT                        NOT NULL,
    status              VARCHAR(16)                 NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED/RETRYING/DEAD',
    attempt_count       INT                         NOT NULL DEFAULT 0,
    max_retries         INT                         NOT NULL DEFAULT 5,
    response_code       INT                         NULL,
    response_body       VARCHAR(2048)               NULL,
    error_msg           VARCHAR(1024)               NULL,
    source              VARCHAR(32)                 NULL COMMENT 'NORMAL/COMPENSATION',
    storm_guard_degraded BOOLEAN                    NOT NULL DEFAULT FALSE,
    rule_match_skipped  BOOLEAN                     NOT NULL DEFAULT FALSE,
    next_retry_at       DATETIME                    NULL,
    delivered_at        DATETIME                    NULL,
    created_at          DATETIME                    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME                    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_status_retry (status, next_retry_at),
    KEY idx_config_created (config_id, created_at),
    UNIQUE KEY uk_event_id (event_id),
    KEY idx_subscription_created (subscription_id, created_at)
) ENGINE=InnoDB COMMENT='推送日志';

-- ============================================================
-- 通知表
-- ============================================================

-- 站内通知
CREATE TABLE IF NOT EXISTS notification (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT                          NOT NULL,
    type            VARCHAR(32)                     NOT NULL COMMENT 'ENDPOINT_FAILURE/ENDPOINT_RECOVERED/PUSH_DEAD/SUBSCRIPTION_CANCELLED',
    title           VARCHAR(256)                    NOT NULL,
    content         TEXT                            NULL,
    related_id      BIGINT                          NULL,
    related_type    VARCHAR(32)                     NULL COMMENT 'ENDPOINT/SUBSCRIPTION',
    is_read         BOOLEAN                         NOT NULL DEFAULT FALSE,
    created_at      DATETIME                        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_read (user_id, is_read),
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB COMMENT='站内通知';

-- 通知偏好
CREATE TABLE IF NOT EXISTS notification_preference (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT                          NOT NULL,
    endpoint_failure_enabled    BOOLEAN                         NOT NULL DEFAULT TRUE,
    endpoint_recovered_enabled  BOOLEAN                         NOT NULL DEFAULT TRUE,
    push_dead_enabled           BOOLEAN                         NOT NULL DEFAULT TRUE,
    failure_frequency           VARCHAR(16)                     NOT NULL DEFAULT 'EACH' COMMENT 'EACH/DAILY_SUMMARY',
    quiet_hours_start           TIME                            NULL,
    quiet_hours_end             TIME                            NULL,
    UNIQUE KEY uk_user_id (user_id)
) ENGINE=InnoDB COMMENT='通知偏好';
