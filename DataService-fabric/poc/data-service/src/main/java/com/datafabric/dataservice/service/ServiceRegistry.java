package com.datafabric.dataservice.service;

import com.datafabric.dataservice.metadata.OpenMetadataClient;
import com.datafabric.dataservice.metadata.TableMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * W5 服务注册表（内存态 PoC）：
 * builtin 静态 6 条 + 自助发布 table-query 条目；发布校验是防注入的第一道闸 ——
 * 列名必须逐字命中 OpenMetadata 元数据，杜绝任意标识符进 SQL。
 *
 * Phase 3+ 边界（团队章程支柱④）：持久化、订阅/API Key 分发、限流计量。
 */
@Service
public class ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Set<String> OPERATORS = Set.of("eq", "like", "gte", "lte");
    private static final Set<String> SOURCES = Set.of("mysql", "clickhouse", "postgres");
    private static final int MAX_LIMIT = 500;

    /** 发布请求体（fqn 为目录表全限定名，table 取其末段） */
    public record PublishRequest(
            String slug, String name, String description, String fqn,
            List<String> allowedColumns, List<ServiceDefinition.FilterSpec> filters,
            Integer defaultLimit) {}

    private final OpenMetadataClient omClient;
    private final Map<String, ServiceDefinition> published = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> callCounts = new ConcurrentHashMap<>();

    public ServiceRegistry(OpenMetadataClient omClient) {
        this.omClient = omClient;
    }

    private static final List<ServiceDefinition> BUILTIN = List.of(
            new ServiceDefinition("customer-profile", "客户画像", "单客户全量画像（脱敏 + 审计 + 血缘三切面）",
                    ServiceDefinition.TYPE_BUILTIN, "GET", "/api/v1/customers/{custId}/profile",
                    null, null, List.of(), List.of(), 1, null),
            new ServiceDefinition("customer-brief", "客户简报", "简要画像（强制隐藏身份证与风险分）",
                    ServiceDefinition.TYPE_BUILTIN, "GET", "/api/v1/customers/{custId}/brief",
                    null, null, List.of(), List.of(), 1, null),
            new ServiceDefinition("customer-search", "客户分群查询", "按等级/风险过滤 + 分页（F7 服务端过滤）",
                    ServiceDefinition.TYPE_BUILTIN, "GET", "/api/v1/customers?level={level}&riskLevel={riskLevel}&page={page}&size={size}",
                    null, null, List.of(), List.of(), 20, null),
            new ServiceDefinition("customer-overview", "客户总览指标", "ARPU / VIP3 / 风险分布快照（Cube 语义层）",
                    ServiceDefinition.TYPE_BUILTIN, "GET", "/api/v1/metrics/customer-overview",
                    null, null, List.of(), List.of(), 1, null),
            new ServiceDefinition("agent-insight", "AI 治理问答（A 路）", "自然语言 → 治理校验 + 审计血缘落账 → 语义层查询",
                    ServiceDefinition.TYPE_BUILTIN, "POST", "/api/v1/agent/insight",
                    null, null, List.of(), List.of(), 1, null),
            new ServiceDefinition("agent-raw", "AI 直连问答（B 路）", "自然语言 → 直连 JDBC（无治理对照）",
                    ServiceDefinition.TYPE_BUILTIN, "POST", "/api/v1/agent/raw",
                    null, null, List.of(), List.of(), 1, null));

    /** 发布一个 table-query 服务（校验失败抛 IllegalArgumentException → 400） */
    public ServiceDefinition publish(PublishRequest req) {
        String slug = requireMatch(req.slug(), SLUG_PATTERN, "slug 仅允许小写字母/数字/连字符，2-63 位");
        if (isBuiltin(slug) || published.containsKey(slug)) {
            throw new IllegalArgumentException("slug 已存在: " + slug);
        }
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (req.fqn() == null || !req.fqn().contains(".")) {
            throw new IllegalArgumentException("fqn 必须是目录表全限定名（如 mysql.customer_db.customer）");
        }
        String source = req.fqn().substring(0, req.fqn().indexOf('.')).toLowerCase(Locale.ROOT);
        if (!SOURCES.contains(source)) {
            throw new IllegalArgumentException("不支持的源: " + source + "（可选 mysql/clickhouse/postgres）");
        }
        String table = lastSegment(req.fqn());
        requireMatch(table, IDENTIFIER_PATTERN, "表名不合法: " + table);

        // 第一道闸：列白名单必须逐字命中 OM 元数据（OM 离线时拒绝发布，不降低校验强度）
        Set<String> metaColumns = metadataColumns(req.fqn());
        if (metaColumns.isEmpty()) {
            throw new IllegalArgumentException("目录元数据不可用或表不存在: " + req.fqn() + "，发布前请确认 OpenMetadata 在线");
        }
        List<String> columns = req.allowedColumns() == null ? List.of() : req.allowedColumns();
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("allowedColumns 至少选择一列");
        }
        for (String col : columns) {
            requireMatch(col, IDENTIFIER_PATTERN, "列名不合法: " + col);
            if (!metaColumns.contains(col)) {
                throw new IllegalArgumentException("列 " + col + " 不在目录表 " + req.fqn() + " 的元数据中");
            }
        }
        List<ServiceDefinition.FilterSpec> filters = normalizeFilters(req.filters(), columns);

        int defaultLimit = req.defaultLimit() == null ? 20 : req.defaultLimit();
        if (defaultLimit < 1 || defaultLimit > MAX_LIMIT) {
            throw new IllegalArgumentException("defaultLimit 必须在 1-" + MAX_LIMIT + " 之间");
        }

        ServiceDefinition def = new ServiceDefinition(slug, req.name().trim(),
                req.description() == null ? "" : req.description().trim(),
                ServiceDefinition.TYPE_TABLE_QUERY, null, null,
                source, table, List.copyOf(columns), filters, defaultLimit, Instant.now());
        published.put(slug, def);
        log.info("服务发布: {} -> {} ({} 列 / {} 过滤参数)", slug, req.fqn(), columns.size(), filters.size());
        return def;
    }

    private static List<ServiceDefinition.FilterSpec> normalizeFilters(
            List<ServiceDefinition.FilterSpec> filters, List<String> columns) {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        List<ServiceDefinition.FilterSpec> out = new ArrayList<>();
        for (ServiceDefinition.FilterSpec f : filters) {
            if (f == null || f.column() == null || f.operator() == null) {
                throw new IllegalArgumentException("filters 每项需含 column 与 operator");
            }
            if (!columns.contains(f.column())) {
                throw new IllegalArgumentException("过滤列 " + f.column() + " 必须同时在 allowedColumns 中");
            }
            String op = f.operator().toLowerCase(Locale.ROOT);
            if (!OPERATORS.contains(op)) {
                throw new IllegalArgumentException("operator 仅支持 eq/like/gte/lte: " + f.operator());
            }
            out.add(new ServiceDefinition.FilterSpec(f.column(), op));
        }
        return List.copyOf(out);
    }

    private Set<String> metadataColumns(String fqn) {
        return omClient.fetchTables().stream()
                .filter(t -> t.fqn().equals(fqn))
                .findFirst()
                .map(t -> Set.copyOf(t.columns().stream().map(TableMetadata.Column::name).toList()))
                .orElse(Set.of());
    }

    public List<ServiceDefinition> list() {
        List<ServiceDefinition> all = new ArrayList<>(BUILTIN);
        all.addAll(published.values().stream()
                .sorted(java.util.Comparator.comparing(ServiceDefinition::createdAt))
                .toList());
        return List.copyOf(all);
    }

    public java.util.Optional<ServiceDefinition> find(String slug) {
        ServiceDefinition def = published.get(slug);
        if (def != null) {
            return java.util.Optional.of(def);
        }
        return BUILTIN.stream().filter(d -> d.slug().equals(slug)).findFirst();
    }

    public boolean isBuiltin(String slug) {
        return BUILTIN.stream().anyMatch(d -> d.slug().equals(slug));
    }

    /** 下线自助发布的服务；builtin 拒删，不存在抛 ServiceNotFoundException（controller 转 404） */
    public void remove(String slug) {
        if (isBuiltin(slug)) {
            throw new IllegalArgumentException("内置服务不可删除: " + slug);
        }
        ServiceDefinition removed = published.remove(slug);
        if (removed == null) {
            throw new ServiceNotFoundException(slug);
        }
        callCounts.remove(slug);
        log.info("服务下线: {}", slug);
    }

    public void recordCall(String slug) {
        callCounts.computeIfAbsent(slug, k -> new AtomicLong()).incrementAndGet();
    }

    public long callCount(String slug) {
        AtomicLong c = callCounts.get(slug);
        return c == null ? 0 : c.get();
    }

    public static int maxLimit() {
        return MAX_LIMIT;
    }

    private static String lastSegment(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    private static String requireMatch(String value, Pattern pattern, String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
