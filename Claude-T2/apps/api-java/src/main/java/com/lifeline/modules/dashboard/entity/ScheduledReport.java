package com.lifeline.modules.dashboard.entity;

import com.lifeline.modules.dashboard.entity.enums.ReportType;
import com.lifeline.modules.dashboard.entity.enums.ScheduleStatus;
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
@Table(name = "scheduled_reports")
@EntityListeners(AuditingEntityListener.class)
public class ScheduledReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReportType type;

    @Column(nullable = false, length = 50)
    private String cron;

    @Column(name = "project_ids", columnDefinition = "jsonb")
    private String projectIds;

    @Column(columnDefinition = "jsonb")
    private String recipients;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ScheduleStatus status = ScheduleStatus.ACTIVE;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
