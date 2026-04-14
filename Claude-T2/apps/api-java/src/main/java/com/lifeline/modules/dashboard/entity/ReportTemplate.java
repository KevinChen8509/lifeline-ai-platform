package com.lifeline.modules.dashboard.entity;

import com.lifeline.modules.dashboard.entity.enums.ReportType;
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
@Table(name = "report_templates")
@EntityListeners(AuditingEntityListener.class)
public class ReportTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReportType type;

    @Column(columnDefinition = "jsonb")
    private String sections;

    @Column(name = "is_default")
    private Boolean isDefault = true;

    @Column(name = "project_id")
    private String projectId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
