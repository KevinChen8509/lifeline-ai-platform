package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "device", uniqueConstraints = @UniqueConstraint(columnNames = "device_key"))
public class Device extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    @Column(name = "device_key", nullable = false, length = 64)
    private String deviceKey;

    @Column(nullable = false)
    private Short status = 0;

    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;

    @Column(columnDefinition = "JSON")
    private String tags;
}
