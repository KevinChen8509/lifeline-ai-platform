package com.project.subscription.model.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警事件 (Kafka topic: alert.event) — 匹配成功后产生，由 Dispatcher 消费推送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {
    private String eventId;
    private String eventType;         // alert.triggered / device.offline / device.online
    private Long timestamp;
    private Long subscriptionId;
    private String subscriptionName;
    private Long endpointId;
    private Long deviceId;
    private String deviceName;
    private String deviceKey;
    private String productKey;
    private Long ruleId;
    private String ruleType;           // THRESHOLD / OFFLINE
    private String conditionSummary;   // "temperature > 80°C"
    private String priority;           // INFO / WARNING / CRITICAL
    private String dataPointIdentifier;
    private String dataPointName;
    private Object triggerValue;
    private String triggerCondition;
    private String payload;            // 完整推送 JSON（预构建）
}
