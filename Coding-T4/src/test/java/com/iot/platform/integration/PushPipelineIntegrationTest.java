package com.iot.platform.integration;

import com.iot.platform.application.DeviceDataApplicationService;
import com.iot.platform.application.DeviceDataApplicationService.UploadRequest;
import com.iot.platform.application.DeviceDataApplicationService.UploadResult;
import com.iot.platform.domain.alarm.repository.AlarmRecordRepository;
import com.iot.platform.domain.device.entity.ThresholdConfig;
import com.iot.platform.domain.device.enums.DeviceType;
import com.iot.platform.domain.device.repository.ThresholdConfigRepository;
import com.iot.platform.domain.device.schema.DeviceSchemaConfig;
import com.iot.platform.domain.device.infrastructure.WebhookPushService;
import com.iot.platform.domain.push.entity.PushTask;
import com.iot.platform.domain.push.repository.PushTaskRepository;
import com.iot.platform.domain.push.retry.RetryPolicy;
import com.iot.platform.domain.subscription.entity.SubscriptionConfig;
import com.iot.platform.domain.subscription.repository.SubscriptionConfigRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 推送链路端到端集成测试。
 * 覆盖：数据上传 → Schema校验 → 阈值评估 → 报警生成 → PushOrchestrator → PushTask持久化
 *
 * Sprint 2 Week 3-4
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PushPipelineIntegrationTest {

    @Autowired private DeviceDataApplicationService uploadService;
    @Autowired private DeviceSchemaConfig schemaConfig;
    @Autowired private WebhookPushService pushService;
    @Autowired private RetryPolicy retryPolicy;
    @Autowired private ThresholdConfigRepository thresholdRepo;
    @Autowired private SubscriptionConfigRepository subscriptionRepo;
    @Autowired private PushTaskRepository pushTaskRepo;
    @Autowired private AlarmRecordRepository alarmRepo;

    private static String testSubId;

    @BeforeAll
    static void setupSubscription(@Autowired SubscriptionConfigRepository subRepo) {
        // Clean up existing test subscription if present
        subRepo.findByProjectId("PRJ-INTEG-TEST").forEach(s -> subRepo.delete(s));

        SubscriptionConfig sub = new SubscriptionConfig();
        sub.setProjectId("PRJ-INTEG-TEST");
        sub.setName("integ-test-sub");
        sub.setPushUrl("http://localhost:18080/fake-endpoint");
        sub.setEncryptionMode(SubscriptionConfig.EncryptionMode.PLAIN);
        sub.setAddressMode(SubscriptionConfig.AddressMode.UNIFIED);
        sub.setDataTypes("[\"REALTIME_DATA\",\"ALERT\"]");
        sub.setDeviceTypes("[]");
        sub.setStatus(SubscriptionConfig.SubscriptionStatus.SUBSCRIBED);
        sub.setVerifyStatus(SubscriptionConfig.VerifyStatus.VERIFIED);
        sub.setSecret("test-secret-key-12345");
        sub.setSubscriptionId("sub_integ_test_" + System.currentTimeMillis());
        sub.setMaxRetries(16);
        subRepo.save(sub);
        testSubId = sub.getSubscriptionId();
    }

    // ── Schema 校验 ──────────────────────────────────────

    @Test
    @Order(1)
    void schemaValidation_validData_passes() {
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("LC0101000100001");
        req.setProjectId("PRJ-INTEG-TEST");
        req.setDataPoints(Map.of("water_level", 2.35, "air_height", 0.65, "battery", 85.0, "signal", -67));

        UploadResult result = uploadService.upload(req);
        assertTrue(result.isValidated());
        assertTrue(result.getValidationErrors().isEmpty());
        assertEquals("LC", result.getDeviceType());
    }

    @Test
    @Order(2)
    void schemaValidation_unknownField_rejected() {
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("LC0101000100001");
        req.setProjectId("PRJ-INTEG-TEST");
        req.setDataPoints(Map.of("water_level", 2.35, "unknown_metric", 999.0));

        UploadResult result = uploadService.upload(req);
        assertFalse(result.isValidated());
        assertTrue(result.getValidationErrors().stream().anyMatch(e -> e.contains("Unknown data point")));
    }

    @Test
    @Order(3)
    void schemaValidation_outOfRange_rejected() {
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("LC0101000100001");
        req.setProjectId("PRJ-INTEG-TEST");
        req.setDataPoints(Map.of("water_level", 60.0));

        UploadResult result = uploadService.upload(req);
        assertFalse(result.isValidated());
        assertTrue(result.getValidationErrors().stream().anyMatch(e -> e.contains("out of range")));
    }

    @Test
    @Order(4)
    void schemaValidation_missingPrimary_rejected() {
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("LC0101000100001");
        req.setProjectId("PRJ-INTEG-TEST");
        req.setDataPoints(Map.of("air_height", 0.65));

        UploadResult result = uploadService.upload(req);
        assertFalse(result.isValidated());
        assertTrue(result.getValidationErrors().stream().anyMatch(e -> e.contains("primary")));
    }

    // ── 4种设备类型全链路 ──────────────────────────────────────

    @Test
    @Order(10)
    void fullPipeline_LC_realtimeData_pushed() {
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("LC0101000100001");
        req.setProjectId("PRJ-INTEG-TEST");
        req.setDataPoints(Map.of("water_level", 1.5, "air_height", 1.2, "battery", 90.0));

        UploadResult result = uploadService.upload(req);
        assertTrue(result.isValidated());
        assertEquals(0, result.getThresholdsMatched());
        assertTrue(result.getSubscriptionsPushed() > 0, "Should push REALTIME_DATA");
    }

    @Test
    @Order(11)
    void fullPipeline_LL_realtimeData_pushed() {
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("LL0101000100001");
        req.setProjectId("PRJ-INTEG-TEST");
        req.setDataPoints(Map.of("instant_flow", 3.5, "water_level", 0.8, "flow_velocity", 0.5, "total_flow", 1000.0));

        UploadResult result = uploadService.upload(req);
        assertTrue(result.isValidated());
        assertEquals("LL", result.getDeviceType());
        assertTrue(result.getSubscriptionsPushed() > 0);
    }

    @Test
    @Order(12)
    void fullPipeline_YL_realtimeData_pushed() {
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("YL0101000100001");
        req.setProjectId("PRJ-INTEG-TEST");
        req.setDataPoints(Map.of("hourly_rainfall", 5.0, "rainfall_5min", 1.0, "current_rainfall", 20.0, "rain_status", 1));

        UploadResult result = uploadService.upload(req);
        assertTrue(result.isValidated());
        assertEquals("YL", result.getDeviceType());
    }

    @Test
    @Order(13)
    void fullPipeline_SZ_realtimeData_pushed() {
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("SZ0101000100001");
        req.setProjectId("PRJ-INTEG-TEST");
        req.setDataPoints(Map.of("cod", 30.0, "ph", 7.0, "ammonia_nitrogen", 1.5, "turbidity", 50.0));

        UploadResult result = uploadService.upload(req);
        assertTrue(result.isValidated());
        assertEquals("SZ", result.getDeviceType());
    }

    // ── 阈值触发 + 报警生成 ──────────────────────────────────────

    @Test
    @Order(20)
    void thresholdTriggered_alarmGeneratedAndPushed() {
        // Clean existing threshold rules for this project+device
        thresholdRepo.findByProjectIdAndDeviceType("PRJ-INTEG-TEST", DeviceType.LC)
            .forEach(r -> thresholdRepo.delete(r));

        // Create threshold rule
        ThresholdConfig rule = new ThresholdConfig();
        rule.setProjectId("PRJ-INTEG-TEST");
        rule.setScopeType(ThresholdConfig.ScopeType.MODEL);
        rule.setScopeId("LC010100");
        rule.setDeviceType(DeviceType.LC);
        rule.setMetricKey("water_level");
        rule.setRuleType(ThresholdConfig.RuleType.THRESHOLD);
        rule.setRuleConfig("{\"operator\":\"GTE\",\"value\":2.5}");
        rule.setSeverity(ThresholdConfig.Severity.CRITICAL);
        rule.setEnabled(true);
        thresholdRepo.save(rule);

        // Upload data above threshold
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("LC0101000100001");
        req.setProjectId("PRJ-INTEG-TEST");
        req.setDataPoints(Map.of("water_level", 3.8, "air_height", 0.1, "battery", 85.0));

        UploadResult result = uploadService.upload(req);
        assertTrue(result.isValidated());
        assertEquals(1, result.getThresholdsMatched(), "Should trigger 1 alarm");
        assertEquals(1, result.getAlarms().size());
        assertEquals("CRITICAL", result.getAlarms().get(0).getSeverity());
        assertEquals(3.8, result.getAlarms().get(0).getTriggerValue());
        assertTrue(result.getSubscriptionsPushed() >= 2, "Should push ALERT + REALTIME_DATA");

        // Verify alarm persisted
        long alarmCount = alarmRepo.count();
        assertTrue(alarmCount > 0, "Alarm should be persisted in DB");
    }

    // ── PushTask 分级重试验证 ──────────────────────────────────────

    @Test
    @Order(30)
    void retryPolicy_ALERT_12Attempts() {
        assertEquals(12, retryPolicy.getMaxAttempts("ALERT"));
        assertEquals(1800000, retryPolicy.getMaxDelayMs("ALERT"));
    }

    @Test
    @Order(31)
    void retryPolicy_REALTIME_3Attempts() {
        assertEquals(3, retryPolicy.getMaxAttempts("REALTIME_DATA"));
        assertEquals(300000, retryPolicy.getMaxDelayMs("REALTIME_DATA"));
    }

    @Test
    @Order(32)
    void retryPolicy_STATUS_8Attempts() {
        assertEquals(8, retryPolicy.getMaxAttempts("DEVICE_STATUS"));
        assertEquals(600000, retryPolicy.getMaxDelayMs("DEVICE_STATUS"));
    }

    @Test
    @Order(33)
    void retryPolicy_default_5Attempts() {
        assertEquals(5, retryPolicy.getMaxAttempts("UNKNOWN_TYPE"));
        assertEquals(300000, retryPolicy.getMaxDelayMs("UNKNOWN_TYPE"));
    }

    // ── PushTask 持久化验证 ──────────────────────────────────────

    @Test
    @Order(40)
    void pushTasksPersisted_withCorrectMaxRetries() {
        List<PushTask> tasks = pushTaskRepo.findBySubscriptionId(testSubId);
        assertFalse(tasks.isEmpty(), "Should have push tasks for test subscription");

        // Verify REALTIME_DATA tasks have 3 max retries
        List<PushTask> realtimeTasks = tasks.stream()
            .filter(t -> "REALTIME_DATA".equals(t.getDataType()))
            .toList();
        if (!realtimeTasks.isEmpty()) {
            assertEquals(3, realtimeTasks.get(0).getMaxRetries(),
                "REALTIME_DATA should have 3 max retries");
        }

        // Verify ALERT tasks have 12 max retries
        List<PushTask> alertTasks = tasks.stream()
            .filter(t -> "ALERT".equals(t.getDataType()))
            .toList();
        if (!alertTasks.isEmpty()) {
            assertEquals(12, alertTasks.get(0).getMaxRetries(),
                "ALERT should have 12 max retries");
        }
    }

    // ── HMAC 签名验证 ──────────────────────────────────────

    @Test
    @Order(50)
    void envelopeSignature_verifiable() {
        Map<String, Object> payload = Map.of("test", "value", "num", 42);
        String secret = "verify-secret";
        Map<String, Object> envelope = pushService.buildEnvelope("ALERT", payload, secret);

        // Reconstruct signing string
        long ts = (Long) envelope.get("timestamp");
        String id = (String) envelope.get("id");
        String payloadJson = (String) envelope.get("payload");
        String signingString = ts + "\n" + id + "\n" + payloadJson;

        assertTrue(pushService.verify(signingString, secret, (String) envelope.get("signature")));
    }

    @Test
    @Order(51)
    void envelopeSignature_wrongSecret_fails() {
        Map<String, Object> payload = Map.of("test", "value");
        Map<String, Object> envelope = pushService.buildEnvelope("REALTIME_DATA", payload, "correct-secret");

        long ts = (Long) envelope.get("timestamp");
        String id = (String) envelope.get("id");
        String payloadJson = (String) envelope.get("payload");
        String signingString = ts + "\n" + id + "\n" + payloadJson;

        assertFalse(pushService.verify(signingString, "wrong-secret", (String) envelope.get("signature")));
    }

    // ── Schema API ──────────────────────────────────────

    @Test
    @Order(60)
    void deviceSchema_all4TypesLoaded() {
        for (DeviceType dt : DeviceType.values()) {
            var schema = schemaConfig.getSchema(dt);
            assertNotNull(schema, "Schema should exist for " + dt);
            assertNotNull(schema.getPrimaryMetric());
            assertFalse(schema.getDataPoints().isEmpty());
        }
    }

    @Test
    @Order(61)
    void deviceSchema_SZ_hasAllWaterQualityPoints() {
        var schema = schemaConfig.getSchema(DeviceType.SZ);
        assertTrue(schema.getDataPoints().containsKey("cod"));
        assertTrue(schema.getDataPoints().containsKey("ph"));
        assertTrue(schema.getDataPoints().containsKey("ammonia_nitrogen"));
        assertTrue(schema.getDataPoints().containsKey("turbidity"));
        assertTrue(schema.getDataPoints().containsKey("dissolved_oxygen"));
        assertEquals("cod", schema.getPrimaryMetric());
    }
}
