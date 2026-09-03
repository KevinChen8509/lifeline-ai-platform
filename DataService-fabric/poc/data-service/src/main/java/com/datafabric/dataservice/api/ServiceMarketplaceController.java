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
 * W5/W6 服务市场：
 *   - 目录：GET /api/v1/services（builtin + 自助发布）
 *   - 自助发布：POST /api/v1/services（校验见 ServiceRegistry；融合声明见 joins）
 *   - 试调：GET /api/v1/services/{slug}/query（table-query / fusion）
 *   - 下线：DELETE /api/v1/services/{slug}
 *
 * SQL 安全（发布期白名单 + 执行期复检 + 全参数绑定）：
 *   - 标识符（表/列/关联键）只能来自注册表，执行时再过 ^[A-Za-z0-9_]+$ 二次复检
 *   - 值一律 PreparedStatement ? 绑定（与 F4 红队验证同一防线）；融合从表的
 *     关联值同样走 IN (?,…) 绑定 —— 即便来自主表查询结果也不拼 SQL
 *   - LIMIT 钳制 1..500；融合从表另加每主键 limitPerParent 钳制
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

    /** 融合响应：主行平铺主表列，每个 join.name 挂嵌套从行数组 */
    public record JoinView(String name, List<String> columns) {}

    public record FusionQueryResponse(String slug, List<String> columns, List<JoinView> joins,
                                      List<Map<String, Object>> rows, int total, long elapsedMs) {}

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

    /** 试调：过滤参数按注册的 filters 生效（作用于主表），limit 钳制 */
    @GetMapping("/{slug}/query")
    public Object query(@PathVariable String slug, @RequestParam Map<String, String> params) {
        ServiceDefinition def = registry.find(slug)
                .orElseThrow(() -> new ServiceNotFoundException(slug));
        if (ServiceDefinition.TYPE_BUILTIN.equals(def.type())) {
            throw new IllegalArgumentException("内置服务请直接调用其端点: " + def.method() + " " + def.pathTemplate());
        }

        String limitRaw = params.remove("limit");
        int limit = parseLimit(limitRaw, def.defaultLimit());
        List<ServiceDefinition.FilterSpec> applied = resolveFilters(def, params);

        long start = System.currentTimeMillis();
        List<Map<String, String>> mainRows = execute(def, applied, params, limit);
        long elapsed = System.currentTimeMillis() - start;

        registry.recordCall(slug);
        if (ServiceDefinition.TYPE_FUSION.equals(def.type())) {
            List<Map<String, Object>> rows = new ArrayList<>(mainRows.size());
            for (Map<String, String> r : mainRows) {
                rows.add(new LinkedHashMap<String, Object>(r));
            }
            attachJoins(def, rows);
            serviceCallLog.info("{\"slug\":\"{}\",\"type\":\"fusion\",\"source\":\"{}\",\"table\":\"{}\",\"filters\":{},\"rows\":{},\"joins\":{},\"limit\":{},\"elapsedMs\":{}}",
                    slug, def.source(), def.table(), params.size(), rows.size(), def.joins().size(), limit, elapsed);
            return new FusionQueryResponse(slug, def.allowedColumns(),
                    def.joins().stream().map(j -> new JoinView(j.name(), j.columns())).toList(),
                    rows, rows.size(), elapsed);
        }
        serviceCallLog.info("{\"slug\":\"{}\",\"source\":\"{}\",\"table\":\"{}\",\"filters\":{},\"rows\":{},\"limit\":{},\"elapsedMs\":{}}",
                slug, def.source(), def.table(), params.size(), mainRows.size(), limit, elapsed);
        return new QueryResponse(slug, def.allowedColumns(), mainRows, mainRows.size(), elapsed);
    }

    /**
     * 融合从表执行：按主行 parentColumn 值批量取从行（IN 全值绑定），
     * 分组挂到主行 join.name 上，每组截断 limitPerParent。就地写入 rows。
     */
    private void attachJoins(ServiceDefinition def, List<Map<String, Object>> rows) {
        for (ServiceDefinition.JoinSpec join : def.joins()) {
            // 第二道闸：从表标识符执行期复检
            for (String col : join.columns()) {
                checkIdentifier(col);
            }
            checkIdentifier(join.joinColumn());
            String joinSource = join.fqn().substring(0, join.fqn().indexOf('.')).toLowerCase(java.util.Locale.ROOT);
            String joinTable = join.fqn().substring(join.fqn().lastIndexOf('.') + 1);
            checkIdentifier(joinTable);

            // 收集主行关联值（去重保序，跳过 null）
            java.util.Set<String> parentValues = new java.util.LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                Object v = row.get(join.parentColumn());
                if (v != null) {
                    parentValues.add(v.toString());
                }
            }
            if (parentValues.isEmpty()) {
                for (Map<String, Object> row : rows) {
                    row.put(join.name(), List.of());
                }
                continue;
            }

            StringBuilder sql = new StringBuilder("SELECT ");
            List<String> selectCols = new ArrayList<>(join.columns());
            selectCols.add(join.joinColumn());
            sql.append(selectCols.stream().map(c -> "`" + c + "`")
                    .reduce((a, b) -> a + ", " + b).orElseThrow());
            sql.append(" FROM `").append(joinTable).append("` WHERE `")
                    .append(join.joinColumn()).append("` IN (");
            sql.append("?, ".repeat(parentValues.size() - 1)).append("?)");
            sql.append(" LIMIT ").append((long) join.limitPerParent() * parentValues.size());

            Map<String, List<Map<String, String>>> grouped = new LinkedHashMap<>();
            HikariDataSource pool = pools.get(joinSource);
            try (Connection conn = pool.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                int i = 1;
                for (String v : parentValues) {
                    ps.setString(i++, v);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        for (String col : join.columns()) {
                            row.put(col, rs.getString(col));
                        }
                        grouped.computeIfAbsent(rs.getString(join.joinColumn()), k -> new ArrayList<>())
                                .add(row);
                    }
                }
            } catch (SQLException e) {
                log.error("融合从表查询失败 slug={} join={} source={}: {}",
                        def.slug(), join.name(), joinSource, e.getMessage());
                throw new SourceUnavailableException(joinSource, e);
            }

            // 挂载：每组截断 limitPerParent
            for (Map<String, Object> mainRow : rows) {
                Object key = mainRow.get(join.parentColumn());
                List<Map<String, String>> children = key == null
                        ? List.of()
                        : grouped.getOrDefault(key.toString(), List.of());
                mainRow.put(join.name(), children.size() > join.limitPerParent()
                        ? children.subList(0, join.limitPerParent())
                        : children);
            }
        }
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
