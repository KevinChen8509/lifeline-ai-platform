package com.lifeline.modules.alert.entity;

import com.lifeline.modules.alert.entity.enums.WorkOrderStatus;
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
@Table(name = "alert_work_orders")
@EntityListeners(AuditingEntityListener.class)
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private String id;

    @Column(name = "work_order_no", nullable = false, unique = true, length = 30)
    private String workOrderNo;

    @Column(name = "alert_id", nullable = false)
    private String alertId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "assignee_id", length = 36)
    private String assigneeId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private WorkOrderStatus status = WorkOrderStatus.pending;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AlertLevel priority = AlertLevel.medium;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
