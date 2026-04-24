package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "webhook_endpoint")
public class WebhookEndpoint extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 512)
    private String url;

    @Column(name = "secret_encrypted", nullable = false, length = 512)
    private String secretEncrypted;

    @Column(name = "secret_iv", nullable = false, length = 64)
    private String secretIv;

    @Column(name = "custom_headers", columnDefinition = "JSON")
    private String customHeaders;

    @Column(nullable = false)
    private Short status = 0;  // 0=启用 1=禁用

    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures = 0;

    @Column(name = "last_push_at")
    private LocalDateTime lastPushAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    public boolean isActive() {
        return status == 0;
    }
}
