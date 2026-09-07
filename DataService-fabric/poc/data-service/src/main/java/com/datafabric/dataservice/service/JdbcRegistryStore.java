package com.datafabric.dataservice.service;

import com.datafabric.dataservice.service.ServiceRegistryStore.DayCount;
import com.datafabric.dataservice.service.ServiceRegistryStore.UsageSummary;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * W6-B 注册表持久化 H2 实现（平台自有文件库，见 RegistryDbConfig）。
 *
 *   - 建表 lazy 幂等（首次访问 ensureSchema）
 *   - save 用 H2 方言 MERGE INTO … KEY(slug)（同 w4-init.sql 种子模式）
 *   - 列白名单/过滤/融合声明序列化为 JSON VARCHAR；时间戳手工映射 Instant
 *   - 降级：库不可达时按接口注释约定的策略吞异常告警，不炸 boot / 不阻断查询
 */
@Component
public class JdbcRegistryStore implements ServiceRegistryStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcRegistryStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** 懒建表标记（volatile：首连接双写无害，CREATE IF NOT EXISTS 幂等） */
    private volatile boolean schemaReady;

    private final HikariDataSource pool;

    public JdbcRegistryStore(HikariDataSource registryPool) {
        this.pool = registryPool;
    }

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS `service_def` (
              `slug` VARCHAR(64) PRIMARY KEY,
              `name` VARCHAR(256) NOT NULL,
              `description` VARCHAR(1024),
              `type` VARCHAR(16) NOT NULL,
              `source` VARCHAR(16) NOT NULL,
              `tbl` VARCHAR(64) NOT NULL,
              `allowed_columns` VARCHAR(4096) NOT NULL,
              `filters` VARCHAR(4096) NOT NULL,
              `joins` VARCHAR(8192) NOT NULL,
              `aggregates` VARCHAR(2048),
              `default_limit` INT NOT NULL,
              `api_key` VARCHAR(64) NOT NULL,
              `key_status` VARCHAR(16) NOT NULL,
              `key_expires_at` TIMESTAMP,
              `rate_limit_per_min` INT NOT NULL,
              `created_at` TIMESTAMP NOT NULL
            )""";

    /** W6-C 迁移：W6-B 落库的旧表补 aggregates 列（旧行 NULL → fromRow 兜底空列表） */
    private static final String DDL_MIGRATE_AGGREGATES =
            "ALTER TABLE `service_def` ADD COLUMN IF NOT EXISTS `aggregates` VARCHAR(2048)";

    private static final String DDL_USAGE = """
            CREATE TABLE IF NOT EXISTS `service_usage` (
              `slug` VARCHAR(64) NOT NULL,
              `usage_day` DATE NOT NULL,
              `calls` BIGINT NOT NULL DEFAULT 0,
              PRIMARY KEY (`slug`, `usage_day`)
            )""";

    private void ensureSchema(Connection conn) throws SQLException {
        if (schemaReady) {
            return;
        }
        try (java.sql.Statement st = conn.createStatement()) {
            st.execute(DDL);
            st.execute(DDL_MIGRATE_AGGREGATES);
            st.execute(DDL_USAGE);
        }
        schemaReady = true;
    }

    @Override
    public void save(ServiceDefinition def) {
        try (Connection conn = pool.getConnection()) {
            ensureSchema(conn);
            // 显式列清单：ALTER 迁移追加的 aggregates 物理上在表尾，位置绑定会错位（E2E 实证）
            String sql = """
                    MERGE INTO `service_def` (`slug`,`name`,`description`,`type`,`source`,`tbl`,
                      `allowed_columns`,`filters`,`joins`,`aggregates`,`default_limit`,`api_key`,
                      `key_status`,`key_expires_at`,`rate_limit_per_min`,`created_at`)
                      KEY(`slug`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, def.slug());
                ps.setString(2, def.name());
                ps.setString(3, def.description());
                ps.setString(4, def.type());
                ps.setString(5, def.source());
                ps.setString(6, def.table());
                ps.setString(7, writeJson(def.allowedColumns()));
                ps.setString(8, writeJson(def.filters()));
                ps.setString(9, writeJson(def.joins()));
                ps.setString(10, writeJson(def.aggregates()));
                ps.setInt(11, def.defaultLimit());
                ps.setString(12, def.apiKey());
                ps.setString(13, def.keyPolicy() == null ? "NONE" : def.keyPolicy().status());
                ps.setTimestamp(14, def.keyPolicy() == null || def.keyPolicy().expiresAt() == null
                        ? null : Timestamp.from(def.keyPolicy().expiresAt()));
                ps.setInt(15, def.keyPolicy() == null ? 0 : def.keyPolicy().rateLimitPerMin());
                ps.setTimestamp(16, Timestamp.from(def.createdAt()));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("注册表持久化 save 降级（内存态继续服务） slug={}: {}", def.slug(), e.getMessage());
        }
    }

    @Override
    public void delete(String slug) {
        try (Connection conn = pool.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM `service_def` WHERE `slug` = ?")) {
                ps.setString(1, slug);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("注册表持久化 delete 降级 slug={}: {}", slug, e.getMessage());
        }
    }

    @Override
    public List<ServiceDefinition> loadAll() {
        try (Connection conn = pool.getConnection()) {
            ensureSchema(conn);
            String sql = "SELECT * FROM `service_def` ORDER BY `created_at`";
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                List<ServiceDefinition> out = new ArrayList<>();
                while (rs.next()) {
                    try {
                        out.add(fromRow(rs));
                    } catch (Exception rowEx) {
                        log.error("注册表行损坏跳过: {}", rowEx.getMessage());
                    }
                }
                return out;
            }
        } catch (SQLException e) {
            log.error("注册表 loadAll 失败（按空表处理 = 未持久化过/库不可达）: {}", e.getMessage());
            return List.of();
        }
    }

    private static ServiceDefinition fromRow(ResultSet rs) throws SQLException {
        Instant expiresAt = rs.getTimestamp("key_expires_at") == null
                ? null : rs.getTimestamp("key_expires_at").toInstant();
        String status = rs.getString("key_status");
        ServiceDefinition.KeyPolicy policy = "NONE".equals(status)
                ? null
                : new ServiceDefinition.KeyPolicy(status, expiresAt, rs.getInt("rate_limit_per_min"));
        return new ServiceDefinition(
                rs.getString("slug"), rs.getString("name"), rs.getString("description"),
                rs.getString("type"), null, null,
                rs.getString("source"), rs.getString("tbl"),
                readJson(rs.getString("allowed_columns"), new TypeReference<List<String>>() {}),
                readJsonList(rs.getString("filters"),
                        new TypeReference<List<ServiceDefinition.FilterSpec>>() {}),
                readJsonList(rs.getString("joins"),
                        new TypeReference<List<ServiceDefinition.JoinSpec>>() {}),
                readJsonList(rs.getString("aggregates"),
                        new TypeReference<List<ServiceDefinition.AggSpec>>() {}),
                rs.getInt("default_limit"), rs.getString("api_key"), policy,
                rs.getTimestamp("created_at").toInstant());
    }

    @Override
    public void recordUsage(String slug) {
        try (Connection conn = pool.getConnection()) {
            ensureSchema(conn);
            LocalDate today = LocalDate.now();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE `service_usage` SET `calls` = `calls` + 1 WHERE `slug` = ? AND `usage_day` = ?")) {
                ps.setString(1, slug);
                ps.setDate(2, java.sql.Date.valueOf(today));
                if (ps.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO `service_usage` (`slug`, `usage_day`, `calls`) VALUES (?,?,1)")) {
                ps.setString(1, slug);
                ps.setDate(2, java.sql.Date.valueOf(today));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            log.warn("用量计量降级（查询不阻断） slug={}: {}", slug, e.getMessage());
        }
    }

    @Override
    public UsageSummary usage(String slug) {
        List<DayCount> recent = new ArrayList<>();
        long total = 0;
        long today = 0;
        String todayStr = LocalDate.now().format(DAY_FMT);
        try (Connection conn = pool.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(`calls`),0) FROM `service_usage` WHERE `slug` = ?")) {
                ps.setString(1, slug);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getLong(1);
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT `usage_day`, `calls` FROM `service_usage` WHERE `slug` = ? "
                            + "ORDER BY `usage_day` DESC LIMIT 14")) {
                ps.setString(1, slug);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String day = rs.getDate("usage_day").toLocalDate().format(DAY_FMT);
                        long calls = rs.getLong("calls");
                        if (todayStr.equals(day)) {
                            today = calls;
                        }
                        recent.add(new DayCount(day, calls));
                    }
                }
            }
        } catch (SQLException e) {
            log.warn("用量查询降级 slug={}: {}", slug, e.getMessage());
        }
        return new UsageSummary(slug, total, today, List.copyOf(recent));
    }

    private static String writeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("注册表 JSON 序列化失败", e);
        }
    }

    private static <T> List<T> readJsonList(String json, TypeReference<List<T>> type) {
        // 旧行迁移后 JSON 列可能为 NULL（W6-C 前落库）→ 按空列表兜底
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<T> v = readJson(json, type);
        return v == null ? List.of() : v;
    }

    private static <T> T readJson(String json, TypeReference<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("注册表 JSON 反序列化失败: " + json, e);
        }
    }
}
