package com.lifeline.modules.ai_model.entity;

import com.lifeline.modules.ai_model.entity.enums.DeviceDeploymentStatus;
import com.lifeline.modules.ai_model.entity.enums.FailureReason;
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
@Table(name = "device_deployments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"deployment_id", "device_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class DeviceDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "deployment_id", nullable = false)
    private String deploymentId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeviceDeploymentStatus status = DeviceDeploymentStatus.pending;

    @Column(name = "failure_reason", length = 30)
    @Enumerated(EnumType.STRING)
    private FailureReason failureReason;

    @Column(columnDefinition = "text")
    private String error;

    @Column
    private Integer progress = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
