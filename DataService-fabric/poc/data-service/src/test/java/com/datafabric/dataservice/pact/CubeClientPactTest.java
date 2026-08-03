package com.datafabric.dataservice.pact;

import au.com.dius.pact.consumer.dsl.PactDslWithEntity;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import com.datafabric.dataservice.client.CubeClient;
import com.datafabric.dataservice.client.CubeClient.CubeQuery;
import com.datafabric.dataservice.config.CubeProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consumer 端 Pact 测试 - 验证 data-service 对 Cube.dev REST API 的调用契约
 *
 * 关键作用：
 *   1. 文档化 data-service 期望的 Cube 请求/响应格式
 *   2. Cube 升级（0.36 → 0.37+）时，重跑 pact 即可发现破坏性变更
 *   3. 生成的 pact JSON 可被 Cube provider 端验证（双向契约测试）
 *
 * Provider 端验证（Cube 自己 replay pacts）留给后续 iteration；
 * PoC 只做 consumer 端，pact JSON 输出到 target/pacts/。
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "cube-dev", port = "8888")
class CubeClientPactTest {

    private static final String AUTH_HEADER_REGEX = "Bearer .+";
    private static final String PATH = "/cubejs-api/v1/load";

    private CubeClient clientFor(int port) {
        return new CubeClient(new CubeProperties("localhost", port, "datafabric-poc-secret-2026", 5000));
    }

    // ============ 场景 1：customerCount 单 measure 查询 ============

    @Pact(consumer = "data-service")
    RequestResponsePact customerCountPact(PactDslWithEntity builder) {
        return builder
                .given("customer 表已加载 1000 行")
                .uponReceiving("查询客户总数（customerCount）")
                .method("POST")
                .path(PATH)
                .matchHeader("Authorization", AUTH_HEADER_REGEX)
                .matchHeader("Content-Type", "application/json")
                .body("""
                        {
                          "query": {
                            "measures": ["CustomerProfile.customerCount"]
                          }
                        }
                        """)
                .willRespondWith()
                .status(200)
                .matchHeader("Content-Type", "application/json")
                .body("""
                        {
                          "query": {
                            "measures": ["CustomerProfile.customerCount"]
                          },
                          "data": [
                            {"CustomerProfile.customerCount": 1000}
                          ],
                          "annotation": {
                            "measures": {
                              "CustomerProfile.customerCount": {"title": "Customer Count"}
                            }
                          }
                        }
                        """)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "customerCountPact")
    void testCustomerCount() {
        CubeClient client = clientFor(8888);
        List<Map<String, Object>> rows = client.load(
                CubeQuery.builder().measure("CustomerProfile.customerCount").build()
        );
        assertEquals(1, rows.size());
        assertEquals(1000L, rows.get(0).get("CustomerProfile.customerCount"));
    }

    // ============ 场景 2：单客户 360° 画像 ============

    @Pact(consumer = "data-service")
    RequestResponsePact customerProfilePact(PactDslWithEntity builder) {
        return builder
                .given("客户 C0001 存在，跨源 JOIN 完整")
                .uponReceiving("查询单客户画像（custId 过滤）")
                .method("POST")
                .path(PATH)
                .matchHeader("Authorization", AUTH_HEADER_REGEX)
                .matchHeader("Content-Type", "application/json")
                .body("""
                        {
                          "query": {
                            "measures": ["CustomerProfile.totalRevenue"],
                            "dimensions": [
                              "CustomerProfile.custId",
                              "CustomerProfile.custName",
                              "CustomerProfile.phone",
                              "CustomerProfile.customerLevel",
                              "CustomerProfile.region",
                              "CustomerProfile.riskLevel"
                            ],
                            "filters": [
                              {
                                "member": "CustomerProfile.custId",
                                "operator": "equals",
                                "values": ["C0001"]
                              }
                            ],
                            "limit": 1
                          }
                        }
                        """)
                .willRespondWith()
                .status(200)
                .body("""
                        {
                          "data": [
                            {
                              "CustomerProfile.custId": "C0001",
                              "CustomerProfile.custName": "李娜",
                              "CustomerProfile.phone": "13800000001",
                              "CustomerProfile.customerLevel": "VIP3",
                              "CustomerProfile.region": "华东",
                              "CustomerProfile.riskLevel": "high",
                              "CustomerProfile.totalRevenue": 128000.0
                            }
                          ]
                        }
                        """)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "customerProfilePact")
    void testCustomerProfile() {
        CubeClient client = clientFor(8888);
        List<Map<String, Object>> rows = client.load(CubeQuery.builder()
                .measure("CustomerProfile.totalRevenue")
                .dimension("CustomerProfile.custId")
                .dimension("CustomerProfile.custName")
                .dimension("CustomerProfile.phone")
                .dimension("CustomerProfile.customerLevel")
                .dimension("CustomerProfile.region")
                .dimension("CustomerProfile.riskLevel")
                .filter("CustomerProfile.custId", "equals", "C0001")
                .limit(1)
                .build());

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("C0001", row.get("CustomerProfile.custId"));
        assertEquals("李娜", row.get("CustomerProfile.custName"));
        assertEquals("VIP3", row.get("CustomerProfile.customerLevel"));
        assertEquals("high", row.get("CustomerProfile.riskLevel"));
        assertEquals(128000.0, row.get("CustomerProfile.totalRevenue"));
    }

