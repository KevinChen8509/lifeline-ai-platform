package com.datafabric.dataservice.governance;

/**
 * 字段级治理策略 - 描述哪些字段需要脱敏 / 隐藏
 *
 * 真实环境从 OpenMetadata 标签读取（PII.Phone / PII.IdCard），
 * PoC 阶段由 {@link StubMetadataClient} 按角色生成。
 *
 * 标签与代码解耦：改 OpenMetadata 标签 → 改策略 → 改脱敏，
 * 全部不需要重新编译业务代码。
 */
public record FieldPolicy(
        boolean maskPhone,
        boolean maskIdCard,
        boolean hideRiskScore
) {
    public static FieldPolicy none() {
        return new FieldPolicy(false, false, false);
    }

    public static FieldPolicy all() {
        return new FieldPolicy(true, true, true);
    }
}
