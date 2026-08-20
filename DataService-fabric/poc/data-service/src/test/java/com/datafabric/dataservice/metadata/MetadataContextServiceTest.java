package com.datafabric.dataservice.metadata;

import com.datafabric.dataservice.config.OpenMetadataProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * F3 元数据 RAG 单测：MockRestServiceServer 桩 OpenMetadata（无需真服务，走真实解析/检索代码）。
 */
class MetadataContextServiceTest {

    private static final String OM_TABLES_JSON = """
            {"data":[
              {"fullyQualifiedName":"mysql.customer_db.customer",
               "description":"客户基础表：姓名、手机号、身份证、等级、地区",
               "columns":[
                 {"name":"cust_id","description":"客户ID"},
                 {"name":"cust_name","description":"客户姓名"},
                 {"name":"phone","description":"手机号"},
                 {"name":"cust_level","description":"客户等级 VIP1-VIP3"},
                 {"name":"region","description":"所在地区"}]},
              {"fullyQualifiedName":"clickhouse.orders_db.orders",
               "description":"客户订单事实表",
               "columns":[
                 {"name":"order_id","description":"订单号"},
                 {"name":"cust_id","description":"客户ID"},
                 {"name":"order_amount","description":"订单金额"},
                 {"name":"order_time","description":"下单时间"}]},
              {"fullyQualifiedName":"postgres.external.risk_tags",
               "description":"客户风险标签",
               "columns":[
                 {"name":"cust_id","description":"客户ID"},
                 {"name":"risk_level","description":"风险等级 high/medium/low"},
                 {"name":"risk_score","description":"风险分 0-100"}]}
            ]}""";

    private MockRestServiceServer server;
    private MetadataContextService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://om-stub/api");
        server = MockRestServiceServer.bindTo(builder).build();
        OpenMetadataClient client = new OpenMetadataClient(props(true), builder.build());
        service = new MetadataContextService(client, props(true));
    }

    private static OpenMetadataProperties props(boolean enabled) {
        return new OpenMetadataProperties("http://om-stub/api", null, enabled, 300, 3, 200);
    }

    @Test
    @DisplayName("正常拉取：中文问题经术语表桥接，订单表按相关性排第一并注入 prompt")
    void augment_ordersQuestion_injectsOrdersTable() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/tables")))
                .andExpect(method(GET))
                .andExpect(queryParam("fields", "columns"))
                .andRespond(withSuccess(OM_TABLES_JSON, APPLICATION_JSON));

        MetadataContextService.Augment aug = service.augment("统计客户 C0001 的订单金额和下单时间");

        // 客户/订单两表都命中，但订单表（order/amount/time 多列命中）得分更高排第一
        assertThat(aug.tables()).isNotEmpty();
        assertThat(aug.tables().get(0)).isEqualTo("clickhouse.orders_db.orders");
        assertThat(aug.augmentedQuestion())
                .contains("【数据目录上下文")
                .contains("clickhouse.orders_db.orders")
                .contains("order_amount: 订单金额")
                .endsWith("统计客户 C0001 的订单金额和下单时间");
        server.verify();
    }

    @Test
    @DisplayName("TTL 缓存：两次检索只发一次 OM 请求")
    void cache_twoRetrieves_singleHttpRequest() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/v1/tables")))
                .andRespond(withSuccess(OM_TABLES_JSON, APPLICATION_JSON));

        service.augment("VIP3 客户有哪些");
        service.augment("高风险客户预警");

        server.verify();
    }

    @Test
    @DisplayName("多表命中：按得分排序，问题关键词决定顺序")
    void ranking_riskQuestion_riskTableFirst() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/v1/tables")))
                .andRespond(withSuccess(OM_TABLES_JSON, APPLICATION_JSON));

        List<TableMetadata> top = service.retrieve("risk_level high 的客户及其 risk_score");

        assertThat(top).isNotEmpty();
        assertThat(top.get(0).fqn()).isEqualTo("postgres.external.risk_tags");
    }

    @Test
    @DisplayName("OM 5xx：优雅降级，问题原样透传")
    void degrade_omDown_passesQuestionThrough() {
        server.expect(once(), requestTo(org.hamcrest.Matchers.containsString("/v1/tables")))
                .andRespond(withServerError());

        MetadataContextService.Augment aug = service.augment("客户总数是多少");

        assertThat(aug.tables()).isEmpty();
        assertThat(aug.augmentedQuestion()).isEqualTo("客户总数是多少");
        server.verify();
    }

    @Test
    @DisplayName("开关关闭：不发任何 OM 请求，直接透传")
    void disabled_noHttpRequest() {
        service = new MetadataContextService(null, props(false));

        MetadataContextService.Augment aug = service.augment("客户总数是多少");

        assertThat(aug.tables()).isEmpty();
        assertThat(aug.augmentedQuestion()).isEqualTo("客户总数是多少");
    }
}
