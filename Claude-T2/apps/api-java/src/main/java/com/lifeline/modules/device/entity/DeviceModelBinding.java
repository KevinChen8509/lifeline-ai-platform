package com.lifeline.modules.device.entity;

import com.lifeline.modules.device.entity.enums.BindingStatus;
import com.lifeline.modules.ai_model.entity.AiModel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "device_model_bindings", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"device_id", "model_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class DeviceModelBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "model_id", nullable = false)
    private String modelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", insertable = false, updatable = false)
    private AiModel model;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BindingStatus status = BindingStatus.pending;

    @Column(name = "bound_version", length = 20)
    private String boundVersion;

    @Column(name = "bound_at", nullable = false)
    private LocalDateTime boundAt = LocalDateTime.now();

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(columnDefinition = "text")
    private String error;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
