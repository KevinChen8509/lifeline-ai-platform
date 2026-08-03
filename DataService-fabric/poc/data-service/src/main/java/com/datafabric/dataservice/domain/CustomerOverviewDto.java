package com.datafabric.dataservice.domain;

/**
 * 全局客户指标快照
 *
 * 对应 Cube schema 中 CustomerMetrics cube 的衍生指标。
 */
public record CustomerOverviewDto(
        Double arpu,
        Integer vipCustomerCount,
        Integer highRiskCustomerCount,
        Integer mediumRiskCustomerCount,
        Integer lowRiskCustomerCount
) {}
