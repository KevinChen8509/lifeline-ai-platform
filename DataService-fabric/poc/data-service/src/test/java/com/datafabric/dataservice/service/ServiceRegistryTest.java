package com.datafabric.dataservice.service;

import com.datafabric.dataservice.metadata.OpenMetadataClient;
import com.datafabric.dataservice.metadata.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W5/W6 服务注册表单元测试：发布校验（防注入第一道闸）+ builtin 目录 + 生命周期
 * + W6-B key 生命周期（轮换/吊销/过期）/ 限流 / 计量 / 持久化灌回（InMemory 替身）。
 */
class ServiceRegistryTest {

    private OpenMetadataClient omClient;
    private ServiceRegistry registry;

    @BeforeEach
    void setUp() {
        omClient = mock(OpenMetadataClient.class);
        registry = new ServiceRegistry(omClient, new InMemoryRegistryStore());
        when(omClient.fetchTables()).thenReturn(List.of(customerTable(), ordersTable()));
    }

    private static TableMetadata customerTable() {
        return new TableMetadata("mysql.customer_db.customer", "客户基础表", List.of(
                new TableMetadata.Column("cust_id", "客户ID"),
                new TableMetadata.Column("cust_name", "客户姓名"),
                new TableMetadata.Column("phone", "手机号"),
                new TableMetadata.Column("cust_level", "客户等级"),
                new TableMetadata.Column("region", "所在地区")));
    }

    private static TableMetadata ordersTable() {
        return new TableMetadata("mysql.customer_db.orders", "客户订单表", List.of(
                new TableMetadata.Column("order_id", "订单号"),
                new TableMetadata.Column("cust_id", "客户ID"),
                new TableMetadata.Column("order_amount", "订单金额"),
                new TableMetadata.Column("order_time", "下单时间")));
    }

    private static ServiceRegistry.PublishRequest validRequest(String slug) {
        return new ServiceRegistry.PublishRequest(
                slug, "VIP 客户查询", "按等级查客户", "mysql.customer_db.customer",
                List.of("cust_id", "cust_name", "cust_level"),
                List.of(new ServiceDefinition.FilterSpec("cust_level", "eq")),
                null, 10, null, null);
    }

    private static ServiceRegistry.JoinRequest validJoin(String name) {
        return new ServiceRegistry.JoinRequest(
                "mysql.customer_db.orders", name,
                List.of("order_id", "order_amount"),
                "cust_id", "cust_id", 50);
    }

