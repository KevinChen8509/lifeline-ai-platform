package com.datafabric.dataservice.agent;

import com.datafabric.dataservice.config.RawDbProperties;
import com.datafabric.dataservice.observability.ToolCallLogger;
import com.zaxxer.hikari.HikariDataSource;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W3 RawDbAgent 工具集（B 路，对照组）
 *
 * 4 个 @Tool 直接通过 JDBC 查询三源。**没有治理**：
 *   - phone / id_card 原样返回（PII 明文）
 *   - 不写 audit_log
 *   - 不发 OpenLineage 血缘事件
 *
 * F2：运维级工具调用日志（ToolCallLogger，记行为不记数据），这不是治理——
 * 治理缺失的对照演示不受影响。
 *
 * F8：DriverManager 裸建连接 → HikariCP 池（RawDbPoolConfig）+ 瞬时故障重试
 * （JdbcRetry，2 次尝试 + 200ms 退避，只重试瞬时异常）。
 *
 * 设计要点：
 *   - PreparedStatement 防注入（PoC 仍守底线，不演示注入漏洞）
 *   - 跨源 JOIN 由 LangChain4j LLM 在工具调用层面组合（不依赖 Trino）
 */
@Component
@EnableConfigurationProperties(RawDbProperties.class)
public class RawDbTools {

    private static final Logger log = LoggerFactory.getLogger(RawDbTools.class);

    private final HikariDataSource mysqlPool;
    private final HikariDataSource clickhousePool;
    private final HikariDataSource postgresPool;
    private final ToolCallLogger toolCallLogger;

    public RawDbTools(HikariDataSource mysqlPool,
                      HikariDataSource clickhousePool,
                      HikariDataSource postgresPool,
                      ToolCallLogger toolCallLogger) {
        this.mysqlPool = mysqlPool;
        this.clickhousePool = clickhousePool;
        this.postgresPool = postgresPool;
        this.toolCallLogger = toolCallLogger;
    }

    @Tool("从 MySQL 直查客户基础信息（含手机号 / 身份证，原样返回）。")
    public String getCustomerRaw(@P("客户 ID，例如 C0001") String custId) {
        log.info("RawTool[getCustomerRaw] custId={}", custId);
        long t0 = System.currentTimeMillis();
        try {
            JdbcRetry.Outcome<QueryResult> out = JdbcRetry.withRetry(() -> {
                String sql = "SELECT cust_id, cust_name, phone, id_card, cust_level, region, register_time "
                        + "FROM customer WHERE cust_id = ?";
                try (Connection conn = mysqlPool.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, custId);
                    return collectRows(ps);
                }
            });
            record("getCustomerRaw", Map.of("custId", custId), t0, out, out.value().rowCount());
            return out.value().rendered();
        } catch (Exception e) {
            recordFailed("getCustomerRaw", Map.of("custId", custId), t0, e);
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    @Tool("从 MySQL 按 cust_level 过滤客户（返回手机号、身份证等明文）。")
    public String searchCustomersByLevelRaw(
            @P("客户等级，如 VIP3") String level,
            @P("返回条数上限") int limit) {
        log.info("RawTool[searchCustomersByLevelRaw] level={} limit={}", level, limit);
        long t0 = System.currentTimeMillis();
        try {
            JdbcRetry.Outcome<QueryResult> out = JdbcRetry.withRetry(() -> {
                String sql = "SELECT cust_id, cust_name, phone, id_card, cust_level, region "
                        + "FROM customer WHERE cust_level = ? LIMIT ?";
                try (Connection conn = mysqlPool.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, level);
                    ps.setInt(2, Math.max(1, Math.min(limit, 500)));
                    return collectRows(ps);
                }
            });
            record("searchCustomersByLevelRaw", Map.of("level", level, "limit", limit),
                    t0, out, out.value().rowCount());
            return out.value().rendered();
        } catch (Exception e) {
            recordFailed("searchCustomersByLevelRaw", Map.of("level", level, "limit", limit), t0, e);
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    @Tool("从 ClickHouse 查客户的订单总额与订单数。")
    public String getCustomerOrders(@P("客户 ID，例如 C0001") String custId) {
        log.info("RawTool[getCustomerOrders] custId={}", custId);
        long t0 = System.currentTimeMillis();
        try {
            JdbcRetry.Outcome<QueryResult> out = JdbcRetry.withRetry(() -> {
                String sql = "SELECT count() as total_orders, sum(order_amount) as total_amount "
                        + "FROM orders WHERE cust_id = ?";
                try (Connection conn = clickhousePool.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, custId);
                    return collectRows(ps);
                }
            });
            record("getCustomerOrders", Map.of("custId", custId), t0, out, out.value().rowCount());
            return out.value().rendered();
        } catch (Exception e) {
            recordFailed("getCustomerOrders", Map.of("custId", custId), t0, e);
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    @Tool("从 PostgreSQL 查所有高风险客户 ID 列表（按 cust_id 关联 MySQL 查明细）。")
    public String getHighRiskCustomerIds() {
        log.info("RawTool[getHighRiskCustomerIds]");
        long t0 = System.currentTimeMillis();
        try {
            JdbcRetry.Outcome<QueryResult> out = JdbcRetry.withRetry(() -> {
                String sql = "SELECT cust_id, risk_level, risk_score FROM risk_tags WHERE risk_level = 'high'";
                try (Connection conn = postgresPool.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    return collectRows(ps);
                }
            });
            record("getHighRiskCustomerIds", Map.of(), t0, out, out.value().rowCount());
            return out.value().rendered();
        } catch (Exception e) {
            recordFailed("getHighRiskCustomerIds", Map.of(), t0, e);
            return "ERROR: " + e.getClass().getSimpleName() + " / " + sanitize(e.getMessage());
        }
    }

    // ---- F2/F8 helpers ----

    /** F2：B 路工具调用日志（path=raw；requestId 由 ToolCallLogger 从 UsageContext 解析）；重试次数进 summary */
    private void record(String tool, Map<String, Object> args, long t0,
                        JdbcRetry.Outcome<?> outcome, int rowCount) {
        String summary = outcome.attempts() > 1
                ? "rows=" + rowCount + " retries=" + (outcome.attempts() - 1)
                : "rows=" + rowCount;
        toolCallLogger.record("raw", tool, args, System.currentTimeMillis() - t0, true, summary);
    }

    private void recordFailed(String tool, Map<String, Object> args, long t0, Exception e) {
        toolCallLogger.record("raw", tool, args, System.currentTimeMillis() - t0, false,
                "ERROR:" + e.getClass().getSimpleName());
    }

    record QueryResult(String rendered, int rowCount) {}

    private static QueryResult collectRows(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return new QueryResult(rows.toString(), rows.size());
        }
    }

    private static String sanitize(String msg) {
        if (msg == null) return "";
        return msg.replaceAll("https?://[^\\s\"]+", "[url]");
    }
}
