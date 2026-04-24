package com.project.subscription.model.dto;

import lombok.Data;

@Data
public class RuleDto {
    private Long id;
    private Long dataPointId;
    private Short ruleType;      // 0=阈值 1=变化率 2=离线检测
    private String conditionJson;
    private Integer cooldownSeconds = 300;
    private Short priority = 1;  // 0=Info 1=Warning 2=Critical
    private Boolean enabled = true;
}
