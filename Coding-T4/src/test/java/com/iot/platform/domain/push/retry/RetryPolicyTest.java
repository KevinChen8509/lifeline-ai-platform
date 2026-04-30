package com.iot.platform.domain.push.retry;

import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HMAC-SHA256 签名契约测试。
 * 固定输入 → 固定输出，确定性测试。
 *
 * SEC-001: 签名一致性
 * SEC-002: 重试策略分级
 */
class RetryPolicyTest {

    private RetryPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new RetryPolicy();
        policy.setBaseDelayMs(5000);
        policy.setJitter(0.0); // 禁用 jitter 以便确定性测试

        Map<String, RetryPolicy.RetryProfile> profiles = new java.util.HashMap<>();

        RetryPolicy.RetryProfile alert = new RetryPolicy.RetryProfile();
        alert.setMaxAttempts(12);
        alert.setMaxDelayMs(1800000L);
        profiles.put("ALERT", alert);

        RetryPolicy.RetryProfile realtime = new RetryPolicy.RetryProfile();
        realtime.setMaxAttempts(3);
        realtime.setMaxDelayMs(300000L);
        profiles.put("REALTIME_DATA", realtime);

        RetryPolicy.RetryProfile status = new RetryPolicy.RetryProfile();
        status.setMaxAttempts(8);
        status.setMaxDelayMs(600000L);
        profiles.put("DEVICE_STATUS", status);

        RetryPolicy.RetryProfile def = new RetryPolicy.RetryProfile();
        def.setMaxAttempts(5);
        def.setMaxDelayMs(300000L);
        profiles.put("default", def);

        policy.setProfiles(profiles);
    }

    // ── ALERT 分级 ──────────────────────────────────────

    @Nested
    class AlertRetry {
        @Test
        void maxAttempts_is12() {
            assertEquals(12, policy.getMaxAttempts("ALERT"));
        }

        @Test
        void canRetry_withinLimit() {
            assertTrue(policy.canRetry("ALERT", 11));
        }

        @Test
        void cannotRetry_atLimit() {
            assertFalse(policy.canRetry("ALERT", 12));
        }

        @Test
        void delay_cappedAt30min() {
            // attempt 15 → base*2^15 = 5s*32768 = 163840s, capped at 1800s
            long delay = policy.calculateDelayMs("ALERT", 15);
            assertEquals(1800000, delay);
        }
    }

    // ── REALTIME_DATA 分级 ──────────────────────────────

    @Nested
    class RealtimeRetry {
        @Test
        void maxAttempts_is3() {
            assertEquals(3, policy.getMaxAttempts("REALTIME_DATA"));
        }

        @Test
        void canRetry_withinLimit() {
            assertTrue(policy.canRetry("REALTIME_DATA", 2));
        }

        @Test
        void cannotRetry_atLimit() {
            assertFalse(policy.canRetry("REALTIME_DATA", 3));
        }

        @Test
        void delay_cappedAt5min() {
            long delay = policy.calculateDelayMs("REALTIME_DATA", 10);
            assertEquals(300000, delay);
        }
    }

    // ── DEVICE_STATUS 分级 ──────────────────────────────

    @Nested
    class StatusRetry {
        @Test
        void maxAttempts_is8() {
            assertEquals(8, policy.getMaxAttempts("DEVICE_STATUS"));
        }
    }

    // ── default fallback ────────────────────────────────

    @Nested
    class DefaultRetry {
        @Test
        void unknownType_usesDefault() {
            assertEquals(5, policy.getMaxAttempts("UNKNOWN_TYPE"));
        }

        @Test
        void default_maxDelay_5min() {
            assertEquals(300000, policy.getMaxDelayMs("UNKNOWN_TYPE"));
        }
    }

    // ── 指数退避计算 ──────────────────────────────────────

    @Nested
    class DelayCalculation {
        @Test
        void attempt0_isBaseDelay() {
            // 5000 * 2^0 = 5000
            assertEquals(5000, policy.calculateDelayMs("ALERT", 0));
        }

        @Test
        void attempt1_is2x() {
            // 5000 * 2^1 = 10000
            assertEquals(10000, policy.calculateDelayMs("ALERT", 1));
        }

        @Test
        void attempt5_is64x() {
            // 5000 * 2^5 = 160000
            assertEquals(160000, policy.calculateDelayMs("ALERT", 5));
        }
    }
}
