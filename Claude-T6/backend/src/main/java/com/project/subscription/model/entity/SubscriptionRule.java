package com.project.subscription.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "subscription_rule")
public class SubscriptionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "data_point_id")
    private Long dataPointId;

    @Column(name = "rule_type", nullable = false)
    private Short ruleType; // 0=阈值 1=变化率 2=离线检测

    @Column(name = "condition_json", nullable = false, columnDefinition = "JSON")
    private String conditionJson;

    @Column(name = "cooldown_seconds", nullable = false)
    private Integer cooldownSeconds = 300;

    @Column(nullable = false)
    private Short priority = 1; // 0=Info 1=Warning 2=Critical

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;
}
