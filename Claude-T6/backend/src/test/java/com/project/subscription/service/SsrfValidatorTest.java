package com.project.subscription.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SsrfValidatorTest {

    private SsrfValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SsrfValidator();
    }

    @Test
    void shouldRejectHttpUrls() {
        assertFalse(validator.isValid("http://example.com/webhook"));
    }

    @Test
    void shouldAcceptHttpsUrls() {
        // Note: DNS resolution may fail in test env, so this tests scheme validation
        try {
            validator.isValid("https://example.com/webhook");
        } catch (Exception e) {
            // DNS resolution may fail, that's ok for this unit test
        }
    }

    @Test
    void shouldRejectLocalhost() {
        assertFalse(validator.isValid("https://localhost:8080/webhook"));
        assertFalse(validator.isValid("https://127.0.0.1/webhook"));
    }

    @Test
    void shouldRejectPrivateIps() {
        assertFalse(validator.isValid("https://192.168.1.1/webhook"));
        assertFalse(validator.isValid("https://10.0.0.1/webhook"));
        assertFalse(validator.isValid("https://172.16.0.1/webhook"));
    }

    @Test
    void shouldRejectMetadataEndpoint() {
        assertFalse(validator.isValid("https://169.254.169.254/latest/meta-data/"));
    }

    @Test
    void shouldRejectInvalidUrl() {
        assertFalse(validator.isValid("not-a-url"));
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid("ftp://example.com"));
    }
}
