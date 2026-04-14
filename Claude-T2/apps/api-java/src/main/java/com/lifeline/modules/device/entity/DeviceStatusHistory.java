package com.lifeline.modules.device.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "device_status_history")
@EntityListeners(AuditingEntityListener.class)
public class DeviceStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "from_status", length = 20)
    @Enumerated(EnumType.STRING)
    private DeviceStatus fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeviceStatus toStatus;

    @Column(length = 255)
    private String reason;

    @Column(name = "operator_id", length = 36)
    private String operatorId;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
