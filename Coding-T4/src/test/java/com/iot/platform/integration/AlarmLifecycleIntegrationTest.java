package com.iot.platform.integration;

import com.iot.platform.application.AlarmApplicationService;
import com.iot.platform.application.DeviceDataApplicationService;
import com.iot.platform.application.DeviceDataApplicationService.UploadRequest;
import com.iot.platform.application.DeviceDataApplicationService.UploadResult;
import com.iot.platform.domain.alarm.entity.AlarmRecord;
import com.iot.platform.domain.alarm.repository.AlarmRecordRepository;
import com.iot.platform.domain.device.entity.ThresholdConfig;
import com.iot.platform.domain.device.enums.DeviceType;
import com.iot.platform.domain.device.infrastructure.WebhookPushService;
import com.iot.platform.domain.device.repository.ThresholdConfigRepository;
import com.iot.platform.domain.subscription.entity.SubscriptionConfig;
import com.iot.platform.domain.subscription.repository.SubscriptionConfigRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 报警全生命周期集成测试。
 * 覆盖：报警生成 → 自动恢复 → 手动确认 → 手动解决 → 统计 API → AES 加密
 *
 * Sprint 3
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AlarmLifecycleIntegrationTest {

    @Autowired private DeviceDataApplicationService uploadService;
    @Autowired private AlarmApplicationService alarmService;
    @Autowired private AlarmRecordRepository alarmRepo;
    @Autowired private ThresholdConfigRepository thresholdRepo;
    @Autowired private SubscriptionConfigRepository subscriptionRepo;
    @Autowired private WebhookPushService pushService;

    private static final String PROJECT = "PRJ-ALARM-TEST";
    private static String testAlarmNo;

    @BeforeAll
    static void setup(@Autowired SubscriptionConfigRepository subRepo,
                      @Autowired ThresholdConfigRepository thRepo) {
        // Clean up
        subRepo.findByProjectId(PROJECT).forEach(s -> subRepo.delete(s));
        thRepo.findByProjectIdAndDeviceType(PROJECT, DeviceType.LC).forEach(r -> thRepo.delete(r));

        // Create subscription
        SubscriptionConfig sub = new SubscriptionConfig();
        sub.setProjectId(PROJECT);
        sub.setName("alarm-test-sub");
        sub.setPushUrl("http://localhost:18080/fake-endpoint");
        sub.setEncryptionMode(SubscriptionConfig.EncryptionMode.PLAIN);
        sub.setAddressMode(SubscriptionConfig.AddressMode.UNIFIED);
        sub.setDataTypes("[\"REALTIME_DATA\",\"ALERT\"]");
        sub.setDeviceTypes("[]");
        sub.setStatus(SubscriptionConfig.SubscriptionStatus.SUBSCRIBED);
        sub.setVerifyStatus(SubscriptionConfig.VerifyStatus.VERIFIED);
        sub.setSecret("alarm-test-secret-key");
        sub.setSubscriptionId("sub_alarm_test_" + System.currentTimeMillis());
        sub.setMaxRetries(16);
        subRepo.save(sub);

        // Create threshold: water_level >= 2.5 CRITICAL
        ThresholdConfig rule = new ThresholdConfig();
        rule.setProjectId(PROJECT);
        rule.setScopeType(ThresholdConfig.ScopeType.MODEL);
        rule.setScopeId("LC010100");
        rule.setDeviceType(DeviceType.LC);
        rule.setMetricKey("water_level");
        rule.setRuleType(ThresholdConfig.RuleType.THRESHOLD);
        rule.setRuleConfig("{\"operator\":\"GTE\",\"value\":2.5}");
        rule.setSeverity(ThresholdConfig.Severity.CRITICAL);
        rule.setEnabled(true);
        thRepo.save(rule);
    }

    // ── 报警生成 ──────────────────────────────────────

    @Test
    @Order(1)
    void alarmGenerated_whenThresholdExceeded() {
        UploadRequest req = new UploadRequest();
        req.setDeviceCode("LC0101000100001");
        req.setProjectId(PROJECT);
        req.setDataPoints(Map.of("water_level", 3.5, "air_height", 0.3, "battery", 90.0));

        UploadResult result = uploadService.upload(req);
        assertTrue(result.isValidated());
        assertEquals(1, result.getThresholdsMatched());
        assertEquals(1, result.getAlarms().size());
        assertEquals("CRITICAL", result.getAlarms().get(0).getSeverity());
        assertEquals(3.5, result.getAlarms().get(0).getTriggerValue());
        assertEquals("ACTIVE", result.getAlarms().get(0).getStatus());

        testAlarmNo = result.getAlarms().get(0).getAlarmNo();
        assertNotNull(testAlarmNo);
    }

    // ── 手动确认 ──────────────────────────────────────

    @Test
    @Order(2)
    void alarmAcknowledged_manually() {
        assertNotNull(testAlarmNo, "Previous test should have created an alarm");

        var response = alarmService.acknowledge(testAlarmNo, "test-user");
        assertEquals("ACKNOWLEDGED", response.getStatus());
        assertNotNull(response.getCreatedAt());
    }

    // ── 手动解决 ──────────────────────────────────────

    @Test
    @Order(3)
    void alarmResolved_manually() {
        assertNotNull(testAlarmNo);

        var response = alarmService.resolve(testAlarmNo);
        assertEquals("RESOLVED", response.getStatus());
        assertNotNull(response.getResolvedAt());
    }

    // ── 自动恢复 ──────────────────────────────────────

    @Test
    @Order(10)
    void alarmAutoRecovered_whenMetricReturnsToNormal() {
        // First: trigger an alarm
        UploadRequest trigger = new UploadRequest();
        trigger.setDeviceCode("LC0101000100002");
        trigger.setProjectId(PROJECT);
        trigger.setDataPoints(Map.of("water_level", 4.0, "air_height", 0.2, "battery", 85.0));

        UploadResult triggerResult = uploadService.upload(trigger);
        assertTrue(triggerResult.isValidated());
        assertEquals(1, triggerResult.getThresholdsMatched());
        assertEquals("ACTIVE", triggerResult.getAlarms().get(0).getStatus());

        String activeAlarmNo = triggerResult.getAlarms().get(0).getAlarmNo();

        // Verify it's ACTIVE in DB
        AlarmRecord activeAlarm = alarmRepo.findByAlarmNo(activeAlarmNo).orElseThrow();
        assertEquals(AlarmRecord.AlarmStatus.ACTIVE, activeAlarm.getStatus());

        // Now: upload normal data → should auto-recover
        UploadRequest normal = new UploadRequest();
        normal.setDeviceCode("LC0101000100002");
        normal.setProjectId(PROJECT);
        normal.setDataPoints(Map.of("water_level", 1.0, "air_height", 1.5, "battery", 88.0));

        UploadResult normalResult = uploadService.upload(normal);
        assertTrue(normalResult.isValidated());
        assertEquals(0, normalResult.getThresholdsMatched());

        // Verify the alarm was auto-recovered
        AlarmRecord recoveredAlarm = alarmRepo.findByAlarmNo(activeAlarmNo).orElseThrow();
        assertEquals(AlarmRecord.AlarmStatus.RECOVERED, recoveredAlarm.getStatus());
        assertNotNull(recoveredAlarm.getResolvedAt());
    }

    // ── 统计 API ──────────────────────────────────────

    @Test
    @Order(20)
    void alarmStats_summaryReturnsCorrectData() {
        Map<String, Object> summary = alarmService.getSummary();

        assertNotNull(summary.get("total"));
        assertTrue((Long) summary.get("total") >= 2, "Should have at least 2 alarms from tests");

        @SuppressWarnings("unchecked")
        Map<String, Long> bySeverity = (Map<String, Long>) summary.get("bySeverity");
        assertNotNull(bySeverity);
        assertTrue(bySeverity.containsKey("CRITICAL"), "Should have CRITICAL severity");

        @SuppressWarnings("unchecked")
        Map<String, Long> byStatus = (Map<String, Long>) summary.get("byStatus");
        assertNotNull(byStatus);
        // Should have RESOLVED and RECOVERED from tests
        assertTrue(byStatus.containsKey("RESOLVED") || byStatus.containsKey("RECOVERED"));
    }

    @Test
    @Order(21)
    void alarmStats_trendReturnsData() {
        List<Map<String, Object>> trend = alarmService.getTrend(7);
        assertNotNull(trend);
        // May or may not have data depending on DB state
    }

    @Test
    @Order(22)
    void alarmStats_byDeviceTypeReturnsData() {
        List<Map<String, Object>> dist = alarmService.getByDeviceType();
        assertNotNull(dist);
        // Should have LC type from our tests
        boolean hasLC = dist.stream().anyMatch(d -> "LC".equals(d.get("deviceType")));
        assertTrue(hasLC, "Should have LC device type alarms");
    }

    @Test
    @Order(23)
    void alarmStats_topDevicesReturnsData() {
        List<Map<String, Object>> top = alarmService.getTopDevices(5);
        assertNotNull(top);
        assertTrue(top.size() <= 5);
        if (!top.isEmpty()) {
            assertTrue((Long) top.get(0).get("alarmCount") >= 1);
        }
    }

    // ── AES-256-GCM 加密 ──────────────────────────────────────

    @Test
    @Order(30)
    void aesEncryption_roundTrip_succeeds() {
        String plaintext = "{\"test\":\"value\",\"num\":42}";
        String aesKey = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        // Fill with known key
        byte[] keyBytes = new byte[32];
        for (int i = 0; i < 32; i++) keyBytes[i] = (byte) i;
        aesKey = java.util.Base64.getEncoder().encodeToString(keyBytes);

        String encrypted = pushService.encryptPayload(plaintext, aesKey);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = pushService.decryptPayload(encrypted, aesKey);
        assertEquals(plaintext, decrypted);
    }

    @Test
    @Order(31)
    void aesEncryption_wrongKey_throws() {
        String plaintext = "{\"test\":\"value\"}";
        byte[] key1 = new byte[32];
        byte[] key2 = new byte[32];
        key2[0] = 1; // Different key
        String aesKey1 = java.util.Base64.getEncoder().encodeToString(key1);
        String aesKey2 = java.util.Base64.getEncoder().encodeToString(key2);

        String encrypted = pushService.encryptPayload(plaintext, aesKey1);

        assertThrows(RuntimeException.class, () -> {
            pushService.decryptPayload(encrypted, aesKey2);
        }, "Decryption with wrong key should fail");
    }

    @Test
    @Order(32)
    void aesEncryption_envelopeContainsEncryptedField() {
        Map<String, Object> payload = Map.of("test", "encryption", "num", 123);
        byte[] keyBytes = new byte[32];
        String aesKey = java.util.Base64.getEncoder().encodeToString(keyBytes);

        Map<String, Object> envelope = pushService.buildEncryptedEnvelope(
            "ALERT", payload, "hmac-secret", aesKey);

        assertEquals("1.0", envelope.get("version"));
        assertEquals("ALERT", envelope.get("type"));
        assertEquals(true, envelope.get("encrypted"));
        assertNotNull(envelope.get("signature"));
        assertNotNull(envelope.get("payload"));

        // Payload should be Base64 encoded encrypted data, not JSON
        String payloadStr = (String) envelope.get("payload");
        assertFalse(payloadStr.startsWith("{"), "Encrypted payload should not start with {");

        // Decrypt and verify
        String decrypted = pushService.decryptPayload(payloadStr, aesKey);
        assertTrue(decrypted.contains("test"), "Decrypted payload should contain original data");
    }
}
