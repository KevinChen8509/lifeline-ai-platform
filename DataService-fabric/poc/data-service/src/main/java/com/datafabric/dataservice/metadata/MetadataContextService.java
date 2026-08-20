package com.datafabric.dataservice.metadata;

import com.datafabric.dataservice.config.OpenMetadataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * F3 A 路 RAG 上下文：把 OpenMetadata 的表注释按问题相关性检索后注入 LLM 提示。
 *
 * 检索是刻意轻量的"元数据 RAG"（非向量）：
 *   - 索引项 = 表名/列名分词（按 . _ 拆开，如 mysql.customer_db.customer → customer/db/mysql）
 *   - 打分   = 问题里命中表级 token +3、列级 token +2（重复命中累计，衡量主题集中度）
 *   - 适用面 = PoC 三源十几张表；表数量上千时再换向量检索（ADR-021 预留）
 *
 * 容错：OM 离线 → 缓存空结果（TTL 内不重试）→ augment() 原样透传，A 路不受影响。
 */
@Service
public class MetadataContextService {

    private static final Logger log = LoggerFactory.getLogger(MetadataContextService.class);

    /**
     * 业务术语表：中文关键词（子串命中）→ 英文元数据 token。
     * 元数据列名是英文、问题是中文，跨语言检索靠这张表桥接；
     * PoC 静态维护，量大了换成 OM 的 glossary（ADR-021 预留）。
     */
    private static final Map<String, List<String>> GLOSSARY = Map.ofEntries(
            Map.entry("客户", List.of("cust", "customer")),
            Map.entry("订单", List.of("order")),
            Map.entry("下单", List.of("order")),
            Map.entry("金额", List.of("amount")),
            Map.entry("时间", List.of("time")),
            Map.entry("风险", List.of("risk")),
            Map.entry("等级", List.of("level")),
            Map.entry("手机", List.of("phone")),
            Map.entry("电话", List.of("phone")),
            Map.entry("地区", List.of("region")),
            Map.entry("区域", List.of("region")),
            Map.entry("身份证", List.of("id", "card")));

    private final OpenMetadataClient client;
    private final OpenMetadataProperties props;

    /** 缓存快照：表列表 + 拉取时刻（空结果同样缓存，防离线重试风暴） */
    private record Snapshot(List<TableMetadata> tables, long fetchedAtMs) {}
    private final AtomicReference<Snapshot> cache = new AtomicReference<>();

    public MetadataContextService(OpenMetadataClient client, OpenMetadataProperties props) {
        this.client = client;
        this.props = props;
    }

    /**
     * 检索增强结果：命中的表 + 注入 LLM 的问题（无命中/关闭/降级时 question 原样返回）。
     * controller 拿 tables 写 RAG_CONTEXT trace 事件，拿 augmentedQuestion 喂 agent。
     */
    public record Augment(String augmentedQuestion, List<String> tables) {}

    public Augment augment(String question) {
        if (!props.ragEnabled()) {
            return new Augment(question, List.of());
        }
        List<TableMetadata> tables = retrieve(question);
        if (tables.isEmpty()) {
            return new Augment(question, List.of());
        }
        String context = renderContext(tables);
        String augmented = """
                【数据目录上下文（来自 OpenMetadata，仅供理解业务概念，非查询结果）】
                %s

                请基于以上背景回答用户问题：%s""".formatted(context, question);
        return new Augment(augmented, tables.stream().map(TableMetadata::fqn).toList());
    }

    /** TTL 内复用缓存；过期才拉取（含空结果缓存） */
    List<TableMetadata> snapshot() {
        Snapshot snap = cache.get();
        long ttlMs = props.cacheTtlSeconds() * 1000;
        if (snap == null || System.currentTimeMillis() - snap.fetchedAtMs() > ttlMs) {
            List<TableMetadata> fresh = client.fetchTables();
            snap = new Snapshot(fresh, System.currentTimeMillis());
            cache.set(snap);
            log.info("OpenMetadata 表元数据刷新: {} 张表", fresh.size());
        }
        return snap.tables();
    }

    /** 关键词打分取 top-K */
    List<TableMetadata> retrieve(String question) {
        List<String> qTokens = tokenize(question);
        if (qTokens.isEmpty()) {
            return List.of();
        }
        record Scored(TableMetadata table, int score) {}
        return snapshot().stream()
                .map(t -> new Scored(t, score(t, qTokens)))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(props.topK())
                .map(Scored::table)
                .toList();
    }

    private static int score(TableMetadata table, List<String> qTokens) {
        int score = 0;
        for (String token : tableTokens(table.fqn())) {
            if (qTokens.contains(token)) {
                score += 3;
            }
        }
        for (TableMetadata.Column col : table.columns()) {
            for (String token : tableTokens(col.name())) {
                if (qTokens.contains(token)) {
                    score += 2;
                }
            }
        }
        return score;
    }

    private static String renderContext(List<TableMetadata> tables) {
        StringBuilder sb = new StringBuilder();
        for (TableMetadata t : tables) {
            sb.append("表 ").append(t.fqn());
            if (!t.description().isBlank()) {
                sb.append("（").append(t.description()).append("）");
            }
            sb.append(":\n");
            for (TableMetadata.Column c : t.columns()) {
                sb.append("  - ").append(c.name());
                if (!c.description().isBlank()) {
                    sb.append(": ").append(c.description());
                }
                sb.append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    /** 问题分词：小写 + 字母数字段（≥2 字符）+ 术语表跨语言扩展（子串命中即算，免去中文分词） */
    private static List<String> tokenize(String text) {
        String q = Optional.ofNullable(text).orElse("").toLowerCase(Locale.ROOT);
        List<String> tokens = new java.util.ArrayList<>(java.util.Arrays.stream(q.split("[^\\p{L}\\p{N}]+"))
                .filter(tok -> tok.length() >= 2)
                .toList());
        for (Map.Entry<String, List<String>> entry : GLOSSARY.entrySet()) {
            if (q.contains(entry.getKey())) {
                tokens.addAll(entry.getValue());
            }
        }
        return tokens;
    }

    /** 表/列名分词：按 . _ 拆 + 去重（customer_db.customer 的 customer 重复出现只计一次，防名字膨胀刷分） */
    static List<String> tableTokens(String identifier) {
        return java.util.Arrays.stream(identifier.toLowerCase(Locale.ROOT).split("[._]+"))
                .filter(tok -> tok.length() >= 2)
                .distinct()
                .toList();
    }
}
