package com.lifeline.modules.dashboard.entity;

import com.lifeline.modules.dashboard.entity.enums.ReportStatus;
import com.lifeline.modules.dashboard.entity.enums.ReportType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "reports")
@EntityListeners(AuditingEntityListener.class)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReportType type;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.GENERATING;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(columnDefinition = "jsonb")
    private String data;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_size")
    private Integer fileSize;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "generated_by", nullable = false, length = 36)
    private String generatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
