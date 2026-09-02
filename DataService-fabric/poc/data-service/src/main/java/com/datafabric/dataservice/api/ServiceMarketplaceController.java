package com.datafabric.dataservice.api;

import com.datafabric.dataservice.service.ServiceDefinition;
import com.datafabric.dataservice.service.ServiceNotFoundException;
import com.datafabric.dataservice.service.ServiceRegistry;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * W5 服务市场：
 *   - 目录：GET /api/v1/services（builtin + 自助发布）
 *   - 自助发布：POST /api/v1/services（校验见 ServiceRegistry）
 *   - 试调：GET /api/v1/services/{slug}/query（table-query 专属）
 *   - 下线：DELETE /api/v1/services/{slug}
 *
 * SQL 安全（发布期白名单 + 执行期复检 + 全参数绑定）：
 *   - 标识符（表/列）只能来自注册表，执行时再过 ^[A-Za-z0-9_]+$ 二次复检
 *   - 值一律 PreparedStatement ? 绑定（与 F4 红队验证同一防线）
 *   - LIMIT 钳制 1..500
 */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceMarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(ServiceMarketplaceController.class);
    /** 单行 JSON 服务调用账（与 TOOL_CALL_LOG/PASSTHROUGH_LOG 同风格） */
    private static final Logger serviceCallLog = LoggerFactory.getLogger("SERVICE_CALL_LOG");

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9_]+$");

    private final ServiceRegistry registry;
    private final Map<String, HikariDataSource> pools;

    public ServiceMarketplaceController(
            ServiceRegistry registry,
            HikariDataSource mysqlPool,
            HikariDataSource clickhousePool,
            HikariDataSource postgresPool) {
        this.registry = registry;
        this.pools = Map.of("mysql", mysqlPool, "clickhouse", clickhousePool, "postgres", postgresPool);
    }

    /** 目录视图：定义 + 调用计数 */
    public record ServiceView(ServiceDefinition service, long callCount) {}

    public record ServicesResponse(List<ServiceView> services) {}

    public record QueryResponse(String slug, List<String> columns, List<Map<String, String>> rows,
                                int total, long elapsedMs) {}

    @GetMapping
    public ServicesResponse list() {
        return new ServicesResponse(registry.list().stream()
                .map(d -> new ServiceView(d, registry.callCount(d.slug())))
                .toList());
    }

    @PostMapping
    public ResponseEntity<ServiceView> publish(@RequestBody ServiceRegistry.PublishRequest req) {
        ServiceDefinition def = registry.publish(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ServiceView(def, 0));
    }

    @GetMapping("/{slug}")
    public ServiceView detail(@PathVariable String slug) {
        return registry.find(slug)
                .map(d -> new ServiceView(d, registry.callCount(slug)))
                .orElseThrow(() -> new ServiceNotFoundException(slug));
    }

    @DeleteMapping("/{slug}")
    public ResponseEntity<Void> remove(@PathVariable String slug) {
        registry.remove(slug);
        return ResponseEntity.noContent().build();
    }

    /** 试调：过滤参数按注册的 filters 生效，limit 钳制 */
    @GetMapping("/{slug}/query")
    public QueryResponse query(@PathVariable String slug, @RequestParam Map<String, String> params) {
        ServiceDefinition def = registry.find(slug)
                .orElseThrow(() -> new ServiceNotFoundException(slug));
        if (!ServiceDefinition.TYPE_TABLE_QUERY.equals(def.type())) {
            throw new IllegalArgumentException("内置服务请直接调用其端点: " + def.method() + " " + def.pathTemplate());
        }

        String limitRaw = params.remove("limit");
        int limit = parseLimit(limitRaw, def.defaultLimit());
        List<ServiceDefinition.FilterSpec> applied = resolveFilters(def, params);

        long start = System.currentTimeMillis();
        List<Map<String, String>> rows = execute(def, applied, params, limit);
        long elapsed = System.currentTimeMillis() - start;

        registry.recordCall(slug);
        serviceCallLog.info("{\"slug\":\"{}\",\"source\":\"{}\",\"table\":\"{}\",\"filters\":{},\"rows\":{},\"limit\":{},\"elapsedMs\":{}}",
                slug, def.source(), def.table(), params.size(), rows.size(), limit, elapsed);
        return new QueryResponse(slug, def.allowedColumns(), rows, rows.size(), elapsed);
    }

    private static int parseLimit(String raw, int defaultLimit) {
        if (raw == null || raw.isBlank()) {
            return Math.min(defaultLimit, ServiceRegistry.maxLimit());
        }
        try {
            return Math.max(1, Math.min(Integer.parseInt(raw), ServiceRegistry.maxLimit()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("limit 必须是整数");
        }
    }

    /** 未知参数直接 400（不静默忽略，契约从注册表来） */
    private static List<ServiceDefinition.FilterSpec> resolveFilters(
            ServiceDefinition def, Map<String, String> params) {
        List<ServiceDefinition.FilterSpec> applied = new ArrayList<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            ServiceDefinition.FilterSpec match = def.filters().stream()
                    .filter(f -> f.column().equals(e.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未注册的过滤参数: " + e.getKey()));
            if (e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            applied.add(match);
        }
        return applied;
    }

    private List<Map<String, String>> execute(
            ServiceDefinition def, List<ServiceDefinition.FilterSpec> applied,
            Map<String, String> params, int limit) {
        // 第二道闸：注册表标识符执行期复检
        for (String col : def.allowedColumns()) {
            checkIdentifier(col);
        }
        checkIdentifier(def.table());

        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(def.allowedColumns().stream()
                .map(c -> "`" + c + "`")
                .reduce((a, b) -> a + ", " + b).orElseThrow());
        sql.append(" FROM `").append(def.table()).append("`");
        List<String> values = new ArrayList<>();
        boolean firstFilter = true;
        for (ServiceDefinition.FilterSpec f : applied) {
            sql.append(firstFilter ? " WHERE " : " AND ").append("`")
                    .append(f.column()).append("` ")
                    .append(operatorSql(f.operator())).append(" ?");
            firstFilter = false;
            String value = params.get(f.column());
            values.add("like".equals(f.operator()) ? "%" + value + "%" : value);
        }
        sql.append(" LIMIT ").append(limit);

        HikariDataSource pool = pools.get(def.source());
        List<Map<String, String>> rows = new ArrayList<>();
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                ps.setString(i + 1, values.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (String col : def.allowedColumns()) {
                        row.put(col, rs.getString(col));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException e) {
            log.error("服务查询失败 slug={} source={}: {}", def.slug(), def.source(), e.getMessage());
            throw new SourceUnavailableException(def.source(), e);
        }
        return rows;
    }

    private static String operatorSql(String operator) {
        return switch (operator) {
            case "eq" -> "=";
            case "like" -> "LIKE";
            case "gte" -> ">=";
            case "lte" -> "<=";
            default -> throw new IllegalArgumentException("不支持的 operator: " + operator);
        };
    }

    private static void checkIdentifier(String identifier) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("标识符不合法: " + identifier);
        }
    }

    /** 数据源不可达（池建连失败/表不存在）→ 502 */
    public static class SourceUnavailableException extends RuntimeException {
        public SourceUnavailableException(String source, Throwable cause) {
            super("数据源不可用: " + source, cause);
        }
    }

    @ExceptionHandler(ServiceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ServiceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "SERVICE_NOT_FOUND", "message", ex.getMessage()));
    }

    @ExceptionHandler(SourceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleSourceDown(SourceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "SOURCE_UNAVAILABLE", "message", ex.getMessage()));
    }
}
