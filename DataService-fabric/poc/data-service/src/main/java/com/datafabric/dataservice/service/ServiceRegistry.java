package com.datafabric.dataservice.service;

import com.datafabric.dataservice.metadata.OpenMetadataClient;
import com.datafabric.dataservice.metadata.TableMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * W5/W6 服务注册表：
 * builtin 静态 6 条 + 自助发布 table-query / fusion / aggregate 条目；发布校验是防注入的第一道闸 ——
 * 主表/从表的列名与关联键必须逐字命中 OpenMetadata 元数据，杜绝任意标识符进 SQL。
 *
 * W6-A 对外开放：发布即生成服务级 API Key（仅可调用自身 /query，双通道见 SecurityConfig）。
 * W6-B 运营化：注册表持久化（ServiceRegistryStore，@PostConstruct 灌回，重启 Key 不变）
 * + key 轮换/吊销/过期 + 按服务每分钟固定窗口限流 + 每日用量计量。
 *
 * Phase 3+ 边界（团队章程支柱④）：持久化、key 吊销/过期、限流计量。
 */
@Service
public class ServiceRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistry.class);

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final Set<String> OPERATORS = Set.of("eq", "like", "gte", "lte");
    private static final Set<String> SOURCES = Set.of("mysql", "clickhouse", "postgres");
    private static final int MAX_LIMIT = 500;
    private static final int MAX_JOINS = 2;
    private static final int MAX_PER_PARENT = 50;
    private static final int MAX_AGGREGATES = 5;
    private static final Set<String> AGG_FUNCTIONS = Set.of("SUM", "COUNT", "AVG", "MIN", "MAX");
    private static final int DEFAULT_RATE_LIMIT_PER_MIN = 60;
    private static final int MAX_RATE_LIMIT_PER_MIN = 600;
    private static final long MAX_TTL_HOURS = 24L * 365 * 10;
    private static final long RATE_WINDOW_MS = 60_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 发布请求体（fqn 为主表目录全限定名，table 取其末段；joins 非空 → fusion；aggregates 非空 → aggregate） */
    public record PublishRequest(
            String slug, String name, String description, String fqn,
            List<String> allowedColumns, List<ServiceDefinition.FilterSpec> filters,
            List<JoinRequest> joins, List<AggRequest> aggregates, Integer defaultLimit,
            Integer rateLimitPerMin, Long keyTtlHours) {}

    /** 融合从表发布声明 */
    public record JoinRequest(String fqn, String name, List<String> columns,
                              String joinColumn, String parentColumn, Integer limitPerParent) {}

    /** 聚合列发布声明（W6-C；column 空仅 COUNT = COUNT(*)） */
    public record AggRequest(String function, String column, String alias) {}

    private final OpenMetadataClient omClient;
    private final ServiceRegistryStore store;
    private final Map<String, ServiceDefinition> published = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> callCounts = new ConcurrentHashMap<>();
    /** 固定窗口限流：slug -> [windowStart, count]（ConcurrentHashMap.compute 原子更新） */
    private final Map<String, long[]> rateWindows = new ConcurrentHashMap<>();

    public ServiceRegistry(OpenMetadataClient omClient, ServiceRegistryStore store) {
        this.omClient = omClient;
        this.store = store;
    }

    /** 启动灌回持久化注册表（重启后服务与 Key 不变；库不可达时 loadAll 返回空 = 全新注册表） */
    @jakarta.annotation.PostConstruct
    void loadPersisted() {
        List<ServiceDefinition> restored = store.loadAll();
        for (ServiceDefinition def : restored) {
            published.put(def.slug(), def);
        }
        if (!restored.isEmpty()) {
            log.info("注册表持久化恢复: {} 个自助发布服务（key 保持不变）", restored.size());
        }
    }

    private static final List<ServiceDefinition> BUILTIN = List.of(
            new ServiceDefinition("customer-profile", "客户画像", "单客户全量画像（脱敏 + 审计 + 血缘三切面）",
                    ServiceDefinition.TYPE_BUILTIN, "GET", "/api/v1/customers/{custId}/profile",
                    null, null, null, List.of(), List.of(), List.of(), List.of(), 1, null, null, null),
            new ServiceDefinition("customer-brief", "客户简报", "简要画像（强制隐藏身份证与风险分）",
                    ServiceDefinition.TYPE_BUILTIN, "GET", "/api/v1/customers/{custId}/brief",
                    null, null, null, List.of(), List.of(), List.of(), List.of(), 1, null, null, null),
            new ServiceDefinition("customer-search", "客户分群查询", "按等级/风险过滤 + 分页（F7 服务端过滤）",
                    ServiceDefinition.TYPE_BUILTIN, "GET", "/api/v1/customers?level={level}&riskLevel={riskLevel}&page={page}&size={size}",
                    null, null, null, List.of(), List.of(), List.of(), List.of(), 20, null, null, null),
            new ServiceDefinition("customer-overview", "客户总览指标", "ARPU / VIP3 / 风险分布快照（Cube 语义层）",
                    ServiceDefinition.TYPE_BUILTIN, "GET", "/api/v1/metrics/customer-overview",
                    null, null, null, List.of(), List.of(), List.of(), List.of(), 1, null, null, null),
            new ServiceDefinition("agent-insight", "AI 治理问答（A 路）", "自然语言 → 治理校验 + 审计血缘落账 → 语义层查询",
                    ServiceDefinition.TYPE_BUILTIN, "POST", "/api/v1/agent/insight",
                    null, null, null, List.of(), List.of(), List.of(), List.of(), 1, null, null, null),
            new ServiceDefinition("agent-raw", "AI 直连问答（B 路）", "自然语言 → 直连 JDBC（无治理对照）",
                    ServiceDefinition.TYPE_BUILTIN, "POST", "/api/v1/agent/raw",
                    null, null, null, List.of(), List.of(), List.of(), List.of(), 1, null, null, null));

    /** 发布一个 table-query / fusion 服务（校验失败抛 IllegalArgumentException → 400） */
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
        String database = middleSegment(req.fqn());
        requireMatch(database, IDENTIFIER_PATTERN, "库名不合法: " + database);
        String table = lastSegment(req.fqn());
        requireMatch(table, IDENTIFIER_PATTERN, "表名不合法: " + table);

        // 第一道闸（主表）：列白名单必须逐字命中 OM 元数据（OM 离线时拒绝发布，不降低校验强度）
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
        // 第一道闸（融合从表）：列/关联键逐字命中从表 OM 元数据，主表关联键 ∈ 主表白名单
        List<ServiceDefinition.JoinSpec> joins = normalizeJoins(req.joins(), columns);

        // 第一道闸（聚合列，W6-C）：函数白名单 + 列逐字命中 OM 元数据 + alias 唯一；与 joins 互斥
        List<ServiceDefinition.AggSpec> aggregates = normalizeAggregates(req.aggregates(), metaColumns, columns);
        if (!aggregates.isEmpty() && !joins.isEmpty()) {
            throw new IllegalArgumentException("聚合与融合互斥，不能同时声明（聚合+组合属 Phase 3+）");
        }
        // 聚合形态的过滤列放宽为「命中元数据即可」（WHERE 先于 GROUP BY，非维度列合法）；
        // table-query / fusion 保持过滤列 ∈ allowedColumns 的旧不变式
        List<ServiceDefinition.FilterSpec> filters = normalizeFilters(
                req.filters(), !aggregates.isEmpty() ? metaColumns : columns);

        int defaultLimit = req.defaultLimit() == null ? 20 : req.defaultLimit();
        if (defaultLimit < 1 || defaultLimit > MAX_LIMIT) {
            throw new IllegalArgumentException("defaultLimit 必须在 1-" + MAX_LIMIT + " 之间");
        }
        int rateLimitPerMin = req.rateLimitPerMin() == null
                ? DEFAULT_RATE_LIMIT_PER_MIN : req.rateLimitPerMin();
        if (rateLimitPerMin < 1 || rateLimitPerMin > MAX_RATE_LIMIT_PER_MIN) {
            throw new IllegalArgumentException("rateLimitPerMin 必须在 1-" + MAX_RATE_LIMIT_PER_MIN + " 之间");
        }
        Instant keyExpiresAt = ttlToExpiry(req.keyTtlHours());

        String type = !aggregates.isEmpty() ? ServiceDefinition.TYPE_AGGREGATE
                : joins.isEmpty() ? ServiceDefinition.TYPE_TABLE_QUERY : ServiceDefinition.TYPE_FUSION;
        ServiceDefinition def = new ServiceDefinition(slug, req.name().trim(),
                req.description() == null ? "" : req.description().trim(),
                type, null, null,
                source, database, table, List.copyOf(columns), filters, joins, aggregates, defaultLimit,
                generateApiKey(),
                new ServiceDefinition.KeyPolicy(
                        ServiceDefinition.KeyPolicy.STATUS_ACTIVE, keyExpiresAt, rateLimitPerMin),
                Instant.now());
        published.put(slug, def);
        store.save(def);
        log.info("服务发布: {} -> {} ({} 列 / {} 过滤参数 / {} 融合从表 / {} 聚合列 / key=sk-…{} / 限流 {}/min / 过期 {})",
                slug, req.fqn(), columns.size(), filters.size(), joins.size(), aggregates.size(),
                def.apiKey().substring(def.apiKey().length() - 4),
                rateLimitPerMin, keyExpiresAt == null ? "永久" : keyExpiresAt);
        return def;
    }

    /** ttlHours 可空=永久；1..MAX_TTL_HOURS → 过期时刻；越界 400 */
    private static Instant ttlToExpiry(Long keyTtlHours) {
        if (keyTtlHours == null) {
            return null;
        }
        if (keyTtlHours < 1 || keyTtlHours > MAX_TTL_HOURS) {
            throw new IllegalArgumentException("keyTtlHours 必须在 1-" + MAX_TTL_HOURS + " 之间（小时）");
        }
        return Instant.now().plusSeconds(keyTtlHours * 3600);
    }

    /** 融合从表校验链（受限 DSL：不开放任意 SQL，标识符全部来自元数据白名单） */
    private List<ServiceDefinition.JoinSpec> normalizeJoins(
            List<JoinRequest> joins, List<String> mainColumns) {
        if (joins == null || joins.isEmpty()) {
            return List.of();
        }
        if (joins.size() > MAX_JOINS) {
            throw new IllegalArgumentException("融合从表最多 " + MAX_JOINS + " 个（不做递归融合）");
        }
        List<ServiceDefinition.JoinSpec> out = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (JoinRequest j : joins) {
            if (j == null || j.fqn() == null || !j.fqn().contains(".")) {
                throw new IllegalArgumentException("joins.fqn 必须是从表目录全限定名");
            }
            String joinName = requireMatch(j.name(), IDENTIFIER_PATTERN, "join.name 不合法: " + j.name());
            if (names.contains(joinName)) {
                throw new IllegalArgumentException("join.name 重复: " + joinName);
            }
            names.add(joinName);

            String joinSource = j.fqn().substring(0, j.fqn().indexOf('.')).toLowerCase(Locale.ROOT);
            if (!SOURCES.contains(joinSource)) {
                throw new IllegalArgumentException("从表不支持的源: " + joinSource);
            }
            String joinTable = lastSegment(j.fqn());
            requireMatch(joinTable, IDENTIFIER_PATTERN, "从表名不合法: " + joinTable);

            Set<String> joinMeta = metadataColumns(j.fqn());
            if (joinMeta.isEmpty()) {
                throw new IllegalArgumentException("从表目录元数据不可用或不存在: " + j.fqn());
            }
            List<String> joinColumns = j.columns() == null ? List.of() : j.columns();
            if (joinColumns.isEmpty()) {
                throw new IllegalArgumentException("join " + joinName + " 的 columns 至少选择一列");
            }
            for (String col : joinColumns) {
                requireMatch(col, IDENTIFIER_PATTERN, "从表列名不合法: " + col);
                if (!joinMeta.contains(col)) {
                    throw new IllegalArgumentException("从表列 " + col + " 不在目录表 " + j.fqn() + " 的元数据中");
                }
            }
            String joinColumn = requireMatch(j.joinColumn(), IDENTIFIER_PATTERN,
                    "joinColumn 不合法: " + j.joinColumn());
            if (!joinMeta.contains(joinColumn)) {
                throw new IllegalArgumentException("joinColumn " + joinColumn + " 不在从表 " + j.fqn() + " 的元数据中");
            }
            String parentColumn = requireMatch(j.parentColumn(), IDENTIFIER_PATTERN,
                    "parentColumn 不合法: " + j.parentColumn());
            if (!mainColumns.contains(parentColumn)) {
                throw new IllegalArgumentException("parentColumn " + parentColumn + " 必须同时在主表 allowedColumns 中");
            }
            int perParent = j.limitPerParent() == null ? 20 : j.limitPerParent();
            if (perParent < 1 || perParent > MAX_PER_PARENT) {
                throw new IllegalArgumentException("limitPerParent 必须在 1-" + MAX_PER_PARENT + " 之间");
            }
            out.add(new ServiceDefinition.JoinSpec(j.fqn(), joinName, List.copyOf(joinColumns),
                    joinColumn, parentColumn, perParent));
        }
        return List.copyOf(out);
    }

    /**
     * 聚合列校验链（W6-C 受限 DSL）：函数白名单 + 列逐字命中主表元数据 +
     * alias 标识符唯一不撞维度；与 joins 互斥（聚合+融合不组合，Phase 3+）。
     */
    private static List<ServiceDefinition.AggSpec> normalizeAggregates(
            List<AggRequest> aggregates, Set<String> metaColumns, List<String> dims) {
        if (aggregates == null || aggregates.isEmpty()) {
            return List.of();
        }
        if (aggregates.size() > MAX_AGGREGATES) {
            throw new IllegalArgumentException("聚合列最多 " + MAX_AGGREGATES + " 个");
        }
        List<ServiceDefinition.AggSpec> out = new ArrayList<>();
        List<String> aliases = new ArrayList<>();
        for (AggRequest a : aggregates) {
            if (a == null || a.function() == null || a.alias() == null) {
                throw new IllegalArgumentException("aggregates 每项需含 function 与 alias");
            }
            String function = a.function().trim().toUpperCase(Locale.ROOT);
            if (!AGG_FUNCTIONS.contains(function)) {
                throw new IllegalArgumentException("聚合函数仅支持 SUM/COUNT/AVG/MIN/MAX: " + a.function());
            }
            String alias = requireMatch(a.alias(), IDENTIFIER_PATTERN, "聚合别名不合法: " + a.alias());
            if (aliases.contains(alias) || dims.contains(alias)) {
                throw new IllegalArgumentException("聚合别名重复或与分组维度撞名: " + alias);
            }
            aliases.add(alias);
            String column = a.column() == null ? null : a.column().trim();
            if (column == null || column.isEmpty()) {
                if (!"COUNT".equals(function)) {
                    throw new IllegalArgumentException("仅 COUNT 允许空列（COUNT(*)）: " + function);
                }
            } else {
                requireMatch(column, IDENTIFIER_PATTERN, "聚合列名不合法: " + column);
                if (!metaColumns.contains(column)) {
                    throw new IllegalArgumentException("聚合列 " + column + " 不在目录表元数据中");
                }
            }
            out.add(new ServiceDefinition.AggSpec(function, column, alias));
        }
        return List.copyOf(out);
    }

    private static List<ServiceDefinition.FilterSpec> normalizeFilters(
            List<ServiceDefinition.FilterSpec> filters, java.util.Collection<String> columns) {
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
        rateWindows.remove(slug);
        store.delete(slug);
        log.info("服务下线: {}", slug);
    }

    public void recordCall(String slug) {
        callCounts.computeIfAbsent(slug, k -> new AtomicLong()).incrementAndGet();
        store.recordUsage(slug);
    }

    public long callCount(String slug) {
        AtomicLong c = callCounts.get(slug);
        return c == null ? 0 : c.get();
    }

    /** 每日用量（近 14 天 + 总量/今日），由 controller /usage 端点透出 */
    public ServiceRegistryStore.UsageSummary usage(String slug) {
        return store.usage(slug);
    }

    /**
     * 固定窗口每分钟限流（作用于 /query，全局 key 与服务 key 同计数）：
     * builtin / 无策略服务不限；窗口滑过自动归零。
     */
    public boolean tryAcquire(String slug) {
        ServiceDefinition def = published.get(slug);
        if (def == null || def.keyPolicy() == null) {
            return true;
        }
        int limit = def.keyPolicy().rateLimitPerMin();
        if (limit <= 0) {
            return true;
        }
        long window = System.currentTimeMillis() / RATE_WINDOW_MS;
        long[] w = rateWindows.compute(slug, (k, old) ->
                (old == null || old[0] != window) ? new long[]{window, 1} : new long[]{window, old[1] + 1});
        return w[1] <= limit;
    }

    /** 轮换 key：重新生成 + 状态复活 ACTIVE；keyTtlHours 可空=沿用原过期时刻 */
    public ServiceDefinition rotateKey(String slug, Long keyTtlHours) {
        ServiceDefinition def = requirePublished(slug);
        Instant expiresAt = def.keyPolicy() == null ? null : def.keyPolicy().expiresAt();
        if (keyTtlHours != null) {
            expiresAt = ttlToExpiry(keyTtlHours);
        }
        return rewritePolicy(def, generateApiKey(),
                ServiceDefinition.KeyPolicy.STATUS_ACTIVE, expiresAt);
    }

    /** 吊销 key：立即失效（401）；rotate 可复活 */
    public ServiceDefinition revokeKey(String slug) {
        ServiceDefinition def = requirePublished(slug);
        Instant expiresAt = def.keyPolicy() == null ? null : def.keyPolicy().expiresAt();
        return rewritePolicy(def, def.apiKey(),
                ServiceDefinition.KeyPolicy.STATUS_REVOKED, expiresAt);
    }

    private ServiceDefinition rewritePolicy(ServiceDefinition def, String apiKey,
            String status, Instant expiresAt) {
        int rateLimitPerMin = def.keyPolicy() == null
                ? DEFAULT_RATE_LIMIT_PER_MIN : def.keyPolicy().rateLimitPerMin();
        ServiceDefinition updated = new ServiceDefinition(def.slug(), def.name(), def.description(),
                def.type(), def.method(), def.pathTemplate(), def.source(), def.database(), def.table(),
                def.allowedColumns(), def.filters(), def.joins(), def.aggregates(), def.defaultLimit(),
                apiKey, new ServiceDefinition.KeyPolicy(status, expiresAt, rateLimitPerMin),
                def.createdAt());
        published.put(def.slug(), updated);
        store.save(updated);
        log.info("key 策略更新: {} -> {} (expiresAt={}, 限流 {}/min)",
                def.slug(), status, expiresAt, rateLimitPerMin);
        return updated;
    }

    private ServiceDefinition requirePublished(String slug) {
        ServiceDefinition def = published.get(slug);
        if (def == null) {
            throw new ServiceNotFoundException(slug);
        }
        return def;
    }

    /**
     * 服务级 key 校验（恒时比较）：仅对【自助发布、key 策略可用（未吊销未过期）】的 slug 有效。
     * 供 SecurityConfig 双通道使用 —— 服务 key 只能敲自己 slug 的 /query。
     */
    public boolean isValidServiceKey(String slug, String providedKey) {
        if (slug == null || providedKey == null) {
            return false;
        }
        ServiceDefinition def = published.get(slug);
        boolean keyMatches = def != null && def.apiKey() != null
                && MessageDigest.isEqual(def.apiKey().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        providedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!keyMatches) {
            return false;
        }
        if (def.keyPolicy() == null || !def.keyPolicy().isUsable()) {
            log.warn("服务 key 被拒绝（吊销/过期）: {}", slug);
            return false;
        }
        return true;
    }

    public static int maxLimit() {
        return MAX_LIMIT;
    }

    /** sk-w6- + 32 hex 随机（发布时生成一次，注册表内存态 PoC） */
    private static String generateApiKey() {
        byte[] buf = new byte[16];
        RANDOM.nextBytes(buf);
        return "sk-w6-" + HexFormat.of().formatHex(buf);
    }

    private static String lastSegment(String fqn) {
        return fqn.substring(fqn.lastIndexOf('.') + 1);
    }

    /** FQN 中段（库名/模式名）：source.database.table → database */
    private static String middleSegment(String fqn) {
        return fqn.substring(fqn.indexOf('.') + 1, fqn.lastIndexOf('.'));
    }

    private static String requireMatch(String value, Pattern pattern, String message) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
