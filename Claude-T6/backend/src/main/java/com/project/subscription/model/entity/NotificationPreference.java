package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "notification_preference", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "endpoint_failure_enabled", nullable = false)
    private Boolean endpointFailureEnabled = true;

    @Column(name = "endpoint_recovered_enabled", nullable = false)
    private Boolean endpointRecoveredEnabled = true;

    @Column(name = "push_dead_enabled", nullable = false)
    private Boolean pushDeadEnabled = true;

    @Column(name = "failure_frequency", nullable = false, length = 16)
    private String failureFrequency = "EACH";

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;
}
