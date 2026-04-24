package com.project.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.subscription.model.event.AlertEvent;
import com.project.subscription.model.event.DeviceRawEvent;
import com.project.subscription.model.entity.Device;
import com.project.subscription.model.entity.SubscriptionRule;
import com.project.subscription.model.entity.WebhookSubscription;
import com.project.subscription.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataIngestionService {

    private final SubscriptionEngine subscriptionEngine;
    private final DeviceRepository deviceRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 消费 device.raw → 规则匹配 → 生产 alert.event
     */
    @KafkaListener(topics = "device.raw", groupId = "ingestion-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onDeviceRaw(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            DeviceRawEvent event = objectMapper.readValue(record.value(), DeviceRawEvent.class);
            log.debug("收到设备数据: deviceId={}, dataPoints={}", event.getDeviceId(),
                event.getDataPoints() != null ? event.getDataPoints().size() : 0);

            // 查找匹配规则
            Long productId = resolveProductId(event);
            List<SubscriptionEngine.MatchedRule> matches =
                subscriptionEngine.findMatchingRules(event.getDeviceId(), productId);

            if (matches.isEmpty()) {
                ack.acknowledge();
                return;
            }

            // 评估每条规则
            int alertCount = 0;
            for (SubscriptionEngine.MatchedRule match : matches) {
                SubscriptionRule rule = match.rule();
                WebhookSubscription sub = match.subscription();

                if (!subscriptionEngine.isCooledDown(rule)) continue;

                boolean triggered = false;
                String identifier = null;
                Object triggerValue = null;

                if (rule.getRuleType() == 0) {
                    // 阈值规则 — 检查数据点值
                    if (event.getDataPoints() != null && rule.getDataPointId() != null) {
                        // 尝试从 dataPoints map 中匹配
                        for (Map.Entry<String, Object> entry : event.getDataPoints().entrySet()) {
                            double value = toDouble(entry.getValue());
                            if (subscriptionEngine.evaluateThreshold(rule, value)) {
                                triggered = true;
                                identifier = entry.getKey();
                                triggerValue = entry.getValue();
                                break;
                            }
                        }
                    }
                } else if (rule.getRuleType() == 2) {
                    // 离线检测 — 由 DeviceStatusChecker 单独处理，此处跳过
                    continue;
                }

                if (triggered) {
                    AlertEvent alert = buildAlertEvent(event, sub, rule, identifier, triggerValue);
                    String alertJson = objectMapper.writeValueAsString(alert);
                    kafkaTemplate.send("alert.event", alert.getEventId(), alertJson);
                    subscriptionEngine.markTriggered(rule.getId());
                    alertCount++;
                }
            }

            if (alertCount > 0) {
                log.info("deviceId={} 匹配 {} 条规则，产生 {} 条告警", event.getDeviceId(), matches.size(), alertCount);
            }

            // 先持久化 DB 再提交 offset (ADR-021)
            ack.acknowledge();
        } catch (Exception e) {
            log.error("device.raw 消费失败: {}", e.getMessage(), e);
            // 不确认，让 Kafka 重投
        }
    }

    private AlertEvent buildAlertEvent(DeviceRawEvent event, WebhookSubscription sub,
                                        SubscriptionRule rule, String identifier, Object triggerValue) {
        String conditionSummary = rule.getRuleType() == 0
            ? identifier + " " + summarizeCondition(rule.getConditionJson())
            : "offline";

        String priority = switch (rule.getPriority()) {
            case 0 -> "INFO";
            case 2 -> "CRITICAL";
            default -> "WARNING";
        };

        return AlertEvent.builder()
            .eventId("evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
            .eventType("alert.triggered")
            .timestamp(Instant.now().toEpochMilli())
            .subscriptionId(sub.getId())
            .subscriptionName(sub.getName())
            .endpointId(sub.getEndpointId())
            .deviceId(event.getDeviceId())
            .deviceName(null) // 可后续从缓存补充
            .deviceKey(event.getDeviceKey())
            .productKey(event.getProductKey())
            .ruleId(rule.getId())
            .ruleType(rule.getRuleType() == 0 ? "THRESHOLD" : "OFFLINE")
            .conditionSummary(conditionSummary)
            .priority(priority)
            .dataPointIdentifier(identifier)
            .triggerValue(triggerValue)
            .triggerCondition(rule.getConditionJson())
            .build();
    }

    private Long resolveProductId(DeviceRawEvent event) {
        if (event.getProductId() != null) return event.getProductId();
        return deviceRepository.findById(event.getDeviceId())
            .map(Device::getProductId).orElse(null);
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (Exception e) { return Double.NaN; }
    }

    private String summarizeCondition(String conditionJson) {
        try {
            var node = objectMapper.readTree(conditionJson);
            String op = node.get("operator").asText();
            double threshold = node.get("threshold").asDouble();
            String opSymbol = switch (op) {
                case "gt" -> ">";
                case "gte" -> ">=";
                case "lt" -> "<";
                case "lte" -> "<=";
                case "eq" -> "==";
                case "neq" -> "!=";
                default -> op;
            };
            return opSymbol + threshold;
        } catch (Exception e) {
            return conditionJson;
        }
    }
}
