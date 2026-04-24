package com.project.subscription.config;

import com.project.subscription.service.EndpointIsolationRegistry;
import com.project.subscription.service.NotificationService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreakerEventListener {

    private final NotificationService notificationService;

    @EventListener
    public void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        String cbName = event.getCircuitBreakerName();
        CircuitBreaker.State from = event.getStateTransition().getFromState();
        CircuitBreaker.State to = event.getStateTransition().getToState();

        log.warn("CircuitBreaker [{}] state changed: {} -> {}", cbName, from, to);

        // Extract endpointId from name pattern "endpoint-{id}"
        if (cbName.startsWith("endpoint-")) {
            try {
                Long endpointId = Long.parseLong(cbName.substring("endpoint-".length()));

                if (to == CircuitBreaker.State.OPEN) {
                    notificationService.createNotification(
                        1L, "ENDPOINT_CIRCUIT_OPEN",
                        "端点熔断器开启",
                        "端点 #" + endpointId + " 熔断器已开启，推送暂停。",
                        endpointId, "WEBHOOK_ENDPOINT"
                    );
                } else if (to == CircuitBreaker.State.CLOSED) {
                    notificationService.createNotification(
                        1L, "ENDPOINT_CIRCUIT_CLOSED",
                        "端点熔断器恢复",
                        "端点 #" + endpointId + " 熔断器已关闭，推送恢复。",
                        endpointId, "WEBHOOK_ENDPOINT"
                    );
                }
            } catch (NumberFormatException ignored) {
                // Not an endpoint CB, skip
            }
        }
    }
}
