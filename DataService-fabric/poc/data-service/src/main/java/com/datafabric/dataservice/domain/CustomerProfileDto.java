package com.datafabric.dataservice.domain;

/**
 * 客户 360° 画像（跨源 JOIN 结果的对外契约）
 *
 * 字段对应 Cube schema 中 CustomerProfile cube 的 measures/dimensions。
 * phone / idCard 在 W2.3 由 DataMaskingAspect 根据 OpenMetadata 标签自动脱敏，
 * 当前版本原样返回（PoC）。
 */
public record CustomerProfileDto(
        String custId,
        String custName,
        String phone,
        String idCard,
        String customerLevel,
        String region,
        Long totalOrders,
        Double totalAmount,
        String riskLevel,
        Integer riskScore,
        String registerTime,
        String lastOrderTime
) {}
