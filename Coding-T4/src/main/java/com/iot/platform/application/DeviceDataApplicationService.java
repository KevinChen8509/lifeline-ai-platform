package com.iot.platform.application;

import com.iot.platform.domain.alarm.dto.AlarmDto;
import com.iot.platform.domain.alarm.entity.AlarmRecord;
import com.iot.platform.domain.alarm.repository.AlarmRecordRepository;
import com.iot.platform.domain.alarm.service.AlertService;
import com.iot.platform.domain.device.engine.ThresholdEvaluator;
import com.iot.platform.domain.device.entity.ThresholdConfig;
import com.iot.platform.domain.device.enums.DataPointId;
import com.iot.platform.domain.device.enums.DeviceType;
import com.iot.platform.domain.device.infrastructure.WebhookPushService;
import com.iot.platform.domain.device.repository.ThresholdConfigRepository;
import com.iot.platform.domain.device.schema.DeviceSchemaConfig;
import com.iot.platform.domain.device.service.DataPointProviderRegistry;
import com.iot.platform.domain.device.service.DeviceTypeResolver;
import com.iot.platform.domain.device.spi.DataPointProvider;
import com.iot.platform.domain.subscription.entity.SubscriptionConfig;
import com.iot.platform.domain.subscription.repository.SubscriptionConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDataApplicationService {

    private final DeviceTypeResolver typeResolver;
    private final DataPointProviderRegistry providerRegistry;
    private final ThresholdConfigRepository thresholdRepo;
    private final SubscriptionConfigRepository subscriptionRepo;
    private final AlarmRecordRepository alarmRepo;
    private final WebhookPushService pushService;
    private final ObjectMapper objectMapper;
    private final PushOrchestrator pushOrchestrator;
    private final DeviceSchemaConfig schemaConfig;

    @Data
    public static class UploadRequest {
        private String deviceCode;
        private String projectId;
        private String domain;
        private Map<String, Object> dataPoints;
    }

    @Data
    public static class UploadResult {
        private String deviceCode;
        private String deviceType;
        private boolean validated;
        private List<String> validationErrors = new ArrayList<>();
        private int thresholdsMatched;
        private List<AlarmDto.Response> alarms = new ArrayList<>();
        private int subscriptionsPushed;
    }

    @Transactional
    public UploadResult upload(UploadRequest req) {
        UploadResult result = new UploadResult();
        result.setDeviceCode(req.getDeviceCode());

        // 1. 解析设备类型
        DeviceTypeResolver.ParseResult parsed = typeResolver.resolve(req.getDeviceCode());
        DeviceType deviceType = parsed.getDeviceType();
        result.setDeviceType(deviceType.name());

        // 2. Schema 校验 + 数据清洗
        DeviceSchemaConfig.ValidationResult vr = schemaConfig.validate(deviceType, req.getDataPoints());
        if (!vr.isValid()) {
            result.setValidated(false);
            result.setValidationErrors(vr.getErrors());
            return result;
        }
        Map<String, Object> cleanData = vr.getCleanData();
        result.setValidated(true);

        String projectId = req.getProjectId() != null ? req.getProjectId() : "DEFAULT";
        String domain = req.getDomain() != null ? req.getDomain() : "DRAINAGE";

        // 4. 查询适用的阈值规则
        List<ThresholdConfig> enabledRules = thresholdRepo
            .findByProjectIdAndDeviceType(projectId, deviceType).stream()
            .filter(ThresholdConfig::getEnabled)
            .toList();

        // 5. 查询活跃订阅
        List<SubscriptionConfig> allSubs = subscriptionRepo.findByProjectId(projectId);
        List<SubscriptionConfig> activeSubs = allSubs.stream()
            .filter(s -> s.getStatus() == SubscriptionConfig.SubscriptionStatus.SUBSCRIBED)
            .filter(s -> s.getVerifyStatus() == SubscriptionConfig.VerifyStatus.VERIFIED)
            .toList();
        // 6. 阈值评估 + 报警生成
        ThresholdEvaluator evaluator = new ThresholdEvaluator();
        List<AlarmRecord> triggeredAlarms = new ArrayList<>();

        for (ThresholdConfig rule : enabledRules) {
            Object value = cleanData.get(rule.getMetricKey());
            if (value == null) continue;

            DataPointId dpId;
            try {
                dpId = DataPointId.resolve(rule.getMetricKey(), deviceType);
            } catch (IllegalArgumentException e) {
                continue;
            }

            // 从 ruleConfig JSON 解析 operator 和 value
            String ruleConfigJson = rule.getRuleConfig();
            OperatorAndValue ov = parseOperatorAndValue(ruleConfigJson);
            if (ov == null) continue;

            boolean triggered = evaluator.evaluate(dpId, value, ov.operator, ov.value);
            if (triggered) {
                AlarmRecord alarm = AlarmRecord.builder()
                    .alarmNo("ALT-" + System.currentTimeMillis() + "-" + String.format("%04d", new Random().nextInt(10000)))
                    .projectId(projectId)
                    .deviceCode(req.getDeviceCode())
                    .deviceType(deviceType)
                    .domain(domain)
                    .metricKey(rule.getMetricKey())
                    .metricName(dpId.getDisplayName())
                    .ruleType(AlarmRecord.RuleType.valueOf(rule.getRuleType().name()))
                    .severity(AlarmRecord.Severity.valueOf(rule.getSeverity().name()))
                    .triggerValue(((Number) value).doubleValue())
                    .thresholdValue(ov.value)
                    .operator(ov.operator.name())
                    .ruleConfig(ruleConfigJson)
                    .status(AlarmRecord.AlarmStatus.ACTIVE)
                    .build();
                triggeredAlarms.add(alarmRepo.save(alarm));
            }
        }
        result.setThresholdsMatched(triggeredAlarms.size());
        result.getAlarms().addAll(triggeredAlarms.stream().map(AlarmDto::toResponse).toList());

        // 6.5 报警自动恢复：检查该设备的 ACTIVE 报警是否恢复正常
        List<AlarmRecord> recoveredAlarms = autoRecoverAlarms(req.getDeviceCode(), cleanData, enabledRules, projectId, activeSubs);
        result.getAlarms().addAll(recoveredAlarms.stream().map(AlarmDto::toResponse).toList());

        // 7. 构建信封并通过 PushOrchestrator 推送
        int pushedCount = 0;
        for (SubscriptionConfig sub : activeSubs) {
            // 推送报警（仅当订阅包含 ALERT 类型时）
            if (!triggeredAlarms.isEmpty() && sub.getDataTypes().contains("ALERT")
                && matchesDeviceType(sub, deviceType)) {
                AlarmRecord primaryAlarm = triggeredAlarms.get(0);
                Map<String, Object> payload = buildAlertPayload(primaryAlarm, domain);
                Map<String, Object> envelope = buildEnvelopeForSub(sub, "ALERT", payload);
                submitToOrchestrator(sub, "ALERT", envelope);
                pushedCount++;
            }

            // 推送实时数据（仅当订阅包含 REALTIME_DATA 类型时）
            if (sub.getDataTypes().contains("REALTIME_DATA")
                && matchesDeviceType(sub, deviceType)) {
                Map<String, Object> payload = buildRealtimePayload(req.getDeviceCode(), deviceType, projectId, domain, cleanData);
                Map<String, Object> envelope = buildEnvelopeForSub(sub, "REALTIME_DATA", payload);
                submitToOrchestrator(sub, "REALTIME_DATA", envelope);
                pushedCount++;
            }
        }
        result.setSubscriptionsPushed(pushedCount);

        return result;
    }

    private void submitToOrchestrator(SubscriptionConfig sub, String dataType, Map<String, Object> envelope) {
        try {
            String envelopeJson = objectMapper.writeValueAsString(envelope);
            pushOrchestrator.submit(sub.getSubscriptionId(), sub.getPushUrl(), dataType, envelope, envelopeJson);
        } catch (Exception e) {
            log.warn("Submit to PushOrchestrator error: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildEnvelopeForSub(SubscriptionConfig sub, String type, Map<String, Object> payload) {
        if (sub.getEncryptionMode() == SubscriptionConfig.EncryptionMode.AES && sub.getAesKey() != null) {
            return pushService.buildEncryptedEnvelope(type, payload, sub.getSecret(), sub.getAesKey());
        }
        return pushService.buildEnvelope(type, payload, sub.getSecret());
    }

    private boolean matchesDeviceType(SubscriptionConfig sub, DeviceType deviceType) {
        String dt = sub.getDeviceTypes();
        if (dt == null || dt.isBlank() || "[]".equals(dt.trim())) return true;
        return dt.contains(deviceType.name());
    }

    private Map<String, Object> buildAlertPayload(AlarmRecord alarm, String domain) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", alarm.getAlarmNo());
        payload.put("eventType", alarm.getRuleType().name() + "_ALERT");
        payload.put("projectId", alarm.getProjectId());
        payload.put("timestamp", alarm.getCreatedAt().toString());
        payload.put("priority", alarm.getSeverity().name());
        payload.put("device", Map.of(
            "deviceId", alarm.getDeviceCode(),
            "deviceName", alarm.getDeviceName() != null ? alarm.getDeviceName() : "",
            "deviceType", alarm.getDeviceType().name(),
            "domain", domain
        ));
        payload.put("trigger", Map.of(
            "dataPointKey", alarm.getMetricKey(),
            "dataPointName", alarm.getMetricName(),
            "triggerValue", alarm.getTriggerValue(),
            "operator", alarm.getOperator(),
            "thresholdValue", alarm.getThresholdValue()
        ));
        payload.put("status", alarm.getStatus().name());
        return payload;
    }

    /**
     * 自动恢复：对设备的 ACTIVE 报警检查指标是否已回落正常。
     * 如果触发值不再满足阈值条件，则标记为 RECOVERED 并推送恢复通知。
     */
    private List<AlarmRecord> autoRecoverAlarms(String deviceCode, Map<String, Object> cleanData,
                                                  List<ThresholdConfig> enabledRules,
                                                  String projectId,
                                                  List<SubscriptionConfig> activeSubs) {
        List<AlarmRecord> activeAlarms = alarmRepo.findByDeviceCodeAndStatus(
            deviceCode, AlarmRecord.AlarmStatus.ACTIVE);
        if (activeAlarms.isEmpty()) return List.of();

        ThresholdEvaluator evaluator = new ThresholdEvaluator();
        List<AlarmRecord> recovered = new ArrayList<>();

        for (AlarmRecord alarm : activeAlarms) {
            Object currentValue = cleanData.get(alarm.getMetricKey());
            if (currentValue == null) continue;

            // 检查当前值是否不再满足阈值条件
            DataPointId dpId;
            try {
                dpId = DataPointId.resolve(alarm.getMetricKey(),
                    alarm.getDeviceType());
            } catch (IllegalArgumentException e) {
                continue;
            }

            ThresholdEvaluator.Operator op = ThresholdEvaluator.Operator.valueOf(alarm.getOperator());
            boolean stillTriggered = evaluator.evaluate(dpId, currentValue, op, alarm.getThresholdValue());

            if (!stillTriggered) {
                alarm.setStatus(AlarmRecord.AlarmStatus.RECOVERED);
                alarm.setResolvedAt(java.time.LocalDateTime.now());
                alarmRepo.save(alarm);
                recovered.add(alarm);

                // 推送恢复通知
                for (SubscriptionConfig sub : activeSubs) {
                    if (sub.getDataTypes().contains("ALERT") && matchesDeviceType(sub, alarm.getDeviceType())) {
                        Map<String, Object> payload = buildAlertPayload(alarm, alarm.getDomain());
                        payload.put("status", "RECOVERED");
                        Map<String, Object> envelope = buildEnvelopeForSub(sub, "ALERT", payload);
                        submitToOrchestrator(sub, "ALERT", envelope);
                    }
                }
            }
        }
        return recovered;
    }

    private Map<String, Object> buildRealtimePayload(String deviceCode, DeviceType deviceType,
                                                      String projectId, String domain,
                                                      Map<String, Object> dataPoints) {
        List<Map<String, Object>> dpList = new ArrayList<>();
        dataPoints.forEach((key, value) -> {
            try {
                DataPointId dp = DataPointId.resolve(key, deviceType);
                dpList.add(Map.of("key", key, "name", dp.getDisplayName(), "unit", dp.getUnit(), "value", value));
            } catch (IllegalArgumentException ignored) {}
        });

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", projectId);
        payload.put("deviceId", deviceCode);
        payload.put("deviceType", deviceType.name());
        payload.put("domain", domain);
        payload.put("timestamp", java.time.LocalDateTime.now().toString());
        payload.put("dataPoints", dpList);
        return payload;
    }

    record OperatorAndValue(ThresholdEvaluator.Operator operator, double value) {}

    private OperatorAndValue parseOperatorAndValue(String ruleConfigJson) {
        try {
            // ruleConfig format: {"operator":"GTE","value":2.5,...}
            String operatorStr = extractJsonValue(ruleConfigJson, "operator");
            String valueStr = extractJsonValue(ruleConfigJson, "value");
            if (operatorStr == null || valueStr == null) return null;
            return new OperatorAndValue(
                ThresholdEvaluator.Operator.valueOf(operatorStr),
                Double.parseDouble(valueStr)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return json.substring(start + 1, end);
        }
        // numeric
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
            end++;
        }
        return json.substring(start, end);
    }
}
