package com.project.subscription.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import java.util.concurrent.Semaphore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EndpointHealthService {

    private final EndpointIsolationRegistry isolationRegistry;
    private final WebhookEndpointService endpointService;

    public Map<String, Object> getEndpointHealth(Long endpointId) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("endpointId", endpointId);

        boolean cbOpen = isolationRegistry.isCircuitBreakerOpen(endpointId);
        health.put("circuitBreakerOpen", cbOpen);

        CircuitBreaker cb = isolationRegistry.getCircuitBreaker(endpointId);
        var metrics = cb.getMetrics();
        health.put("failureRate", metrics.getFailureRate());
        health.put("slowCallRate", metrics.getSlowCallRate());
        health.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
        health.put("failedCalls", metrics.getNumberOfFailedCalls());
        health.put("successfulCalls", metrics.getNumberOfSuccessfulCalls());

        Semaphore sem = isolationRegistry.getSemaphore(endpointId);
        health.put("availablePermits", sem.availablePermits());

        return health;
    }
}
