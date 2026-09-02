package com.datafabric.dataservice.service;

import com.datafabric.dataservice.metadata.OpenMetadataClient;
import com.datafabric.dataservice.metadata.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W5 服务注册表单元测试：发布校验（防注入第一道闸）+ builtin 目录 + 生命周期。
 */
class ServiceRegistryTest {

    private OpenMetadataClient omClient;
    private ServiceRegistry registry;

    @BeforeEach
    void setUp() {
        omClient = mock(OpenMetadataClient.class);
        registry = new ServiceRegistry(omClient);
        when(omClient.fetchTables()).thenReturn(List.of(customerTable()));
    }

    private static TableMetadata customerTable() {
        return new TableMetadata("mysql.customer_db.customer", "客户基础表", List.of(
                new TableMetadata.Column("cust_id", "客户ID"),
                new TableMetadata.Column("cust_name", "客户姓名"),
                new TableMetadata.Column("phone", "手机号"),
                new TableMetadata.Column("cust_level", "客户等级"),
                new TableMetadata.Column("region", "所在地区")));
    }

    private static ServiceRegistry.PublishRequest validRequest(String slug) {
        return new ServiceRegistry.PublishRequest(
                slug, "VIP 客户查询", "按等级查客户", "mysql.customer_db.customer",
                List.of("cust_id", "cust_name", "cust_level"),
                List.of(new ServiceDefinition.FilterSpec("cust_level", "eq")),
                10);
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
                List.of(), 10);

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
                List.of(new ServiceDefinition.FilterSpec("cust_level", "BETWEEN")), 10);

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
                List.of(new ServiceDefinition.FilterSpec("region", "eq")), 10);

        assertThatThrownBy(() -> registry.publish(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedColumns");
    }

    @Test
    @DisplayName("发布：defaultLimit 越界 → 拒绝")
    void publish_defaultLimitOutOfRange_rejected() {
        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "limit-0", "零", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("defaultLimit");
        assertThatThrownBy(() -> registry.publish(new ServiceRegistry.PublishRequest(
                "limit-9999", "超限", "", "mysql.customer_db.customer",
                List.of("cust_id"), List.of(), 501)))
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
}
