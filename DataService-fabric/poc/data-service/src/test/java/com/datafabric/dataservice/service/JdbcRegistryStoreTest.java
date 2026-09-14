package com.datafabric.dataservice.service;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W6-B 持久化语义单测：JdbcRegistryStore 在 H2（MODE=MySQL）上
 * save → loadAll 往返一致（含 joins / KeyPolicy 过期时刻）/ delete / 用量累计。
 */
class JdbcRegistryStoreTest {

    private static final String H2_URL =
            "jdbc:h2:mem:w6bstore;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static HikariDataSource pool;

    @BeforeAll
    static void setUpPool() {
        pool = new HikariDataSource();
        pool.setJdbcUrl(H2_URL);
        pool.setUsername("sa");
        pool.setPassword("");
        pool.setMaximumPoolSize(2);
    }

    @AfterAll
    static void closePool() {
        if (pool != null) {
            pool.close();
        }
    }

    private static ServiceDefinition sampleFusion(String slug) {
        return sampleFusion(slug, ServiceKeys.sha256Hex("sk-w6-roundtrip0001"));
    }

    /** Blocker 2：apiKeyHash 参数形态即库内形态（哈希）；传明文格式值仅用于模拟存量旧行 */
    private static ServiceDefinition sampleFusion(String slug, String apiKeyHash) {
        return new ServiceDefinition(
                slug, "客户订单融合", "持久化往返", ServiceDefinition.TYPE_FUSION, null, null,
                "mysql", "customer_db", "customer",
                List.of("cust_id", "cust_name"),
                List.of(new ServiceDefinition.FilterSpec("cust_id", "eq")),
                List.of(new ServiceDefinition.JoinSpec(
                        "mysql.customer_db.orders", "orders",
                        List.of("order_id", "order_amount"), "cust_id", "cust_id", 20)),
                List.of(),
                10, apiKeyHash,
                new ServiceDefinition.KeyPolicy(
                        ServiceDefinition.KeyPolicy.STATUS_ACTIVE,
                        Instant.parse("2027-01-01T00:00:00Z"), 42),
                Instant.parse("2026-09-04T00:00:00Z"));
    }

