package com.project.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 推送疲劳控制 — 防止短时间内对同一端点发送过多推送。
 * Redis 不可用时降级到内存滑动窗口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StormGuard {

    private final StringRedisTemplate redisTemplate;

    private static final String RATE_PREFIX = "storm:rate:";
    private static final int MAX_RATE_PER_MINUTE = 100;
    private static final int MAX_RATE_PER_TEN_SECONDS = 30;

    // 内存降级
    private final ConcurrentHashMap<Long, ConcurrentLinkedDeque<Long>> memoryWindows = new ConcurrentHashMap<>();

    public boolean allowPush(Long endpointId) {
        String key = RATE_PREFIX + endpointId;
        try {
            String count = redisTemplate.opsForValue().get(key);
            if (count != null && Integer.parseInt(count) >= MAX_RATE_PER_MINUTE) {
                log.warn("StormGuard 限流: endpointId={}, 当前QPS超限", endpointId);
                return false;
            }
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, java.time.Duration.ofMinutes(1));
            return true;
        } catch (Exception e) {
            log.warn("StormGuard Redis 不可用，降级到内存限流: {}", e.getMessage());
            return allowPushMemory(endpointId);
        }
    }

    private boolean allowPushMemory(Long endpointId) {
        ConcurrentLinkedDeque<Long> window = memoryWindows.computeIfAbsent(endpointId,
            id -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000;

        // 清理过期记录
        while (!window.isEmpty() && window.peekFirst() < cutoff) {
            window.pollFirst();
        }

        if (window.size() >= MAX_RATE_PER_MINUTE) {
            return false;
        }

        window.addLast(now);
        return true;
    }

    public boolean isDegraded() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
