package com.lifeline.modules.alert.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "alert_status_history")
@EntityListeners(AuditingEntityListener.class)
public class AlertStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "alert_id", nullable = false)
    private String alertId;

    @Column(name = "old_status", length = 20)
    @Enumerated(EnumType.STRING)
    private AlertStatus oldStatus;

    @Column(name = "new_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AlertStatus newStatus;

    @Column(name = "operator_id", length = 36)
    private String operatorId;

    @Column(columnDefinition = "text")
    private String note;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