    // ============ 场景 3：VIP3 客户分群查询 ============

    @Pact(consumer = "data-service")
    RequestResponsePact vip3SearchPact(PactDslWithEntity builder) {
        return builder
                .given("VIP3 客户分群数据存在")
                .uponReceiving("查询 VIP3 客户列表（分页 size=5）")
                .method("POST")
                .path(PATH)
                .matchHeader("Authorization", AUTH_HEADER_REGEX)
                .body("""
                        {
                          "query": {
                            "measures": ["CustomerProfile.totalRevenue"],
                            "dimensions": ["CustomerProfile.custId", "CustomerProfile.custName"],
                            "filters": [
                              {
                                "member": "CustomerProfile.customerLevel",
                                "operator": "equals",
                                "values": ["VIP3"]
                              }
                            ],
                            "order": {"CustomerProfile.totalRevenue": "desc"},
                            "limit": 5,
                            "offset": 0
                          }
                        }
                        """)
                .willRespondWith()
                .status(200)
                .body("""
                        {
                          "data": [
                            {"CustomerProfile.custId": "C0050", "CustomerProfile.custName": "王芳", "CustomerProfile.totalRevenue": 95000.0},
                            {"CustomerProfile.custId": "C0042", "CustomerProfile.custName": "张伟", "CustomerProfile.totalRevenue": 88000.0}
                          ]
                        }
                        """)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "vip3SearchPact")
    void testVip3Search() {
        CubeClient client = clientFor(8888);
        List<Map<String, Object>> rows = client.load(CubeQuery.builder()
                .measure("CustomerProfile.totalRevenue")
                .dimension("CustomerProfile.custId")
                .dimension("CustomerProfile.custName")
                .filter("CustomerProfile.customerLevel", "equals", "VIP3")
                .order("CustomerProfile.totalRevenue", "desc")
                .limit(5)
                .offset(0)
                .build());

        assertEquals(2, rows.size());
        assertEquals("C0050", rows.get(0).get("CustomerProfile.custId"));
        assertEquals("王芳", rows.get(0).get("CustomerProfile.custName"));
    }

    // ============ 场景 4：全局指标快照 ============

    @Pact(consumer = "data-service")
    RequestResponsePact metricsOverviewPact(PactDslWithEntity builder) {
        return builder
                .given("CustomerMetrics cube 加载完成")
                .uponReceiving("查询全局客户指标快照（ARPU / VIP3 / 风险分布）")
                .method("POST")
                .path(PATH)
                .matchHeader("Authorization", AUTH_HEADER_REGEX)
                .body("""
                        {
                          "query": {
                            "measures": [
                              "CustomerMetrics.arpu",
                              "CustomerMetrics.vipCustomerCount",
                              "CustomerMetrics.highRiskCustomerCount",
                              "CustomerMetrics.mediumRiskCustomerCount",
                              "CustomerMetrics.lowRiskCustomerCount"
                            ]
                          }
                        }
                        """)
                .willRespondWith()
                .status(200)
                .body("""
                        {
                          "data": [
                            {
                              "CustomerMetrics.arpu": 3200.5,
                              "CustomerMetrics.vipCustomerCount": 50,
                              "CustomerMetrics.highRiskCustomerCount": 14,
                              "CustomerMetrics.mediumRiskCustomerCount": 21,
                              "CustomerMetrics.lowRiskCustomerCount": 15
                            }
                          ]
                        }
                        """)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "metricsOverviewPact")
    void testMetricsOverview() {
        CubeClient client = clientFor(8888);
        List<Map<String, Object>> rows = client.load(CubeQuery.builder()
                .measure("CustomerMetrics.arpu")
                .measure("CustomerMetrics.vipCustomerCount")
                .measure("CustomerMetrics.highRiskCustomerCount")
                .measure("CustomerMetrics.mediumRiskCustomerCount")
                .measure("CustomerMetrics.lowRiskCustomerCount")
                .build());

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals(3200.5, row.get("CustomerMetrics.arpu"));
        assertEquals(50L, row.get("CustomerMetrics.vipCustomerCount"));
        assertEquals(14L, row.get("CustomerMetrics.highRiskCustomerCount"));
    }
}
