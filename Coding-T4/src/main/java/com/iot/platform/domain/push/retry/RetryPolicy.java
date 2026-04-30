package com.iot.platform.domain.push.retry;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@ConfigurationProperties(prefix = "push.retry")
public class RetryPolicy {

    private long baseDelayMs = 5000;
    private double jitter = 0.25;

    /** 按数据类型的重试配置 profile */
    private Map<String, RetryProfile> profiles = new java.util.HashMap<>();

    /** 缓存解析后的 profile */
    private final Map<String, RetryProfile> resolvedProfiles = new ConcurrentHashMap<>();

    public long calculateDelayMs(String dataType, int attemptCount) {
        RetryProfile profile = getProfile(dataType);
        long base = (long) (baseDelayMs * Math.pow(2, attemptCount));
        long capped = Math.min(base, profile.getMaxDelayMs());
        long result = capped;
        if (jitter > 0.0) {
            double jitterFactor = 1.0 + ThreadLocalRandom.current().nextDouble(-jitter, jitter);
            result = (long) (capped * jitterFactor);
        }
        return Math.max(result, 1000);
    }

    public boolean canRetry(String dataType, int currentRetries) {
        return currentRetries < getProfile(dataType).getMaxAttempts();
    }

    public int getMaxAttempts(String dataType) {
        return getProfile(dataType).getMaxAttempts();
    }

    public long getMaxDelayMs(String dataType) {
        return getProfile(dataType).getMaxDelayMs();
    }

    private RetryProfile getProfile(String dataType) {
        return resolvedProfiles.computeIfAbsent(dataType, dt -> {
            RetryProfile p = profiles.get(dt);
            if (p != null) return p;
            RetryProfile def = profiles.get("default");
            if (def != null) return def;
            log.warn("No retry profile for {} and no default, using hardcoded fallback", dt);
            RetryProfile fallback = new RetryProfile();
            fallback.setMaxAttempts(5);
            fallback.setMaxDelayMs(300000);
            return fallback;
        });
    }

    // Backward compat
    public long calculateDelayMs(int attemptCount) {
        return calculateDelayMs("default", attemptCount);
    }

    public boolean canRetry(int currentRetries) {
        return canRetry("default", currentRetries);
    }

    // Getters / Setters
    public long getBaseDelayMs() { return baseDelayMs; }
    public void setBaseDelayMs(long baseDelayMs) { this.baseDelayMs = baseDelayMs; }

    public double getJitter() { return jitter; }
    public void setJitter(double jitter) { this.jitter = jitter; }

    public Map<String, RetryProfile> getProfiles() { return profiles; }
    public void setProfiles(Map<String, RetryProfile> profiles) { this.profiles = profiles; }

    @Data
    public static class RetryProfile {
        private int maxAttempts = 5;
        private long maxDelayMs = 300000;
    }
}
