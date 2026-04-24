package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "webhook_delivery_log")
public class WebhookDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_id", nullable = false)
    private Long configId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(nullable = false, length = 64)
    private String event;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 5;

    @Column(name = "response_code")
    private Integer responseCode;

    @Column(name = "response_body", length = 2048)
    private String responseBody;

    @Column(name = "error_msg", length = 1024)
    private String errorMsg;

    @Column(length = 32)
    private String source;

    @Column(name = "storm_guard_degraded", nullable = false)
    private Boolean stormGuardDegraded = false;

    @Column(name = "rule_match_skipped", nullable = false)
    private Boolean ruleMatchSkipped = false;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
