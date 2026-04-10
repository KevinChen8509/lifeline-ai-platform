package com.lifeline.modules.alert.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "alerts")
@EntityListeners(AuditingEntityListener.class)
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "project_id")
    private String projectId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AlertType type;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AlertLevel level;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AlertStatus status = AlertStatus.pending;

    @Column(name = "analysis_result_id")
    private String analysisResultId;

    @Column(name = "analysis_data", columnDefinition = "jsonb")
    private String analysisData;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private String acknowledgedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(columnDefinition = "text")
    private String resolution;

    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    @Column(name = "is_escalated")
    private Boolean isEscalated = false;

    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
