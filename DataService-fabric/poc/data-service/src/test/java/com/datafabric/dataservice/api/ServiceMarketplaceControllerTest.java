package com.datafabric.dataservice.api;

import com.datafabric.dataservice.config.SecurityConfig;
import com.datafabric.dataservice.metadata.OpenMetadataClient;
import com.datafabric.dataservice.metadata.TableMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W5 服务市场端到端（HTTP 层）：发布 → 列表 → 试调 → 注入防御 → 下线。
 *
 * H2（MODE=MySQL）替身 MySQL 池（照搬 RawDbRedTeamTest 模式）；
 * OM 元数据用 @MockBean 桩（CI 无 OM，注册表校验确定性）。
 * 每个用例发布独立 slug —— 注册表是单例，跨用例共享，禁止 slug 交叉依赖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "datafabric.raw-db.mysql-url=jdbc:h2:mem:w5market;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "datafabric.raw-db.mysql-user=sa",
        "datafabric.raw-db.mysql-password=",
        "datafabric.security.api-key=test-api-key-fixed-for-ci",
})
class ServiceMarketplaceControllerTest {

    private static final String TEST_API_KEY = "test-api-key-fixed-for-ci";
    private static final String H2_URL =
            "jdbc:h2:mem:w5market;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    /** 与 om-stub.js 同形的 customer 表元数据（5 列） */
    private static List<TableMetadata> omTables() {
        return List.of(new TableMetadata("mysql.customer_db.customer", "客户基础表", List.of(
                new TableMetadata.Column("cust_id", "客户ID"),
                new TableMetadata.Column("cust_name", "客户姓名"),
                new TableMetadata.Column("phone", "手机号"),
                new TableMetadata.Column("cust_level", "客户等级"),
                new TableMetadata.Column("region", "所在地区"))));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenMetadataClient omClient;

    @BeforeAll
    static void seedH2() throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS customer");
            st.execute("""
                    CREATE TABLE customer (
                        cust_id       VARCHAR(16) PRIMARY KEY,
                        cust_name     VARCHAR(64),
                        phone         VARCHAR(20),
                        id_card       VARCHAR(32),
                        cust_level    VARCHAR(8),
                        region        VARCHAR(32),
                        register_time TIMESTAMP
                    )""");
            st.execute("INSERT INTO customer VALUES ('C0001','张伟','13800000001','110101199001011234','VIP3','北京','2026-01-15 10:30:00')");
            st.execute("INSERT INTO customer VALUES ('C0002','王芳','13900000002','310101199202022345','VIP2','上海','2026-02-20 14:00:00')");
            st.execute("INSERT INTO customer VALUES ('C0003','李娜','13700000003','440101199303033456','VIP1','广州','2026-03-25 09:15:00')");
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(omClient.fetchTables()).thenReturn(omTables());
    }

    private String publishBody(String slug) {
        return """
                {
                  "slug": "%s",
                  "name": "VIP 客户查询",
                  "description": "按等级过滤客户",
                  "fqn": "mysql.customer_db.customer",
                  "allowedColumns": ["cust_id", "cust_name", "cust_level", "phone"],
                  "filters": [{"column": "cust_level", "operator": "eq"}],
                  "defaultLimit": 10
                }""".formatted(slug);
    }

    @Test
    @DisplayName("完整链：发布 201 → 列表可见 → 试调返回种子行（张伟）")
    void publishThenQuery_returnsSeededRow() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody("vip-query-e2e")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.service.slug").value("vip-query-e2e"))
                .andExpect(jsonPath("$.service.type").value("table-query"));

        mockMvc.perform(get("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[?(@.service.slug=='vip-query-e2e')]").exists());

        mockMvc.perform(get("/api/v1/services/vip-query-e2e/query")
                        .param("cust_level", "VIP3")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].cust_name").value("张伟"))
                .andExpect(jsonPath("$.rows[0].cust_level").value("VIP3"));
    }

    @Test
    @DisplayName("注入防御：OR 恒真 payload 走参数绑定 → 0 行零泄露（F4 同一防线）")
    void query_injectionPayload_zeroRows() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody("vip-query-inject")))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/services/vip-query-inject/query")
                        .param("cust_level", "VIP3' OR '1'='1")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("\"total\":0")
                .doesNotContain("张伟").doesNotContain("王芳").doesNotContain("李娜");
    }

    @Test
    @DisplayName("契约：未注册的过滤参数 → 400（不静默忽略）")
    void query_unknownParam_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody("vip-query-strict")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/services/vip-query-strict/query")
                        .param("region", "北京")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("鉴权：无 X-API-Key → 401（市场端点与 /api/v1/* 同一防线）")
    void services_withoutApiKey_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("发布校验：列不在元数据 → 400")
    void publish_columnNotInMetadata_badRequest() throws Exception {
        String body = """
                {
                  "slug": "evil-cols",
                  "name": "越界列",
                  "fqn": "mysql.customer_db.customer",
                  "allowedColumns": ["cust_id", "cust_level; DROP TABLE customer"],
                  "filters": [],
                  "defaultLimit": 10
                }""";
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("下线：builtin 400；自助发布 204 → 详情 404")
    void delete_builtinRejected_publishedRemoved() throws Exception {
        mockMvc.perform(delete("/api/v1/services/customer-profile")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publishBody("vip-query-temp")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/services/vip-query-temp")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/services/vip-query-temp")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("目录：builtin 6 条内置服务可见（注册表跨用例共享，条数只增不减）")
    void list_containsSixBuiltins() throws Exception {
        mockMvc.perform(get("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.services[?(@.service.type=='builtin')]").exists());
    }
}
