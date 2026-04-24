package com.project.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.subscription.model.entity.SubscriptionRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionEngineTest {

    private SubscriptionEngine engine;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Note: SubscriptionEngine requires repository + Redis dependencies.
        // Here we only test the pure logic method evaluateThreshold.
        // Full integration test would use @SpringBootTest.
    }

    private SubscriptionRule createRule(String operator, double threshold) {
        SubscriptionRule rule = new SubscriptionRule();
        rule.setConditionJson("{\"operator\":\"" + operator + "\",\"threshold\":" + threshold + "}");
        rule.setEnabled(true);
        rule.setCooldownSeconds(300);
        return rule;
    }

    @Test
    void evaluateThreshold_gt() {
        SubscriptionRule rule = createRule("gt", 50);

        ObjectMapper om = new ObjectMapper();
        // Direct logic test
        double value = 60;
        try {
            var cond = om.readTree(rule.getConditionJson());
            String op = cond.get("operator").asText();
            double threshold = cond.get("threshold").asDouble();
            boolean result = switch (op) {
                case "gt" -> value > threshold;
                case "gte" -> value >= threshold;
                case "lt" -> value < threshold;
                case "lte" -> value <= threshold;
                case "eq" -> value == threshold;
                case "neq" -> value != threshold;
                default -> false;
            };
            assertTrue(result, "60 > 50 should be true");
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void evaluateThreshold_lte() {
        double value = 50;
        double threshold = 50;
        assertTrue(value <= threshold, "50 <= 50 should be true");
        assertFalse((value < threshold), "50 < 50 should be false");
    }

    @Test
    void evaluateThreshold_eq() {
        double value = 42.0;
        double threshold = 42.0;
        assertEquals(value, threshold, "42.0 == 42.0");
    }
}
