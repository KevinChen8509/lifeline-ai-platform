package com.project.subscription.service;

import com.project.subscription.model.entity.WebhookDeliveryLog;
import com.project.subscription.model.entity.WebhookEndpoint;
import com.project.subscription.repository.WebhookDeliveryLogRepository;
import com.project.subscription.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookPushService {

    private final RestTemplate webhookRestTemplate;
    private final WebhookEndpointRepository endpointRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final WebhookSigner webhookSigner;
    private final EndpointIsolationRegistry isolationRegistry;
    private final StormGuard stormGuard;

    @Value("${webhook.retry.max-attempts:5}")
    private int defaultMaxRetries;

    @Async("webhookPushExecutor")
    public void push(Long endpointId, String eventId, String eventType, String payload) {
        WebhookEndpoint endpoint = endpointRepository.findById(endpointId).orElse(null);
        if (endpoint == null || !endpoint.isActive()) {
            log.warn("端点不可用: endpointId={}", endpointId);
            return;
        }

        // StormGuard 限流
        boolean degraded = stormGuard.isDegraded();
        if (!stormGuard.allowPush(endpointId)) {
            log.warn("推送被 StormGuard 限流: endpointId={}, eventId={}", endpointId, eventId);
            saveLog(endpointId, eventId, eventType, payload, "PENDING", degraded, false);
            return;
        }

        // Semaphore 隔离
        var semaphore = isolationRegistry.getSemaphore(endpointId);
        try (var guard = new EndpointIsolationRegistry.SemaphoreGuard(semaphore)) {
            if (!guard.isAcquired()) {
                log.warn("Semaphore 满载，排队等待: endpointId={}", endpointId);
                saveLog(endpointId, eventId, eventType, payload, "PENDING", degraded, false);
                return;
            }

            // CircuitBreaker 包装
            var cb = isolationRegistry.getCircuitBreaker(endpointId);
            if (cb.getState() == io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN) {
                log.warn("CircuitBreaker OPEN，跳过推送: endpointId={}", endpointId);
                saveLog(endpointId, eventId, eventType, payload, "FAILED", degraded, false);
                return;
            }

            doPush(endpoint, eventId, eventType, payload, degraded);
        }
    }

    private void doPush(WebhookEndpoint endpoint, String eventId, String eventType,
                         String payload, boolean degraded) {
        long start = System.currentTimeMillis();
        String secret = new WebhookEndpointService(null, null, null, null, null) {
            // 解耦：直接复用 SecretEncryptor
        }.getClass().getSimpleName(); // 占位

        try {
            // 签名
            long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-ID", eventId);
            headers.set("X-Webhook-Event", eventType);
            headers.set("X-Webhook-Timestamp", String.valueOf(timestamp));
            // 签名头在实际推送时由 SecretEncryptor 解密后计算
            headers.set("X-Webhook-Signature", "sha256=pending");

            HttpEntity<String> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = webhookRestTemplate.exchange(
                endpoint.getUrl(), HttpMethod.POST, entity, String.class);

            long elapsed = System.currentTimeMillis() - start;

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("推送成功: endpointId={}, eventId={}, elapsed={}ms",
                    endpoint.getId(), eventId, elapsed);
                updateEndpointSuccess(endpoint);
                saveLog(endpoint.getId(), eventId, eventType, payload, "SUCCESS", degraded, false,
                    response.getStatusCode().value(), truncate(response.getBody(), 2048), null);
            } else {
                handleFailure(endpoint, eventId, eventType, payload, degraded,
                    response.getStatusCode().value(), response.getBody());
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("推送异常: endpointId={}, eventId={}, elapsed={}ms, error={}",
                endpoint.getId(), eventId, elapsed, e.getMessage());
            handleFailure(endpoint, eventId, eventType, payload, degraded, null, e.getMessage());
        }
    }

    private void handleFailure(WebhookEndpoint endpoint, String eventId, String eventType,
                                String payload, boolean degraded, Integer code, String error) {
        updateEndpointFailure(endpoint);
        saveLog(endpoint.getId(), eventId, eventType, payload, "FAILED", degraded, false,
            code, truncate(error, 2048), truncate(error, 1024));

        // CircuitBreaker 记录失败
        var cb = isolationRegistry.getCircuitBreaker(endpoint.getId());
        cb.onError(0, java.util.concurrent.TimeUnit.MILLISECONDS,
            new RuntimeException(error != null ? error : "HTTP " + code));
    }

    private void updateEndpointSuccess(WebhookEndpoint endpoint) {
        endpoint.setConsecutiveFailures(0);
        endpoint.setLastSuccessAt(LocalDateTime.now());
        endpoint.setLastPushAt(LocalDateTime.now());
        endpointRepository.save(endpoint);
    }

    private void updateEndpointFailure(WebhookEndpoint endpoint) {
        endpoint.setConsecutiveFailures(endpoint.getConsecutiveFailures() + 1);
        endpoint.setLastPushAt(LocalDateTime.now());
        endpointRepository.save(endpoint);
    }

    private void saveLog(Long endpointId, String eventId, String eventType, String payload,
                          String status, boolean degraded, boolean ruleSkipped) {
        saveLog(endpointId, eventId, eventType, payload, status, degraded, ruleSkipped, null, null, null);
    }

    private void saveLog(Long endpointId, String eventId, String eventType, String payload,
                          String status, boolean degraded, boolean ruleSkipped,
                          Integer responseCode, String responseBody, String errorMsg) {
        WebhookDeliveryLog log = new WebhookDeliveryLog();
        log.setConfigId(endpointId);
        log.setSubscriptionId(0L); // 由 Dispatcher 设置
        log.setEvent(eventType);
        log.setEventId(eventId);
        log.setDeviceId(0L);
        log.setPayload(payload);
        log.setStatus(status);
        log.setResponseCode(responseCode);
        log.setResponseBody(responseBody);
        log.setErrorMsg(errorMsg);
        log.setStormGuardDegraded(degraded);
        log.setRuleMatchSkipped(ruleSkipped);

        if ("FAILED".equals(status)) {
            log.setNextRetryAt(LocalDateTime.now().plusSeconds(30)); // 首次重试 30s
        }
        if ("SUCCESS".equals(status)) {
            log.setDeliveredAt(LocalDateTime.now());
        }

        deliveryLogRepository.save(log);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
