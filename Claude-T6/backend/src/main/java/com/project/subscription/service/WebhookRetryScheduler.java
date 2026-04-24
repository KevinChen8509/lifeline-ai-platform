package com.project.subscription.service;

import com.project.subscription.model.entity.WebhookDeliveryLog;
import com.project.subscription.model.entity.WebhookEndpoint;
import com.project.subscription.repository.WebhookDeliveryLogRepository;
import com.project.subscription.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookRetryScheduler {

    private final WebhookDeliveryLogRepository logRepository;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookPushService pushService;
    private final StringRedisTemplate redisTemplate;

    @Value("${webhook.retry.max-attempts:5}")
    private int maxRetries;

    @Value("${webhook.retry.base-seconds:30}")
    private int baseSeconds;

    @Value("${webhook.retry.multiplier:2}")
    private int multiplier;

    private static final String LOCK_PREFIX = "retry:lock:";
    private static final int BATCH_SIZE = 10;
    private static final int MAX_PENDING = 100;

    /**
     * 每 30 秒扫描需要重试的推送记录
     */
    @Scheduled(fixedDelay = 30000)
    public void scanRetries() {
        List<String> retryStatuses = List.of("FAILED", "RETRYING");
        List<WebhookDeliveryLog> pending = logRepository.findPendingRetries(
            retryStatuses, LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

        if (pending.isEmpty()) return;
        log.info("扫描到 {} 条待重试记录", pending.size());

        int retried = 0;
        for (WebhookDeliveryLog dl : pending) {
            if (!tryAcquireLock(dl.getEventId())) continue;

            try {
                if (dl.getAttemptCount() >= dl.getMaxRetries()) {
                    // 超过最大重试次数 → DEAD
                    dl.setStatus("DEAD");
                    dl.setErrorMsg(truncate(dl.getErrorMsg() + " [已达最大重试次数]", 1024));
                    logRepository.save(dl);
                    log.warn("推送最终失败(DEAD): eventId={}, attempts={}", dl.getEventId(), dl.getAttemptCount());
                    continue;
                }

                // 重新推送
                dl.setAttemptCount(dl.getAttemptCount() + 1);
                dl.setStatus("RETRYING");
                dl.setSource("COMPENSATION");
                logRepository.save(dl);

                pushService.push(dl.getConfigId(), dl.getEventId(), dl.getEvent(), dl.getPayload());
                retried++;
            } catch (Exception e) {
                log.error("重试推送失败: eventId={}, error={}", dl.getEventId(), e.getMessage());
            } finally {
                releaseLock(dl.getEventId());
            }
        }

        if (retried > 0) {
            log.info("本轮重试 {} 条", retried);
        }
    }

    private boolean tryAcquireLock(String eventId) {
        String key = LOCK_PREFIX + eventId;
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", 60, TimeUnit.SECONDS);
        if (ok == null || !ok) {
            // Redis 不可用 → 降级，允许执行
            return true;
        }
        return true;
    }

    private void releaseLock(String eventId) {
        try {
            redisTemplate.delete(LOCK_PREFIX + eventId);
        } catch (Exception ignored) {}
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
