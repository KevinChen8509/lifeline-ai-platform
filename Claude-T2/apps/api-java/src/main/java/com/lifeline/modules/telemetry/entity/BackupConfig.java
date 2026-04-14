package com.lifeline.modules.telemetry.entity;

import com.lifeline.modules.telemetry.entity.enums.BackupType;
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
@Table(name = "backup_configs")
@EntityListeners(AuditingEntityListener.class)
public class BackupConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BackupType type;

    @Column(length = 50)
    private String schedule = "0 2 * * *";

    @Column(name = "retention_days")
    private Integer retentionDays = 30;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Column(name = "is_enabled")
    private Boolean isEnabled = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
