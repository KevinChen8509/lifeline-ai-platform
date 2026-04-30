package com.iot.platform.application;

import com.iot.platform.domain.push.circuitbreaker.CircuitBreakerRegistry;
import com.iot.platform.domain.push.entity.PushTask;
import com.iot.platform.domain.push.repository.PushTaskRepository;
import com.iot.platform.domain.push.retry.RetryPolicy;
import com.iot.platform.domain.push.scheduler.RetryScheduler;
import com.iot.platform.domain.push.throttle.PushThrottle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushOrchestrator {

    private final PushTaskRepository taskRepository;
    private final RetryScheduler retryScheduler;
    private final CircuitBreakerRegistry cbRegistry;
    private final PushThrottle throttle;
    private final RetryPolicy retryPolicy;

    /**
     * 统一推送入口。
     * 持久化 PushTask → 检查限流/断路器 → 交给 RetryScheduler 执行。
     */
    public CompletableFuture<PushResult> submit(String subscriptionId, String endpointUrl,
                                                  String dataType, Map<String, Object> envelope,
                                                  String envelopeJson) {
        PushTask task = PushTask.builder()
            .subscriptionId(subscriptionId)
            .endpointUrl(endpointUrl)
            .dataType(dataType)
            .payload(envelopeJson)
            .status("PENDING")
            .retryCount(0)
            .maxRetries(retryPolicy.getMaxAttempts(dataType))
            .priority(PushTask.priorityOf(dataType))
            .nextRetryAt(Instant.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        // 持久化（先落库再推送）
        task = taskRepository.save(task);
        final PushTask savedTask = task;
        log.debug("PushTask {} created: {} → {}", savedTask.getId(), dataType, endpointUrl);

        // 检查限流
        if (!throttle.allowPush(endpointUrl)) {
            savedTask.setStatus("RETRY_SCHEDULED");
            savedTask.setNextRetryAt(Instant.now().plusSeconds(60));
            savedTask.setLastError("Rate limited");
            taskRepository.save(savedTask);
            return CompletableFuture.completedFuture(
                new PushResult(savedTask.getId(), "RATE_LIMITED", "端点限流，已排队等待"));
        }

        // 检查断路器
        var breaker = cbRegistry.getOrCreate(endpointUrl);
        if (!breaker.allowRequest()) {
            savedTask.setStatus("CIRCUIT_OPEN");
            savedTask.setNextRetryAt(Instant.now().plusSeconds(60));
            savedTask.setLastError("Circuit breaker open");
            taskRepository.save(savedTask);
            return CompletableFuture.completedFuture(
                new PushResult(savedTask.getId(), "CIRCUIT_OPEN", "断路器开启，已排队等待"));
        }

        // 交给调度器立即执行
        retryScheduler.executeNow(savedTask);
        return CompletableFuture.completedFuture(
            new PushResult(savedTask.getId(), "SUBMITTED", "已提交推送"));
    }

    public record PushResult(Long taskId, String status, String message) {}
}
