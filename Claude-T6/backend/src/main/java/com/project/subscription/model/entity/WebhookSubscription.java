package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "webhook_subscription")
public class WebhookSubscription extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "endpoint_id", nullable = false)
    private Long endpointId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "subscription_type", nullable = false)
    private Short subscriptionType = 0; // 0=设备级 1=设备类型级 2=分组级

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "data_point_ids", columnDefinition = "JSON")
    private String dataPointIds;

    @Column(nullable = false)
    private Short status = 0; // 0=启用 1=暂停 2=已删除

    @Column(name = "max_retries", nullable = false)
    private Integer maxRetries = 5;

    @Column(name = "retry_interval_seconds", nullable = false)
    private Integer retryIntervalSeconds = 10;

    public boolean isActive() {
        return status == 0;
    }
}
