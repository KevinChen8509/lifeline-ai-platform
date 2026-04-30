package com.iot.platform.domain.push.scheduler;

import com.iot.platform.domain.push.circuitbreaker.CircuitBreakerRegistry;
import com.iot.platform.domain.push.entity.PushTask;
import com.iot.platform.domain.push.repository.PushTaskRepository;
import com.iot.platform.domain.push.retry.RetryPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RetryScheduler {

    private final PushTaskRepository taskRepository;
    private final RetryPolicy retryPolicy;
    private final CircuitBreakerRegistry cbRegistry;

    @Value("${push.scheduler.batch-size:50}")
    private int batchSize;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(5))
        .build();

    /** 注册一个推送任务的延迟重试 */
    public void scheduleRetry(PushTask task, long delayMs) {
        executor.schedule(() -> executeTask(task), delayMs, TimeUnit.MILLISECONDS);
        log.debug("Scheduled retry for task {} in {}ms (attempt {})", task.getId(), delayMs, task.getRetryCount());
    }

    /** 立即执行推送任务 */
    public void executeNow(PushTask task) {
        executor.submit(() -> executeTask(task));
    }

    /** Layer 2: 定时扫表兜底 */
    @Scheduled(fixedDelayString = "${push.scheduler.poll-interval-ms:30000}")
    public void pollScheduledTasks() {
        List<String> statuses = List.of("PENDING", "RETRY_SCHEDULED", "IN_MEMORY_RETRY");
        List<PushTask> tasks = taskRepository.findScheduledTasks(statuses, Instant.now(), PageRequest.of(0, batchSize));
        for (PushTask task : tasks) {
            executeNow(task);
        }
        if (!tasks.isEmpty()) {
            log.debug("Poll picked up {} tasks", tasks.size());
        }
    }

    private void executeTask(PushTask task) {
        // 刷新实体
        PushTask fresh = taskRepository.findById(task.getId()).orElse(null);
        if (fresh == null || "SUCCESS".equals(fresh.getStatus()) || "DEAD_LETTER".equals(fresh.getStatus())) {
            return;
        }

        // 检查断路器
        var breaker = cbRegistry.getOrCreate(fresh.getEndpointUrl());
        if (!breaker.allowRequest()) {
            fresh.setStatus("CIRCUIT_OPEN");
            fresh.setNextRetryAt(Instant.now().plusSeconds(60));
            taskRepository.save(fresh);
            log.debug("Task {} blocked by circuit breaker for {}", fresh.getId(), fresh.getEndpointUrl());
            return;
        }

        // 执行 HTTP 推送
        fresh.setStatus("IN_MEMORY_RETRY");
        fresh.setLastAttemptAt(Instant.now());
        taskRepository.save(fresh);

        boolean success = doHttpPost(fresh.getEndpointUrl(), fresh.getPayload());

        if (success) {
            breaker.recordSuccess();
            fresh.setStatus("SUCCESS");
            fresh.setNextRetryAt(null);
            taskRepository.save(fresh);
            log.info("Push task {} succeeded on attempt {}", fresh.getId(), fresh.getRetryCount());
        } else {
            breaker.recordFailure();
            fresh.setRetryCount(fresh.getRetryCount() + 1);

            String dataType = fresh.getDataType();
            if (retryPolicy.canRetry(dataType, fresh.getRetryCount())) {
                long delay = retryPolicy.calculateDelayMs(dataType, fresh.getRetryCount());
                fresh.setStatus("RETRY_SCHEDULED");
                fresh.setNextRetryAt(Instant.now().plusMillis(delay));
                taskRepository.save(fresh);
                scheduleRetry(fresh, delay);
                log.debug("Push task {} failed, retry {} scheduled in {}ms", fresh.getId(), fresh.getRetryCount(), delay);
            } else {
                fresh.setStatus("DEAD_LETTER");
                fresh.setNextRetryAt(null);
                taskRepository.save(fresh);
                log.warn("Push task {} exhausted retries, moved to DEAD_LETTER", fresh.getId());
            }
        }
    }

    private boolean doHttpPost(String url, String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(java.time.Duration.ofSeconds(5))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("HTTP POST to {} failed: {}", url, e.getMessage());
            return false;
        }
    }
}
