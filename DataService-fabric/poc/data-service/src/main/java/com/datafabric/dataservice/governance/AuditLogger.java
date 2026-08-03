package com.datafabric.dataservice.governance;

/**
 * 审计日志接口
 *
 * 生产实现：写 ClickHouse `analytics.audit_log` 表（设计 spec Week 2 退出标准）
 * PoC 实现：{@link LoggingAuditLogger} SLF4J + 内存 ring buffer（用于 /api/v1/audit/recent 演示）
 */
public interface AuditLogger {

    void log(AuditEvent event);
}
