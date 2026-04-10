package com.lifeline.modules.device.entity;

import com.lifeline.modules.project.entity.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "devices")
@EntityListeners(AuditingEntityListener.class)
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "serial_number", nullable = false, unique = true, length = 50)
    private String serialNumber;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(length = 100)
    private String model;

    @Column(length = 100)
    private String manufacturer;

    @Column(name = "project_id")
    private String projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    private Project project;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeviceStatus status = DeviceStatus.pending;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeviceSource source = DeviceSource.self_developed;

    @Column(columnDefinition = "jsonb")
    private String config;

    @Enumerated(EnumType.STRING)
    private DeviceProtocol protocol;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "last_online_at")
    private LocalDateTime lastOnlineAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