    @Test
    @DisplayName("发布：合法请求生成 table-query 定义（source/table 从 FQN 解析）")
    void publish_validRequest_createsDefinition() {
        ServiceDefinition def = registry.publish(validRequest("vip-customers"));

        assertThat(def.slug()).isEqualTo("vip-customers");
        assertThat(def.type()).isEqualTo(ServiceDefinition.TYPE_TABLE_QUERY);
        assertThat(def.source()).isEqualTo("mysql");
        assertThat(def.table()).isEqualTo("customer");
        assertThat(def.allowedColumns()).containsExactly("cust_id", "cust_name", "cust_level");
        assertThat(def.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("发布：slug 撞 builtin 或已发布条目 → 拒绝")
    void publish_duplicateSlug_rejected() {
        registry.publish(validRequest("vip-customers"));

        assertThatThrownBy(() -> registry.publish(validRequest("vip-customers")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
        assertThatThrownBy(() -> registry.publish(validRequest("customer-profile")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("发布：slug 非法字符/过短 → 拒绝")
    void publish_invalidSlug_rejected() {
        assertThatThrownBy(() -> registry.publish(validRequest("Bad_Slug")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
        assertThatThrownBy(() -> registry.publish(validRequest("a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slug");
    }

    @Test
    @DisplayName("发布：列不在 OM 元数据白名单 → 拒绝（注入 payload 同路拦下）")
    void publish_columnNotInMetadata_rejected() {
        ServiceRegistry.PublishRequest evil = new ServiceRegistry.PublishRequest(
                "evil-service", "注入尝试", "", "mysql.customer_db.customer",
                List.of("cust_id", "cust_level; DROP TABLE customer"),
                List.of(), null, 10, null, null);

        assertThatThrownBy(() -> registry.publish(evil))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("列名不合法");
    }

    @Test
    @DisplayName("发布：OM 离线（元数据空）→ 拒绝发布而非降低校验强度")
    void publish_omOffline_rejected() {
        when(omClient.fetchTables()).thenReturn(List.of());

        assertThatThrownBy(() -> registry.publish(validRequest("offline-publish")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("元数据不可用");
    }

    @Test
    @DisplayName("发布：operator 白名单外 → 拒绝")
    void publish_unknownOperator_rejected() {
        ServiceRegistry.PublishRequest req = new ServiceRegistry.PublishRequest(
                "bad-op", "非法操作符", "", "mysql.customer_db.customer",
                List.of("cust_level"),
                List.of(new ServiceDefinition.FilterSpec("cust_level", "BETWEEN")),
                null, 10, null, null);

        assertThatThrownBy(() -> registry.publish(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operator");
    }

    @Test
    @DisplayName("发布：过滤列未同时选进返回列 → 拒绝")
    void publish_filterColumnNotInAllowed_rejected() {
        ServiceRegistry.PublishRequest req = new ServiceRegistry.PublishRequest(
                "bad-filter", "过滤列越界", "", "mysql.customer_db.customer",
                List.of("cust_id"),
                List.of(new ServiceDefinition.FilterSpec("region", "eq")),
                null, 10, null, null);

        assertThatThrownBy(() -> registry.publish(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedColumns");
    }

    @Test
    @DisplayName("发布：defaultLimit 越界 → 拒绝")
    void publish_defaultLimitOutOfRange_rejected() {
        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "limit-0", "零", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), null, 0, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultLimit");
        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "limit-9999", "超限", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), null, 501, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultLimit");
    }

    @Test
    @DisplayName("目录：builtin 6 条 + 发布条目一起列出")
    void list_containsBuiltinsAndPublished() {
        registry.publish(validRequest("vip-customers"));

        List<ServiceDefinition> all = registry.list();

        assertThat(all).hasSize(7);
        assertThat(all.stream().filter(d -> ServiceDefinition.TYPE_BUILTIN.equals(d.type()))).hasSize(6);
        assertThat(all.stream().map(ServiceDefinition::slug)).contains("vip-customers", "customer-profile", "agent-raw");
    }

    @Test
    @DisplayName("下线：builtin 拒删；已发布可删；删后查无 → ServiceNotFoundException")
    void remove_lifecycle() {
        assertThatThrownBy(() -> registry.remove("customer-profile"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内置服务");

        registry.publish(validRequest("vip-customers"));
        registry.remove("vip-customers");
        assertThat(registry.find("vip-customers")).isEmpty();
        assertThatThrownBy(() -> registry.remove("vip-customers"))
                .isInstanceOf(ServiceNotFoundException.class);
    }

    @Test
    @DisplayName("调用计数：recordCall 累加，未调用为 0")
    void callCount_accumulates() {
        registry.publish(validRequest("vip-customers"));
        assertThat(registry.callCount("vip-customers")).isZero();

        registry.recordCall("vip-customers");
        registry.recordCall("vip-customers");
        assertThat(registry.callCount("vip-customers")).isEqualTo(2);
    }

    // ============ W6 融合发布校验 ============

    @Test
    @DisplayName("融合发布：合法 joins → TYPE_FUSION + 生成服务级 apiKey（sk-w6- 前缀）")
    void publish_validFusion_createsFusionDefinitionWithKey() {
        ServiceDefinition def = registry.publish(new ServiceRegistry.PublishRequest(
                "customer-orders-fusion", "客户订单融合", "客户+订单", "mysql.customer_db.customer",
                List.of("cust_id", "cust_name", "cust_level"),
                List.of(new ServiceDefinition.FilterSpec("cust_id", "eq")),
                List.of(validJoin("orders")), 10, null, null));

        assertThat(def.type()).isEqualTo(ServiceDefinition.TYPE_FUSION);
        assertThat(def.joins()).hasSize(1);
        assertThat(def.joins().get(0).name()).isEqualTo("orders");
        assertThat(def.joins().get(0).limitPerParent()).isEqualTo(50);
        assertThat(def.apiKey()).startsWith("sk-w6-").hasSize(6 + 32);
        assertThat(registry.isValidServiceKey("customer-orders-fusion", def.apiKey())).isTrue();
        assertThat(registry.isValidServiceKey("customer-orders-fusion", "sk-w6-wrong")).isFalse();
        assertThat(registry.isValidServiceKey("other-slug", def.apiKey())).isFalse();
        assertThat(registry.isValidServiceKey("customer-profile", def.apiKey())).isFalse();
    }

    @Test
    @DisplayName("融合发布：单表（joins 空）也发 apiKey，type 仍为 table-query")
    void publish_tableQuery_alsoGetsApiKey() {
        ServiceDefinition def = registry.publish(validRequest("keyed-single"));

        assertThat(def.type()).isEqualTo(ServiceDefinition.TYPE_TABLE_QUERY);
        assertThat(def.apiKey()).startsWith("sk-w6-");
    }

    @Test
    @DisplayName("融合发布：从表列不在从表元数据 → 拒绝（注入列同路拦下）")
    void publish_joinColumnNotInJoinMetadata_rejected() {
        ServiceRegistry.JoinRequest evil = new ServiceRegistry.JoinRequest(
                "mysql.customer_db.orders", "orders",
                List.of("order_id", "cust_id; DROP TABLE orders"),
                "cust_id", "cust_id", 50);

        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "evil-join", "注入尝试", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), List.of(evil), 10, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("列名不合法");
    }

    @Test
    @DisplayName("融合发布：joinColumn 不在从表元数据 → 拒绝")
    void publish_joinJoinColumnNotInMetadata_rejected() {
        ServiceRegistry.JoinRequest bad = new ServiceRegistry.JoinRequest(
                "mysql.customer_db.orders", "orders",
                List.of("order_id"), "evil_col", "cust_id", 50);

        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "bad-joincol", "坏关联键", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), List.of(bad), 10, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("joinColumn");
    }

    @Test
    @DisplayName("融合发布：parentColumn 不在主表 allowedColumns → 拒绝")
    void publish_parentColumnNotInMainColumns_rejected() {
        ServiceRegistry.JoinRequest bad = new ServiceRegistry.JoinRequest(
                "mysql.customer_db.orders", "orders",
                List.of("order_id"), "cust_id", "phone", 50);

        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "bad-parent", "坏主表键", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), List.of(bad), 10, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parentColumn");
    }

    @Test
    @DisplayName("融合发布：joins 超过 2 个 / name 重复 → 拒绝")
    void publish_tooManyOrDuplicateJoins_rejected() {
        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "join-3", "三个从表", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(),
                List.of(validJoin("a"), validJoin("b"), validJoin("c")), 10, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多");

        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "join-dup", "重名从表", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(),
                List.of(validJoin("orders"), validJoin("orders")), 10, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
    }

    @Test
    @DisplayName("融合发布：limitPerParent 越界 / 从表元数据不可用 → 拒绝")
    void publish_joinBoundsAndOffline_rejected() {
        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "per-999", "从行超限", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(),
                List.of(new ServiceRegistry.JoinRequest(
                        "mysql.customer_db.orders", "orders",
                        List.of("order_id"), "cust_id", "cust_id", 999)), 10, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limitPerParent");

        when(omClient.fetchTables()).thenReturn(List.of(customerTable()));
        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "join-offline", "从表离线", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), List.of(validJoin("orders")), 10, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("从表目录元数据不可用");
    }

    // ============ W6-B key 生命周期 / 限流 / 计量 / 持久化 ============

    @Test
    @DisplayName("KeyPolicy.isUsable 矩阵：ACTIVE+未过期 ✓ / 过期 ✗ / REVOKED ✗")
    void keyPolicy_isUsableMatrix() {
        assertThat(new ServiceDefinition.KeyPolicy(
                ServiceDefinition.KeyPolicy.STATUS_ACTIVE, null, 60).isUsable()).isTrue();
        assertThat(new ServiceDefinition.KeyPolicy(
                ServiceDefinition.KeyPolicy.STATUS_ACTIVE,
                Instant.now().plusSeconds(3600), 60).isUsable()).isTrue();
        assertThat(new ServiceDefinition.KeyPolicy(
                ServiceDefinition.KeyPolicy.STATUS_ACTIVE,
                Instant.now().minusSeconds(60), 60).isUsable()).isFalse();
        assertThat(new ServiceDefinition.KeyPolicy(
                ServiceDefinition.KeyPolicy.STATUS_REVOKED, null, 60).isUsable()).isFalse();
    }

    @Test
    @DisplayName("发布策略：keyTtlHours=24 → 过期时刻 ≈ now+24h；默认限流 60/分")
    void publish_withTtlAndRate_policyPopulated() {
        Instant before = Instant.now();
        ServiceDefinition def = registry.publish(new ServiceRegistry.PublishRequest(
                "policy-service", "带策略", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), null, 10, null, 24L));

        assertThat(def.keyPolicy()).isNotNull();
        assertThat(def.keyPolicy().status()).isEqualTo(ServiceDefinition.KeyPolicy.STATUS_ACTIVE);
        assertThat(def.keyPolicy().rateLimitPerMin()).isEqualTo(60);
        // 24h ± 容差（测试执行耗时）
        assertThat(def.keyPolicy().expiresAt()).isAfter(before.plusSeconds(24 * 3600 - 60));
        assertThat(def.keyPolicy().expiresAt()).isBefore(Instant.now().plusSeconds(24 * 3600 + 60));
    }

    @Test
    @DisplayName("发布策略：rateLimitPerMin 越界（0/601）→ 拒绝")
    void publish_rateLimitOutOfRange_rejected() {
        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "rate-0", "零限流", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), null, 10, 0, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rateLimitPerMin");
        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "rate-601", "超限流", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), null, 10, 601, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rateLimitPerMin");
    }

    @Test
    @DisplayName("轮换：新 key 生效、旧 key 立即失效；builtin 404；未知 slug 404")
    void rotateKey_swapsKeyImmediately() {
        ServiceDefinition def = registry.publish(validRequest("rotate-me"));
        String oldKey = def.apiKey();
        assertThat(registry.isValidServiceKey("rotate-me", oldKey)).isTrue();

        ServiceDefinition rotated = registry.rotateKey("rotate-me", null);

        assertThat(rotated.apiKey()).isNotEqualTo(oldKey).startsWith("sk-w6-");
        assertThat(registry.isValidServiceKey("rotate-me", oldKey)).isFalse();
        assertThat(registry.isValidServiceKey("rotate-me", rotated.apiKey())).isTrue();
        assertThatThrownBy(() -> registry.rotateKey("customer-profile", null))
                .isInstanceOf(ServiceNotFoundException.class);
        assertThatThrownBy(() -> registry.rotateKey("no-such-slug", null))
                .isInstanceOf(ServiceNotFoundException.class);
    }

    @Test
    @DisplayName("吊销：key 立即失效；rotate 可复活（新 key ACTIVE）")
    void revokeKey_invalidatesAndRotateRevives() {
        ServiceDefinition def = registry.publish(validRequest("revoke-me"));

        registry.revokeKey("revoke-me");
        assertThat(registry.isValidServiceKey("revoke-me", def.apiKey())).isFalse();
        assertThat(registry.find("revoke-me")).isPresent(); // 服务本身还在

        ServiceDefinition revived = registry.rotateKey("revoke-me", null);
        assertThat(revived.keyPolicy().status()).isEqualTo(ServiceDefinition.KeyPolicy.STATUS_ACTIVE);
        assertThat(registry.isValidServiceKey("revoke-me", revived.apiKey())).isTrue();
    }

    @Test
    @DisplayName("限流：rateLimitPerMin=2 → 第 3 次 tryAcquire 拒绝；builtin 不限")
    void tryAcquire_fixedWindowClamps() {
        registry.publish(new ServiceRegistry.PublishRequest(
                "limited-svc", "限流服务", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), null, 10, 2, null));

        assertThat(registry.tryAcquire("limited-svc")).isTrue();
        assertThat(registry.tryAcquire("limited-svc")).isTrue();
        assertThat(registry.tryAcquire("limited-svc")).isFalse();
        // builtin 无 key 策略 → 不限流
        assertThat(registry.tryAcquire("customer-profile")).isTrue();
    }

    @Test
    @DisplayName("计量：recordCall 2 次 → usage 总量=今日=2")
    void usage_countsCalls() {
        registry.publish(validRequest("metered-svc"));

        registry.recordCall("metered-svc");
        registry.recordCall("metered-svc");

        ServiceRegistryStore.UsageSummary usage = registry.usage("metered-svc");
        assertThat(usage.totalCalls()).isEqualTo(2);
        assertThat(usage.todayCalls()).isEqualTo(2);
        assertThat(usage.recentDays()).hasSize(1);
    }

    @Test
    @DisplayName("持久化灌回：同 store 重建 registry → 服务与 key 原样恢复")
    void loadPersisted_restoresServicesWithSameKey() {
        InMemoryRegistryStore store = new InMemoryRegistryStore();
        ServiceRegistry first = new ServiceRegistry(omClient, store);
        when(omClient.fetchTables()).thenReturn(List.of(customerTable(), ordersTable()));
        ServiceDefinition def = first.publish(validRequest("persist-me"));

        // 模拟重启：同一存储上的新 registry 实例执行 @PostConstruct 装载
        ServiceRegistry second = new ServiceRegistry(omClient, store);
        second.loadPersisted();

        assertThat(second.find("persist-me")).isPresent();
        assertThat(second.find("persist-me").orElseThrow().apiKey()).isEqualTo(def.apiKey());
        assertThat(second.isValidServiceKey("persist-me", def.apiKey())).isTrue();
    }
}
