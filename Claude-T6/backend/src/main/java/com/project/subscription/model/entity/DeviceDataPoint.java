package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "device_data_point", uniqueConstraints = @UniqueConstraint(columnNames = {"device_id", "identifier"}))
public class DeviceDataPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(nullable = false, length = 64)
    private String identifier;

    @Column(name = "data_type", nullable = false, length = 16)
    private String dataType = "float";

    @Column(name = "last_value", length = 256)
    private String lastValue;

    @Column(name = "last_report_at")
    private LocalDateTime lastReportAt;

    @Column(nullable = false)
    private Short quality = 0;
}
