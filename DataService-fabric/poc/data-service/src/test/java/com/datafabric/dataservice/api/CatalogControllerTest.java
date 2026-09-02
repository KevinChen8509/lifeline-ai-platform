package com.datafabric.dataservice.api;

import com.datafabric.dataservice.config.SecurityConfig;
import com.datafabric.dataservice.metadata.OpenMetadataClient;
import com.datafabric.dataservice.metadata.TableMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W5 数据资源目录：FQN 拆解（source/database/table）+ 域映射 + OM 离线降级标注。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "datafabric.security.api-key=test-api-key-fixed-for-ci",
})
class CatalogControllerTest {

    private static final String TEST_API_KEY = "test-api-key-fixed-for-ci";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenMetadataClient omClient;

    @BeforeEach
    void setUp() {
        when(omClient.fetchTables()).thenReturn(List.of(
                new TableMetadata("mysql.customer_db.customer", "客户基础表", List.of(
                        new TableMetadata.Column("cust_id", "客户ID"),
                        new TableMetadata.Column("cust_name", "客户姓名"))),
                new TableMetadata("clickhouse.orders_db.orders", "客户订单事实表", List.of(
                        new TableMetadata.Column("order_id", "订单号"))),
                new TableMetadata("postgres.external.risk_tags", "客户风险标签", List.of(
                        new TableMetadata.Column("risk_level", "风险等级")))));
    }

    @Test
    @DisplayName("目录：三源表 FQN 拆解 + 域映射 + 列注释透出")
    void tables_parsesFqnAndMapsDomain() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/tables")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.tables.length()").value(3))
                .andExpect(jsonPath("$.tables[0].source").value("mysql"))
                .andExpect(jsonPath("$.tables[0].database").value("customer_db"))
                .andExpect(jsonPath("$.tables[0].table").value("customer"))
                .andExpect(jsonPath("$.tables[0].domain").value("客户域"))
                .andExpect(jsonPath("$.tables[0].columns[0].name").value("cust_id"))
                .andExpect(jsonPath("$.tables[1].source").value("clickhouse"))
                .andExpect(jsonPath("$.tables[2].domain").value("风险域"));
    }

    @Test
    @DisplayName("降级：OM 离线 → 空表 + degraded=true（页面可标注，不 5xx）")
    void tables_omOffline_degradedFlag() throws Exception {
        when(omClient.fetchTables()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/catalog/tables")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.tables.length()").value(0));
    }
}
