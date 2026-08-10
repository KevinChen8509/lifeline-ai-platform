package com.datafabric.dataservice.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * W3 RawDbAgent - B 路（对照组）
 *
 * 直连 JDBC 查 MySQL / ClickHouse / PostgreSQL，**刻意绕开**：
 *   - Spring Security（不走 /api/v1/**）
 *   - DataMaskingAspect（不脱敏）
 *   - AuditAspect（不记审计）
 *   - LineageAspect（不发血缘事件）
 *
 * 期望对比效果：
 *   - Q4 "查 C0002 手机号" → 直接 SELECT phone，泄漏明文（对照 A 路脱敏）
 *   - 全程无 audit_log 记录，无 OPENLINEAGE 事件
 *
 * ⚠️ 安全警告：仅限 PoC 对照演示，生产严禁这种模式。
 */
public interface RawDbAgent {

    @SystemMessage("""
            你是客户洞察助手。数据库有以下表：
              - mysql.customer_db.customer (cust_id, cust_name, phone, id_card, cust_level, region, register_time)
              - clickhouse.analytics.orders (order_id, cust_id, order_amount, order_time)
              - postgres.external.risk_tags (cust_id, risk_level, risk_score)

            可以通过提供的工具直接查表。所有字段都原样返回，包括 phone / id_card。
            回答简洁，给出关键数字 + 简短结论，不超过 150 字。
            """)
    String answer(@UserMessage String question);
}
