package com.project.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.subscription.model.event.AlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcher {

    private final WebhookPushService pushService;
    private final ObjectMapper objectMapper;

    /**
     * 消费 alert.event → 委托 WebhookPushService 执行 HTTP 推送
     */
    @KafkaListener(topics = "alert.event", groupId = "dispatch-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onAlertEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            AlertEvent alert = objectMapper.readValue(record.value(), AlertEvent.class);
            log.debug("收到告警事件: eventId={}, endpointId={}, subscriptionId={}",
                alert.getEventId(), alert.getEndpointId(), alert.getSubscriptionId());

            // 构建推送 payload
            String payload = buildPayload(alert);

            // 异步推送
            pushService.push(alert.getEndpointId(), alert.getEventId(),
                alert.getEventType(), payload);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("alert.event 消费失败: {}", e.getMessage(), e);
        }
    }

    private String buildPayload(AlertEvent alert) {
        try {
            var payload = new java.util.LinkedHashMap<String, Object>();
            payload.put("event_id", alert.getEventId());
            payload.put("event_type", alert.getEventType());
            payload.put("timestamp", java.time.Instant.ofEpochMilli(alert.getTimestamp()).toString());
            payload.put("subscription", Map.of(
                "id", alert.getSubscriptionId(),
                "name", alert.getSubscriptionName() != null ? alert.getSubscriptionName() : ""
            ));
            payload.put("device", Map.of(
                "id", alert.getDeviceId(),
                "device_key", alert.getDeviceKey() != null ? alert.getDeviceKey() : "",
                "product_key", alert.getProductKey() != null ? alert.getProductKey() : ""
            ));
            if (alert.getDataPointIdentifier() != null) {
                payload.put("data_point", Map.of(
                    "identifier", alert.getDataPointIdentifier(),
                    "name", alert.getDataPointName() != null ? alert.getDataPointName() : alert.getDataPointIdentifier()
                ));
                payload.put("trigger_value", alert.getTriggerValue());
            }
            payload.put("trigger_condition", alert.getConditionSummary());
            payload.put("rule", Map.of(
                "id", alert.getRuleId(),
                "type", alert.getRuleType()
            ));
            payload.put("priority", alert.getPriority());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("构建推送 payload 失败: {}", e.getMessage());
            return "{}";
        }
    }
}
