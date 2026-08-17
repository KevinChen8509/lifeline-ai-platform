package com.datafabric.dataservice.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * F5 流式版 RawDbAgent（B 路对照组）。
 *
 * 与 {@link RawDbAgent} 共享 system message + 工具集，仅返回类型不同：
 * 同步版返回 {@code String}，流式版返回 {@link TokenStream}。
 *
 * ⚠️ 安全警告：与同步版一致，仅限 PoC 对照演示，生产严禁。
 */
public interface RawDbStreamAgent {

    @SystemMessage("""
            你是客户洞察助手。数据库有以下表：
              - mysql.customer_db.customer (cust_id, cust_name, phone, id_card, cust_level, region, register_time)
              - clickhouse.analytics.orders (order_id, cust_id, order_amount, order_time)
              - postgres.external.risk_tags (cust_id, risk_level, risk_score)

            可以通过提供的工具直接查表。所有字段都原样返回，包括 phone / id_card。
            回答简洁，给出关键数字 + 简短结论，不超过 150 字。
            """)
    TokenStream answer(@UserMessage String question);
}
