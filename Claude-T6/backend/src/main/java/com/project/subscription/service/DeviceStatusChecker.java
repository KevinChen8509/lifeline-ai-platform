package com.project.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.subscription.model.event.AlertEvent;
import com.project.subscription.model.entity.SubscriptionRule;
import com.project.subscription.model.entity.WebhookSubscription;
import com.project.subscription.repository.DeviceRepository;
import com.project.subscription.repository.SubscriptionRuleRepository;
import com.project.subscription.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 设备离线检测 — 定时扫描离线规则，检测设备是否超时未上报
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusChecker {

    private final SubscriptionRuleRepository ruleRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final DeviceRepository deviceRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SubscriptionEngine subscriptionEngine;
    private final ObjectMapper objectMapper;

    /**
     * 每 60 秒扫描一次离线检测规则
     */
    @Scheduled(fixedDelay = 60000)
    public void checkOfflineDevices() {
        // 查找所有离线检测规则 (ruleType=2)
        List<WebhookSubscription> allActive = subscriptionRepository.findAll().stream()
            .filter(s -> s.getStatus() == 0).toList();

        int detected = 0;
        for (WebhookSubscription sub : allActive) {
            List<SubscriptionRule> rules = ruleRepository.findBySubscriptionId(sub.getId());
            for (SubscriptionRule rule : rules) {
                if (rule.getRuleType() != 2 || !rule.getEnabled()) continue;
                if (!subscriptionEngine.isCooledDown(rule)) continue;

                // 解析超时时间
                try {
                    var cond = objectMapper.readTree(rule.getConditionJson());
                    int timeoutSeconds = cond.has("timeout") ? cond.get("timeout").asInt(600) : 600;

                    // 检查目标设备最后活跃时间
                    var device = deviceRepository.findById(sub.getTargetId());
                    if (device.isPresent()) {
                        var d = device.get();
                        boolean isOffline = d.getLastActiveAt() == null ||
                            d.getLastActiveAt().plusSeconds(timeoutSeconds).isBefore(LocalDateTime.now());

                        if (isOffline && d.getStatus() != 2) { // 2=已离线
                            // 产生离线告警
                            AlertEvent alert = AlertEvent.builder()
                                .eventId("evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                                .eventType("device.offline")
                                .timestamp(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                                .subscriptionId(sub.getId())
                                .subscriptionName(sub.getName())
                                .endpointId(sub.getEndpointId())
                                .deviceId(d.getId())
                                .deviceName(d.getDeviceName())
                                .deviceKey(d.getDeviceKey())
                                .ruleId(rule.getId())
                                .ruleType("OFFLINE")
                                .conditionSummary("设备离线超时 " + timeoutSeconds + "s")
                                .priority(switch (rule.getPriority()) {
                                    case 0 -> "INFO";
                                    case 2 -> "CRITICAL";
                                    default -> "WARNING";
                                })
                                .build();

                            kafkaTemplate.send("alert.event", alert.getEventId(),
                                objectMapper.writeValueAsString(alert));
                            subscriptionEngine.markTriggered(rule.getId());
                            detected++;

                            // 更新设备状态
                            d.setStatus((short) 2);
                            deviceRepository.save(d);
                        }
                    }
                } catch (Exception e) {
                    log.error("离线检测失败 ruleId={}: {}", rule.getId(), e.getMessage());
                }
            }
        }

        if (detected > 0) {
            log.info("离线检测: 发现 {} 台设备离线", detected);
        }
    }
}
