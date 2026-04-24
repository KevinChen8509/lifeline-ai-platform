package com.project.subscription.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebhookSignerTest {

    private final WebhookSigner signer = new WebhookSigner();

    @Test
    void sign_shouldProduceDeterministicResult() {
        String secret = "whsec_test1234567890";
        long timestamp = 1700000000L;
        String payload = "{\"event\":\"test\"}";

        String sig1 = signer.sign(secret, timestamp, payload);
        String sig2 = signer.sign(secret, timestamp, payload);

        assertTrue(sig1.startsWith("sha256="));
        assertEquals(sig1, sig2, "Same inputs should produce same signature");
    }

    @Test
    void sign_shouldVaryWithDifferentPayloads() {
        String secret = "whsec_test";
        long ts = 1700000000L;

        String sig1 = signer.sign(secret, ts, "{\"a\":1}");
        String sig2 = signer.sign(secret, ts, "{\"a\":2}");

        assertNotEquals(sig1, sig2, "Different payloads should produce different signatures");
    }

    @Test
    void sign_shouldVaryWithDifferentTimestamps() {
        String secret = "whsec_test";

        String sig1 = signer.sign(secret, 1700000000L, "{\"a\":1}");
        String sig2 = signer.sign(secret, 1700000001L, "{\"a\":1}");

        assertNotEquals(sig1, sig2);
    }

    @Test
    void sign_shouldHandleEmptyPayload() {
        String sig = signer.sign("whsec_test", 1700000000L, "");
        assertTrue(sig.startsWith("sha256="));
        assertTrue(sig.length() > 10);
    }

    @Test
    void sign_shouldHandleUnicodePayload() {
        String sig = signer.sign("whsec_test", 1700000000L, "{\"msg\":\"中文测试🎉\"}");
        assertTrue(sig.startsWith("sha256="));
    }
}
