package com.datafabric.dataservice.governance;

import java.time.Instant;

/**
 * 审计事件 - 每次数据访问留痕
 *
 * 目标落库：ClickHouse `analytics.audit_log`（见 clickhouse-init/02-audit-log.sql）
 * PoC 实现：{@link LoggingAuditLogger} 先用 SLF4J + 内存 ring buffer
 *
 * F9：requestId 关联 Agent 请求（X-Request-Id 头透传；普通 API 调用为 null）
 */
public record AuditEvent(
        Instant timestamp,
        String actor,
        String action,
        String resource,
        String riskLevel,
        String result,
        String requestId
) {
    public static AuditEvent of(String actor, String action, String resource,
                                String riskLevel, String result, String requestId) {
        return new AuditEvent(Instant.now(), actor, action, resource, riskLevel, result, requestId);
    }
}
