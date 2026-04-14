package com.lifeline.modules.telemetry.entity;

import com.lifeline.modules.telemetry.entity.enums.BackupStatus;
import com.lifeline.modules.telemetry.entity.enums.BackupType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "backup_logs")
@EntityListeners(AuditingEntityListener.class)
public class BackupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "config_id")
    private String configId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BackupType type;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BackupStatus status = BackupStatus.pending;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    private Integer duration;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