    @Test
    @DisplayName("往返：save → loadAll 定义逐字段一致（joins/KeyPolicy/createdAt）")
    void saveThenLoadAll_roundTrips() {
        JdbcRegistryStore store = new JdbcRegistryStore(pool);
        ServiceDefinition original = sampleFusion("roundtrip-fusion");
        store.save(original);

        List<ServiceDefinition> loaded = store.loadAll();

        ServiceDefinition restored = loaded.stream()
                .filter(d -> d.slug().equals("roundtrip-fusion"))
                .findFirst().orElseThrow();
        assertThat(restored.name()).isEqualTo(original.name());
        assertThat(restored.type()).isEqualTo(ServiceDefinition.TYPE_FUSION);
        assertThat(restored.source()).isEqualTo("mysql");
        assertThat(restored.database()).isEqualTo("customer_db"); // W6-D：FQN 中段落库
        assertThat(restored.table()).isEqualTo("customer");
        assertThat(restored.allowedColumns()).containsExactly("cust_id", "cust_name");
        assertThat(restored.filters()).containsExactly(new ServiceDefinition.FilterSpec("cust_id", "eq"));
        assertThat(restored.joins()).hasSize(1);
        ServiceDefinition.JoinSpec join = restored.joins().get(0);
        assertThat(join.fqn()).isEqualTo("mysql.customer_db.orders");
        assertThat(join.columns()).containsExactly("order_id", "order_amount");
        assertThat(join.joinColumn()).isEqualTo("cust_id");
        assertThat(join.parentColumn()).isEqualTo("cust_id");
        assertThat(join.limitPerParent()).isEqualTo(20);
        assertThat(restored.apiKeyHash()).isEqualTo(ServiceKeys.sha256Hex("sk-w6-roundtrip0001"));
        assertThat(restored.keyPolicy().status()).isEqualTo(ServiceDefinition.KeyPolicy.STATUS_ACTIVE);
        assertThat(restored.keyPolicy().expiresAt()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(restored.keyPolicy().rateLimitPerMin()).isEqualTo(42);
        assertThat(restored.createdAt()).isEqualTo(Instant.parse("2026-09-04T00:00:00Z"));
    }

    @Test
    @DisplayName("覆盖写：同 slug 二次 save（rotate 后）→ loadAll 只有一条且是新值")
    void saveUpsert_overwritesSameSlug() {
        JdbcRegistryStore store = new JdbcRegistryStore(pool);
        store.save(sampleFusion("upsert-svc"));
        ServiceDefinition rotated = new ServiceDefinition(
                "upsert-svc", "新 key 版", "", ServiceDefinition.TYPE_TABLE_QUERY, null, null,
                "mysql", "customer_db", "customer",
                List.of("cust_id"), List.of(), List.of(), List.of(), 10,
                ServiceKeys.sha256Hex("sk-w6-newkey0002"),
                new ServiceDefinition.KeyPolicy(
                        ServiceDefinition.KeyPolicy.STATUS_REVOKED, null, 60),
                Instant.parse("2026-09-04T00:00:00Z"));
        store.save(rotated);

        List<ServiceDefinition> loaded = store.loadAll().stream()
                .filter(d -> d.slug().equals("upsert-svc")).toList();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).apiKeyHash()).isEqualTo(ServiceKeys.sha256Hex("sk-w6-newkey0002"));
        assertThat(loaded.get(0).keyPolicy().status()).isEqualTo(ServiceDefinition.KeyPolicy.STATUS_REVOKED);
    }

    @Test
    @DisplayName("删除：delete 后 loadAll 无此条")
    void delete_removesRow() {
        JdbcRegistryStore store = new JdbcRegistryStore(pool);
        store.save(sampleFusion("delete-me"));
        assertThat(store.loadAll().stream().anyMatch(d -> d.slug().equals("delete-me"))).isTrue();

        store.delete("delete-me");

        assertThat(store.loadAll().stream().anyMatch(d -> d.slug().equals("delete-me"))).isFalse();
    }

    @Test
    @DisplayName("用量：recordUsage 3 次 → usage 总量=今日=3；未知 slug 全 0")
    void recordUsage_accumulatesDaily() {
        JdbcRegistryStore store = new JdbcRegistryStore(pool);
        store.recordUsage("usage-svc");
        store.recordUsage("usage-svc");
        store.recordUsage("usage-svc");

        ServiceRegistryStore.UsageSummary usage = store.usage("usage-svc");
        assertThat(usage.totalCalls()).isEqualTo(3);
        assertThat(usage.todayCalls()).isEqualTo(3);
        assertThat(usage.recentDays()).hasSize(1);

        ServiceRegistryStore.UsageSummary empty = store.usage("no-such-svc");
        assertThat(empty.totalCalls()).isZero();
        assertThat(empty.recentDays()).isEmpty();
    }

    // ============ W6-C 聚合列持久化 ============

    private static ServiceDefinition sampleAggregate(String slug) {
        return new ServiceDefinition(
                slug, "按客户聚合订单", "W6-C 往返", ServiceDefinition.TYPE_AGGREGATE, null, null,
                "mysql", "customer_db", "orders",
                List.of("cust_id"),
                List.of(),
                List.of(),
                List.of(
                        new ServiceDefinition.AggSpec("COUNT", null, "order_count"),
                        new ServiceDefinition.AggSpec("SUM", "order_amount", "total_amount")),
                10, ServiceKeys.sha256Hex("sk-w6-aggtrip0003"),
                new ServiceDefinition.KeyPolicy(
                        ServiceDefinition.KeyPolicy.STATUS_ACTIVE, null, 60),
                Instant.parse("2026-09-04T00:00:00Z"));
    }

    @Test
    @DisplayName("聚合往返：save → loadAll aggregates 逐字段一致（COUNT 星号保留 null）")
    void aggregateRoundTrip_preservesAggSpecs() {
        JdbcRegistryStore store = new JdbcRegistryStore(pool);
        store.save(sampleAggregate("roundtrip-agg"));

        ServiceDefinition restored = store.loadAll().stream()
                .filter(d -> d.slug().equals("roundtrip-agg"))
                .findFirst().orElseThrow();

        assertThat(restored.type()).isEqualTo(ServiceDefinition.TYPE_AGGREGATE);
        assertThat(restored.aggregates()).hasSize(2);
        assertThat(restored.aggregates().get(0).function()).isEqualTo("COUNT");
        assertThat(restored.aggregates().get(0).column()).isNull(); // COUNT(*) 星号形态
        assertThat(restored.aggregates().get(0).alias()).isEqualTo("order_count");
        assertThat(restored.aggregates().get(0).sqlExpr()).isEqualTo("COUNT(*)");
        assertThat(restored.aggregates().get(1).sqlExpr()).isEqualTo("SUM(`order_amount`)");
    }

    @Test
    @DisplayName("旧表迁移：W6-B 15 列旧表 ALTER 追加 aggregates（物理在表尾）→ save 聚合不错位 + 旧行兜底")
    void legacyTable_alterAppendsAggregatesAtEnd_saveStillBindsCorrectly() throws Exception {
        JdbcRegistryStore store = new JdbcRegistryStore(pool);
        // 重建 W6-B 时代的 15 列旧表（无 aggregates 列）—— 模拟生产库真实迁移前形态
        try (var conn = pool.getConnection(); var st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS `service_def`");
            st.execute("""
                    CREATE TABLE `service_def` (
                      `slug` VARCHAR(64) PRIMARY KEY,
                      `name` VARCHAR(256) NOT NULL,
                      `description` VARCHAR(1024),
                      `type` VARCHAR(16) NOT NULL,
                      `source` VARCHAR(16) NOT NULL,
                      `tbl` VARCHAR(64) NOT NULL,
                      `allowed_columns` VARCHAR(4096) NOT NULL,
                      `filters` VARCHAR(4096) NOT NULL,
                      `joins` VARCHAR(8192) NOT NULL,
                      `default_limit` INT NOT NULL,
                      `api_key` VARCHAR(128),
                      `key_status` VARCHAR(16),
                      `key_expires_at` TIMESTAMP,
                      `rate_limit_per_min` INT NOT NULL,
                      `created_at` TIMESTAMP NOT NULL
                    )""");
            // 旧行：W6-C 前落库的数据（等价 aggregates=NULL）
            st.execute("""
                    INSERT INTO `service_def` VALUES ('legacy-w6b-row', '旧融合', '', 'fusion', 'mysql', 'customer',
                      '[\"cust_id\"]', '[]', '[]', 10, 'sk-w6-legacy0004',
                      'ACTIVE', NULL, 60, '2026-09-03 00:00:00')""");
        }

        // store.save 触发 ensureSchema：ALTER 追加 aggregates 到表尾 + MERGE（显式列清单，不随物理列序错位）
        store.save(sampleAggregate("agg-on-migrated-table"));

        ServiceDefinition legacy = store.loadAll().stream()
                .filter(d -> d.slug().equals("legacy-w6b-row"))
                .findFirst().orElseThrow();
        ServiceDefinition agg = store.loadAll().stream()
                .filter(d -> d.slug().equals("agg-on-migrated-table"))
                .findFirst().orElseThrow();

        // 旧行：aggregates NULL → 兜底空列表，不炸反序列化；db_name 同样 NULL → database=null
        assertThat(legacy.type()).isEqualTo(ServiceDefinition.TYPE_FUSION);
        assertThat(legacy.aggregates()).isEmpty();
        assertThat(legacy.database()).isNull();
        // 新行：聚合定义逐字段落库（曾因列序错位报 Data conversion 降级 —— E2E 实证）
        assertThat(agg.type()).isEqualTo(ServiceDefinition.TYPE_AGGREGATE);
        assertThat(agg.aggregates()).hasSize(2);
        assertThat(agg.aggregates().get(0).sqlExpr()).isEqualTo("COUNT(*)");
        assertThat(agg.aggregates().get(1).sqlExpr()).isEqualTo("SUM(`order_amount`)");
        assertThat(agg.defaultLimit()).isEqualTo(10); // 错位时 JSON 曾落进 DEFAULT_LIMIT
        assertThat(agg.database()).isEqualTo("customer_db"); // W6-D：db_name 列也经 ALTER 追加在表尾
    }

    // ============ Blocker 2：key 哈希化 + 存量明文自愈迁移 ============

    @Test
    @DisplayName("明文迁移：库内 sk-w6- 明文行 → loadAll 就地 UPDATE 为哈希，原 key 继续有效，幂等")
    void legacyPlaintextKey_isHashedOnLoad_idempotent() {
        JdbcRegistryStore store = new JdbcRegistryStore(pool);
        // 形态合法的明文（sk-w6- + 32 hex）—— 模拟 W6-B~W6-D 落库的旧行
        String plaintext = "sk-w6-0123456789abcdef0123456789abcdef";
        String expectedHash = ServiceKeys.sha256Hex(plaintext);
        store.save(sampleFusion("legacy-plain-key", plaintext));

        ServiceDefinition migrated = store.loadAll().stream()
                .filter(d -> d.slug().equals("legacy-plain-key"))
                .findFirst().orElseThrow();

        assertThat(migrated.apiKeyHash()).isEqualTo(expectedHash).hasSize(64);
        assertThat(migrated.apiKeyHash()).isNotEqualTo(plaintext);

        // 幂等：库内已是哈希 → 再 load 不变形（哈希形态不会被误判为明文）
        ServiceDefinition again = store.loadAll().stream()
                .filter(d -> d.slug().equals("legacy-plain-key"))
                .findFirst().orElseThrow();
        assertThat(again.apiKeyHash()).isEqualTo(expectedHash);
    }

    @Test
    @DisplayName("哈希校验闭环：明文经 ServiceKeys.sha256Hex 后与库内哈希恒等（isValidServiceKey 同路）")
    void sha256_isStableForSamePlaintext() {
        String plaintext = "sk-w6-abcdef0123456789abcdef0123456789";
        assertThat(ServiceKeys.sha256Hex(plaintext)).isEqualTo(ServiceKeys.sha256Hex(plaintext));
        assertThat(ServiceKeys.sha256Hex(plaintext)).hasSize(64);
        assertThat(ServiceKeys.isPlaintextFormat(plaintext)).isTrue();
        assertThat(ServiceKeys.isPlaintextFormat(ServiceKeys.sha256Hex(plaintext))).isFalse();
        assertThat(ServiceKeys.isPlaintextFormat("sk-w6-roundtrip0001")).isFalse(); // 非 32 hex 不误判
        assertThat(ServiceKeys.isPlaintextFormat(null)).isFalse();
    }
}
