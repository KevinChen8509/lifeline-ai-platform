package com.iot.platform.domain.alarm.entity;

import com.iot.platform.domain.device.enums.DeviceType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 报警记录实体。
 * 记录所有触发的阈值报警、变化率报警、离线报警及AI诊断结果。
 */
@Entity
@Table(name = "alarm_record",
       indexes = {
           @Index(name = "idx_device", columnList = "deviceCode"),
           @Index(name = "idx_status", columnList = "status"),
           @Index(name = "idx_created", columnList = "createdAt"),
           @Index(name = "idx_project_severity", columnList = "projectId,severity")
       })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AlarmRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alarm_no", nullable = false, unique = true, length = 32)
    private String alarmNo;

    @Column(name = "project_id", nullable = false, length = 32)
    private String projectId;

    @Column(name = "device_code", nullable = false, length = 15)
    private String deviceCode;

    @Column(name = "device_name", length = 64)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 16)
    private DeviceType deviceType;

    @Column(name = "domain", nullable = false, length = 16)
    private String domain;

    @Column(name = "metric_key", nullable = false, length = 32)
    private String metricKey;

    @Column(name = "metric_name", length = 32)
    private String metricName;

    /** THRESHOLD / RATE_OF_CHANGE / OFFLINE_DETECTION */
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 24)
    private RuleType ruleType;

    /** INFO / WARNING / CRITICAL */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private Severity severity;

    /** 触发值 */
    @Column(name = "trigger_value")
    private Double triggerValue;

    /** 阈值 */
    @Column(name = "threshold_value")
    private Double thresholdValue;

    /** 比较运算符：GT/LT/GTE/LTE/EQ/NEQ */
    @Column(name = "operator", length = 4)
    private String operator;

    /** 规则配置JSON */
    @Column(name = "rule_config", columnDefinition = "TEXT")
    private String ruleConfig;

    /** AI诊断类型：OVERFLOW/SILTATION/MIXED_CONNECTION/FULL_PIPE/NONE */
    @Column(name = "ai_diagnosis", length = 24)
    private String aiDiagnosis;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "ai_detail", columnDefinition = "TEXT")
    private String aiDetail;

    /** PENDING/ACKNOWLEDGED/PROCESSING/RESOLVED/IGNORED */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AlarmStatus status;

    @Column(name = "threshold_config_id")
    private Long thresholdConfigId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by", length = 32)
    private String acknowledgedBy;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        if (status == null) status = AlarmStatus.ACTIVE;
        if (aiDiagnosis == null) aiDiagnosis = "NONE";
    }

    public enum RuleType { THRESHOLD, RATE_OF_CHANGE, OFFLINE_DETECTION }
    public enum Severity { INFO, WARNING, CRITICAL }
    public enum AlarmStatus { ACTIVE, ACKNOWLEDGED, PROCESSING, RESOLVED, RECOVERED, IGNORED }
}
