package com.lifeline.modules.ai_model.entity;

import com.lifeline.modules.ai_model.entity.enums.DeploymentStatus;
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
@Table(name = "model_deployments")
@EntityListeners(AuditingEntityListener.class)
public class ModelDeployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column(name = "target_version", nullable = false, length = 20)
    private String targetVersion;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeploymentStatus status = DeploymentStatus.pending;

    @Column(name = "total_devices")
    private Integer totalDevices = 0;

    @Column(name = "success_count")
    private Integer successCount = 0;

    @Column(name = "failed_count")
    private Integer failedCount = 0;

    @Column(name = "in_progress_count")
    private Integer inProgressCount = 0;

    @Column(name = "pending_count")
    private Integer pendingCount = 0;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
