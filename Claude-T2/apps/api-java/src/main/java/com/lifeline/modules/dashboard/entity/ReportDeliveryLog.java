package com.lifeline.modules.dashboard.entity;

import com.lifeline.modules.dashboard.entity.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "report_delivery_logs")
@EntityListeners(AuditingEntityListener.class)
public class ReportDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "scheduled_report_id", nullable = false)
    private String scheduledReportId;

    @Column(name = "report_id", nullable = false)
    private String reportId;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column
    private Integer attempts = 0;

    @Column(columnDefinition = "text")
    private String error;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
