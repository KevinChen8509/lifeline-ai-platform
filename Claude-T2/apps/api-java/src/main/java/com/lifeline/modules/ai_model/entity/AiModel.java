package com.lifeline.modules.ai_model.entity;

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
@Table(name = "ai_models")
@EntityListeners(AuditingEntityListener.class)
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AiModelType type;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_size")
    private Integer fileSize;

    @Column(length = 128)
    private String checksum;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AiModelStatus status = AiModelStatus.draft;

    @Column(columnDefinition = "jsonb")
    private String specs;

    @Column(name = "applicable_device_types", columnDefinition = "text")
    private String applicableDeviceTypes;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
