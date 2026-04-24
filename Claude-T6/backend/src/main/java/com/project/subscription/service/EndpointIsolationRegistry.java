package com.project.subscription.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Slf4j
@Service
public class EndpointIsolationRegistry {

    private static final int MAX_CONCURRENT_PER_ENDPOINT = 10;

    private final ConcurrentHashMap<Long, Semaphore> semaphores = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry cbRegistry;

    public EndpointIsolationRegistry() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindowSize(10)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .build();
        this.cbRegistry = CircuitBreakerRegistry.of(cbConfig);
    }

    public Semaphore getSemaphore(Long endpointId) {
        return semaphores.computeIfAbsent(endpointId,
            id -> new Semaphore(MAX_CONCURRENT_PER_ENDPOINT));
    }

    public CircuitBreaker getCircuitBreaker(Long endpointId) {
        return cbRegistry.circuitBreaker("endpoint-" + endpointId);
    }

    public boolean isCircuitBreakerOpen(Long endpointId) {
        CircuitBreaker cb = cbRegistry.find("endpoint-" + endpointId).orElse(null);
        return cb != null && cb.getState() == CircuitBreaker.State.OPEN;
    }

    /**
     * SemaphoreGuard — AutoCloseable 封装确保信号量释放
     */
    public static class SemaphoreGuard implements AutoCloseable {
        private final Semaphore semaphore;
        private final boolean acquired;

        public SemaphoreGuard(Semaphore semaphore) {
            this.semaphore = semaphore;
            this.acquired = semaphore.tryAcquire();
        }

        public boolean isAcquired() { return acquired; }

        @Override
        public void close() {
            if (acquired) semaphore.release();
        }
    }
}
