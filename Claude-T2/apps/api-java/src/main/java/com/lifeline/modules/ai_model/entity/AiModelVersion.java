package com.lifeline.modules.ai_model.entity;

import com.lifeline.modules.ai_model.entity.enums.ModelVersionStatus;
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
@Table(name = "ai_model_versions")
@EntityListeners(AuditingEntityListener.class)
public class AiModelVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ModelVersionStatus status = ModelVersionStatus.draft;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_size")
    private Integer fileSize;

    @Column(length = 128)
    private String checksum;

    @Column(columnDefinition = "text")
    private String signature;

    @Column(name = "change_log", columnDefinition = "text")
    private String changeLog;

    @Column(columnDefinition = "jsonb")
    private String specs;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "published_by", length = 36)
    private String publishedBy;

    @Column(name = "is_current")
    private Boolean isCurrent = false;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
