package com.datafabric.dataservice.api;

import com.datafabric.dataservice.config.SecurityConfig;
import com.datafabric.dataservice.metadata.OpenMetadataClient;
import com.datafabric.dataservice.metadata.TableMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W5/W6 服务市场端到端（HTTP 层）：发布 → 列表 → 试调 → 注入防御 → 下线
 * + W6 融合（金标/注入/钳制）与服务级 API Key 双通道鉴权。
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
        // W6-D：postgres 池也用 H2 替身（MODE=PostgreSQL，真验证双引号方言 —— 反引号在此模式必语法错）
        "datafabric.raw-db.postgres-url=jdbc:h2:mem:w6dpg;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "datafabric.raw-db.postgres-user=sa",
        "datafabric.raw-db.postgres-password=",
        "datafabric.security.api-key=test-api-key-fixed-for-ci",
        // W6-B：注册表持久化库指向 mem（避免测试写文件库；DB_CLOSE_DELAY 使跨用例状态与内存 Map 语义一致）
        "datafabric.registry-db.url=jdbc:h2:mem:w6bmarketreg;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "datafabric.registry-db.user=sa",
        "datafabric.registry-db.password=",
})
class ServiceMarketplaceControllerTest {

    private static final String TEST_API_KEY = "test-api-key-fixed-for-ci";
    private static final String H2_URL =
            "jdbc:h2:mem:w5market;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final String PG_URL =
            "jdbc:h2:mem:w6dpg;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    /** 与 om-stub.js 同形的 customer/orders 表元数据（W6 融合）+ W6-D 跨源从表 */
    private static List<TableMetadata> omTables() {
        return List.of(
                new TableMetadata("mysql.customer_db.customer", "客户基础表", List.of(
                        new TableMetadata.Column("cust_id", "客户ID"),
                        new TableMetadata.Column("cust_name", "客户姓名"),
                        new TableMetadata.Column("phone", "手机号"),
                        new TableMetadata.Column("cust_level", "客户等级"),
                        new TableMetadata.Column("region", "所在地区"))),
                new TableMetadata("mysql.customer_db.orders", "客户订单表", List.of(
                        new TableMetadata.Column("order_id", "订单号"),
                        new TableMetadata.Column("cust_id", "客户ID"),
                        new TableMetadata.Column("order_amount", "订单金额"),
                        new TableMetadata.Column("order_time", "下单时间"))),
                new TableMetadata("postgres.external.risk_tags", "客户风险标签", List.of(
                        new TableMetadata.Column("cust_id", "客户ID"),
                        new TableMetadata.Column("risk_level", "风险等级"),
                        new TableMetadata.Column("risk_score", "风险分"))));
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpenMetadataClient omClient;

    @BeforeAll
    static void seedH2() throws Exception {
        try (Connection conn = DriverManager.getConnection(H2_URL, "sa", "");
             Statement st = conn.createStatement()) {
            // W6-D：FROM 显式限定 库.表 → 种子落 customer_db schema（与 FQN 中段一致）
            st.execute("DROP SCHEMA IF EXISTS customer_db CASCADE");
            st.execute("CREATE SCHEMA customer_db");
            st.execute("""
                    CREATE TABLE customer_db.customer (
                        cust_id       VARCHAR(16) PRIMARY KEY,
                        cust_name     VARCHAR(64),
                        phone         VARCHAR(20),
                        id_card       VARCHAR(32),
                        cust_level    VARCHAR(8),
                        region        VARCHAR(32),
                        register_time TIMESTAMP
                    )""");
            st.execute("INSERT INTO customer_db.customer VALUES ('C0001','张伟','13800000001','110101199001011234','VIP3','北京','2026-01-15 10:30:00')");
            st.execute("INSERT INTO customer_db.customer VALUES ('C0002','王芳','13900000002','310101199202022345','VIP2','上海','2026-02-20 14:00:00')");
            st.execute("INSERT INTO customer_db.customer VALUES ('C0003','李娜','13700000003','440101199303033456','VIP1','广州','2026-03-25 09:15:00')");
            // W6 融合从表种子（金标：C0001=2 单 / C0002=1 / C0003=1）
            st.execute("""
                    CREATE TABLE customer_db.orders (
                        order_id     VARCHAR(16) PRIMARY KEY,
                        cust_id      VARCHAR(16),
                        order_amount DECIMAL(12,2),
                        order_time   TIMESTAMP
                    )""");
            st.execute("INSERT INTO customer_db.orders VALUES ('O1001','C0001',1290.00,'2026-04-01 09:10:00')");
            st.execute("INSERT INTO customer_db.orders VALUES ('O1002','C0001',3380.50,'2026-04-12 20:45:00')");
            st.execute("INSERT INTO customer_db.orders VALUES ('O1003','C0002',860.00,'2026-05-03 14:20:00')");
            st.execute("INSERT INTO customer_db.orders VALUES ('O1004','C0003',2180.00,'2026-05-21 11:00:00')");
        }
        // W6-D 跨源从表替身：PG 模式 H2 里的 external.risk_tags（金标：C0001=high/82）
        // 全双引号建种子：不依赖 H2 模式的大小写折叠规则（PG 模式默认折叠非小写会 Schema not found）
        try (Connection conn = DriverManager.getConnection(PG_URL, "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("DROP SCHEMA IF EXISTS \"external\" CASCADE");
            st.execute("CREATE SCHEMA \"external\"");
            st.execute("""
                    CREATE TABLE "external"."risk_tags" (
                        "cust_id"     VARCHAR(16),
                        "risk_level"  VARCHAR(8),
                        "risk_score"  INT
                    )""");
            st.execute("INSERT INTO \"external\".\"risk_tags\" VALUES ('C0001','high',82)");
            st.execute("INSERT INTO \"external\".\"risk_tags\" VALUES ('C0002','medium',55)");
            st.execute("INSERT INTO \"external\".\"risk_tags\" VALUES ('C0003','low',12)");
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
    @DisplayName("目录：builtin 6 条内置服务可见（注册表跨用例共享，条数只增不减）；不透出任何 key 材料")
    void list_containsSixBuiltins() throws Exception {
        publishFusion("keyless-list-check", 50);
        mockMvc.perform(get("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.services[?(@.service.type=='builtin')]").exists())
                // Blocker 2：目录面不再含明文 key，也不含哈希（@JsonIgnore）
                .andExpect(jsonPath("$.services[?(@.apiKey)]").doesNotExist())
                .andExpect(jsonPath("$.services[?(@.apiKeyHash)]").doesNotExist())
                .andExpect(jsonPath("$.services[?(@.service.apiKey)]").doesNotExist())
                .andExpect(jsonPath("$.services[?(@.service.apiKeyHash)]").doesNotExist());

        // 详情面同理
        mockMvc.perform(get("/api/v1/services/keyless-list-check")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.service.apiKey").doesNotExist())
                .andExpect(jsonPath("$.service.apiKeyHash").doesNotExist());
    }

    // ============ W6 融合 + 服务级 API Key ============

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 融合发布体：customer 主表 + orders 从表（cust_id 关联） */
    private String fusionBody(String slug, int limitPerParent) {
        return """
                {
                  "slug": "%s",
                  "name": "客户订单融合",
                  "description": "客户+订单",
                  "fqn": "mysql.customer_db.customer",
                  "allowedColumns": ["cust_id", "cust_name", "cust_level"],
                  "filters": [{"column": "cust_id", "operator": "eq"}],
                  "joins": [{
                    "fqn": "mysql.customer_db.orders",
                    "name": "orders",
                    "columns": ["order_id", "order_amount", "order_time"],
                    "joinColumn": "cust_id",
                    "parentColumn": "cust_id",
                    "limitPerParent": %d
                  }],
                  "defaultLimit": 10
                }""".formatted(slug, limitPerParent);
    }

    private String publishFusion(String slug, int limitPerParent) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fusionBody(slug, limitPerParent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.service.type").value("fusion"))
                // Blocker 2：明文 key 只在发布响应顶层出现一次，service 内无 key 材料
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andExpect(jsonPath("$.service.apiKey").doesNotExist())
                .andExpect(jsonPath("$.service.apiKeyHash").doesNotExist())
                .andReturn();
        return MAPPER.readTree(result.getResponse().getContentAsString())
                .path("apiKey").asText();
    }

    @Test
    @DisplayName("融合金标：发布 fusion → query C0001 → 主行张伟 + orders 嵌套 2 条")
    void fusionPublishThenQuery_goldenRow() throws Exception {
        publishFusion("cust-orders-golden", 50);

        mockMvc.perform(get("/api/v1/services/cust-orders-golden/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].cust_name").value("张伟"))
                .andExpect(jsonPath("$.rows[0].cust_level").value("VIP3"))
                .andExpect(jsonPath("$.joins[0].name").value("orders"))
                .andExpect(jsonPath("$.rows[0].orders.length()").value(2))
                .andExpect(jsonPath("$.rows[0].orders[0].order_id").exists())
                // 从行透出关联键（FV6 关联一致性依赖）
                .andExpect(jsonPath("$.rows[0].orders[0].cust_id").value("C0001"))
                .andExpect(jsonPath("$.rows[0].orders[1].cust_id").value("C0001"));
    }

    @Test
    @DisplayName("融合注入：关联参数 OR 恒真 payload → 0 主行 0 订单（全值绑定）")
    void fusionQuery_injectionPayload_zeroRows() throws Exception {
        publishFusion("cust-orders-inject", 50);

        MvcResult result = mockMvc.perform(get("/api/v1/services/cust-orders-inject/query")
                        .param("cust_id", "C0001' OR '1'='1")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"total\":0").doesNotContain("张伟").doesNotContain("O1001");
    }

    @Test
    @DisplayName("融合发布闸：从表列塞注入 payload → 400（FV5）")
    void fusionPublish_evilJoinColumn_badRequest() throws Exception {
        String body = fusionBody("evil-join-cols", 50)
                .replace("\"order_id\", \"order_amount\", \"order_time\"",
                        "\"order_id\", \"cust_id; DROP TABLE orders\"");
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("服务 key：仅凭自己 slug 的 key 调 /query → 200（对外开放通道）")
    void serviceKey_ownSlugQuery_ok() throws Exception {
        String serviceKey = publishFusion("cust-orders-keyed", 50);

        mockMvc.perform(get("/api/v1/services/cust-orders-keyed/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].cust_name").value("张伟"))
                .andExpect(jsonPath("$.rows[0].orders.length()").value(2));
    }

    @Test
    @DisplayName("服务 key：敲别的 slug → 401；发布端点 → 401（key 只放行自己的 /query）")
    void serviceKey_otherSlugAndPost_unauthorized() throws Exception {
        String serviceKey = publishFusion("cust-orders-keyed-a", 50);
        publishFusion("cust-orders-keyed-b", 50);

        mockMvc.perform(get("/api/v1/services/cust-orders-keyed-b/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, serviceKey))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, serviceKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fusionBody("should-never-exist", 50)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/services/should-never-exist")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("融合钳制：limitPerParent=1 → C0001 的 orders 只出 1 条（金标 2 条）")
    void fusionQuery_limitPerParentClamped() throws Exception {
        publishFusion("cust-orders-clamp", 1);

        mockMvc.perform(get("/api/v1/services/cust-orders-clamp/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].orders.length()").value(1));
    }

    // ============ W6-B 运营化：key 生命周期 / 限流 / 计量 / 策略透出 ============

    /** 带 W6-B 策略字段的融合发布体 */
    private String fusionBodyWithPolicy(String slug, int rateLimitPerMin, Integer ttlHours) {
        String base = """
                {
                  "slug": "%s",
                  "name": "客户订单融合",
                  "description": "W6-B 策略",
                  "fqn": "mysql.customer_db.customer",
                  "allowedColumns": ["cust_id", "cust_name", "cust_level"],
                  "filters": [{"column": "cust_id", "operator": "eq"}],
                  "joins": [{
                    "fqn": "mysql.customer_db.orders",
                    "name": "orders",
                    "columns": ["order_id", "order_amount"],
                    "joinColumn": "cust_id",
                    "parentColumn": "cust_id",
                    "limitPerParent": 50
                  }],
                  "defaultLimit": 10,
                  "rateLimitPerMin": %d%s
                }""".formatted(slug, rateLimitPerMin,
                ttlHours == null ? "" : ",\n  \"keyTtlHours\": " + ttlHours);
        return base;
    }

    @Test
    @DisplayName("发布策略透出：rateLimitPerMin=5 + keyTtlHours=24 → 201 响应含 keyPolicy")
    void publish_withPolicy_policyEchoed() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fusionBodyWithPolicy("policy-echo", 5, 24)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.service.keyPolicy.status").value("ACTIVE"))
                .andExpect(jsonPath("$.service.keyPolicy.rateLimitPerMin").value(5))
                .andExpect(jsonPath("$.service.keyPolicy.expiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("轮换 E2E：rotate 后旧 key 401 / 新 key 200；服务 key 自调 rotate → 401")
    void keyRotate_oldKeyDiesNewKeyWorks() throws Exception {
        String oldKey = publishFusion("rotate-e2e", 50);

        MvcResult rotateResult = mockMvc.perform(post("/api/v1/services/rotate-e2e/key/rotate")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andExpect(jsonPath("$.service.apiKey").doesNotExist())
                .andReturn();
        String newKey = MAPPER.readTree(rotateResult.getResponse().getContentAsString())
                .path("apiKey").asText();
        assertThat(newKey).isNotEqualTo(oldKey).startsWith("sk-w6-");

        mockMvc.perform(get("/api/v1/services/rotate-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, oldKey))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/services/rotate-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, newKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].cust_name").value("张伟"));

        // 服务 key 不能自我管理（仅全局 key 有 rotate 权限）
        mockMvc.perform(post("/api/v1/services/rotate-e2e/key/rotate")
                        .header(SecurityConfig.HEADER_API_KEY, newKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("吊销 E2E：revoke 后服务 key 401；全局 key 试调照常 200（服务未下线）")
    void keyRevoked_serviceKey401GlobalStillWorks() throws Exception {
        String serviceKey = publishFusion("revoke-e2e", 50);

        mockMvc.perform(post("/api/v1/services/revoke-e2e/key/revoke")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service.keyPolicy.status").value("REVOKED"));

        mockMvc.perform(get("/api/v1/services/revoke-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, serviceKey))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/services/revoke-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].cust_name").value("张伟"));
    }

    @Test
    @DisplayName("限流 E2E：rateLimitPerMin=2 → 第 3 次 429 + Retry-After: 60")
    void rateLimit_thirdCallInMinute429() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fusionBodyWithPolicy("rate-limit-e2e", 2, null)))
                .andExpect(status().isCreated());

        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(get("/api/v1/services/rate-limit-e2e/query")
                            .param("cust_id", "C0001")
                            .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/services/rate-limit-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"));
    }

    @Test
    @DisplayName("限流分窗 E2E（HV1）：global 窗 429 后服务 key 照常 200 —— 通道互不饿死")
    void rateLimit_globalWindowExhausted_serviceKeyUnaffected() throws Exception {
        MvcResult publishResult = mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fusionBodyWithPolicy("channel-window-e2e", 1, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apiKey").isNotEmpty())
                .andReturn();
        String serviceKey = MAPPER.readTree(publishResult.getResponse().getContentAsString())
                .path("apiKey").asText();

        // global 窗（rateLimit=1）：第 1 次 200，第 2 次 429
        mockMvc.perform(get("/api/v1/services/channel-window-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/services/channel-window-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isTooManyRequests());

        // service 窗独立：第三方 key 不被平台巡检挤占，第 1 次照常 200；第 2 次自己窗满 429
        mockMvc.perform(get("/api/v1/services/channel-window-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, serviceKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].cust_name").value("张伟"));
        mockMvc.perform(get("/api/v1/services/channel-window-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, serviceKey))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("计量 E2E：查询 2 次 → usage 总量=今日=2；未知 slug → 404；builtin rotate → 404")
    void usage_countsQueriesAnd404s() throws Exception {
        publishFusion("usage-e2e", 50);

        mockMvc.perform(get("/api/v1/services/usage-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/services/usage-e2e/query")
                        .param("cust_id", "C0001")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/services/usage-e2e/usage")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCalls").value(2))
                .andExpect(jsonPath("$.todayCalls").value(2))
                .andExpect(jsonPath("$.recentDays[0].calls").value(2));

        mockMvc.perform(get("/api/v1/services/no-such-slug/usage")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/services/customer-profile/key/rotate")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isNotFound());
    }

    // ============ W6-C 聚合列 DSL ============

    /** 聚合发布体：orders 表 GROUP BY cust_id + COUNT(*)/SUM(order_amount) */
    private String aggregateBody(String slug) {
        return """
                {
                  "slug": "%s",
                  "name": "按客户聚合订单",
                  "description": "W6-C 聚合",
                  "fqn": "mysql.customer_db.orders",
                  "allowedColumns": ["cust_id"],
                  "filters": [],
                  "aggregates": [
                    {"function": "COUNT", "column": null, "alias": "order_count"},
                    {"function": "SUM", "column": "order_amount", "alias": "total_amount"}
                  ],
                  "defaultLimit": 10
                }""".formatted(slug);
    }

    @Test
    @DisplayName("聚合金标：GROUP BY cust_id → C0001 order_count=2 / total_amount=4670.50")
    void aggregatePublishThenQuery_goldenRow() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aggregateBody("orders-by-cust-golden")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.service.type").value("aggregate"))
                .andExpect(jsonPath("$.apiKey").isNotEmpty());

        mockMvc.perform(get("/api/v1/services/orders-by-cust-golden/query")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                // 输出契约：columns = 维度 + 聚合别名
                .andExpect(jsonPath("$.columns.length()").value(3))
                // ORDER BY 1 稳定次序：C0001 首行（金标 2 单 / 1290.00+3380.50）
                .andExpect(jsonPath("$.rows[0].cust_id").value("C0001"))
                .andExpect(jsonPath("$.rows[0].order_count").value("2"))
                .andExpect(jsonPath("$.rows[0].total_amount").value("4670.50"))
                .andExpect(jsonPath("$.rows[1].cust_id").value("C0002"))
                .andExpect(jsonPath("$.rows[1].order_count").value("1"))
                .andExpect(jsonPath("$.rows[1].total_amount").value("860.00"));
    }

    @Test
    @DisplayName("聚合非维度列过滤：WHERE cust_level=VIP1 先于 GROUP BY → 只出 VIP1 客户行")
    void aggregateQuery_filterOnNonDimColumn() throws Exception {
        String body = """
                {
                  "slug": "agg-filter-nondim-e2e",
                  "name": "非维度过滤聚合",
                  "fqn": "mysql.customer_db.customer",
                  "allowedColumns": ["cust_id"],
                  "filters": [{"column": "cust_level", "operator": "eq"}],
                  "aggregates": [{"function": "COUNT", "column": null, "alias": "cnt"}],
                  "defaultLimit": 10
                }""";
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/services/agg-filter-nondim-e2e/query")
                        .param("cust_level", "VIP1")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].cust_id").value("C0003"))
                .andExpect(jsonPath("$.rows[0].cnt").value("1"));
    }

    @Test
    @DisplayName("聚合注入：过滤值 OR 恒真 payload → 200 且 total=0（全值绑定）")
    void aggregateQuery_injectionPayload_zeroRows() throws Exception {
        String body = """
                {
                  "slug": "agg-inject-e2e",
                  "name": "注入聚合",
                  "fqn": "mysql.customer_db.customer",
                  "allowedColumns": ["cust_id"],
                  "filters": [{"column": "cust_level", "operator": "eq"}],
                  "aggregates": [{"function": "COUNT", "column": null, "alias": "cnt"}],
                  "defaultLimit": 10
                }""";
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/services/agg-inject-e2e/query")
                        .param("cust_level", "VIP1' OR '1'='1")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("\"total\":0");
    }

    @Test
    @DisplayName("聚合契约：未注册的过滤参数 → 400（不静默忽略）")
    void aggregateQuery_unknownParam_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(aggregateBody("agg-strict-e2e")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/services/agg-strict-e2e/query")
                        .param("order_time", "2026-04-01")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("聚合发布闸：alias 塞注入 payload → 400；聚合+融合同报 → 400")
    void aggregatePublish_evilAliasAndJoinsMix_badRequest() throws Exception {
        String evilAlias = aggregateBody("agg-alias-evil")
                .replace("\"alias\": \"total_amount\"", "\"alias\": \"total; DROP TABLE orders\"");
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evilAlias))
                .andExpect(status().isBadRequest());

        String mix = aggregateBody("agg-join-mix-e2e")
                .replace("\"filters\": []", "\"filters\": [], \"joins\": [" +
                        "{\"fqn\": \"mysql.customer_db.customer\", \"name\": \"c\", " +
                        "\"columns\": [\"cust_name\"], \"joinColumn\": \"cust_id\", " +
                        "\"parentColumn\": \"cust_id\", \"limitPerParent\": 10}]");
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mix))
                .andExpect(status().isBadRequest());
    }

    // ============ W6-D 跨源融合（MySQL 主表 + PostgreSQL 从表） ============

    /** 跨源融合发布体：customer（MySQL）+ risk_tags（PostgreSQL），cust_id 关联 */
    private String crossSourceFusionBody(String slug) {
        return """
                {
                  "slug": "%s",
                  "name": "客户+风险标签跨源融合",
                  "description": "MySQL 主表 + PostgreSQL 从表（W6-D）",
                  "fqn": "mysql.customer_db.customer",
                  "allowedColumns": ["cust_id", "cust_name", "cust_level"],
                  "filters": [{"column": "cust_level", "operator": "eq"}],
                  "joins": [{
                    "fqn": "postgres.external.risk_tags",
                    "name": "risk_tags",
                    "columns": ["risk_level", "risk_score"],
                    "joinColumn": "cust_id",
                    "parentColumn": "cust_id",
                    "limitPerParent": 5
                  }],
                  "defaultLimit": 10
                }""".formatted(slug);
    }

    @Test
    @DisplayName("跨源金标：MySQL 主表 + PG 从表 → C0001 行嵌套 risk_tags[0].risk_level=high（引号方言被真验证）")
    void crossSourceFusion_goldenRow() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crossSourceFusionBody("cust-risk-xsrc")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.service.type").value("fusion"));

        // PG 替身是 MODE=PostgreSQL：若从表 SQL 沿用反引号会直接语法错（502）——金标即方言验证
        mockMvc.perform(get("/api/v1/services/cust-risk-xsrc/query")
                        .param("cust_level", "VIP3")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].cust_name").value("张伟"))
                .andExpect(jsonPath("$.rows[0].risk_tags.length()").value(1))
                .andExpect(jsonPath("$.rows[0].risk_tags[0].risk_level").value("high"))
                .andExpect(jsonPath("$.rows[0].risk_tags[0].risk_score").value("82"))
                .andExpect(jsonPath("$.rows[0].risk_tags[0].cust_id").value("C0001"));
    }

    @Test
    @DisplayName("跨源注入：payload 走参数绑定 → 0 主行 0 从行零泄露")
    void crossSourceFusion_injectionPayload_zeroRows() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(crossSourceFusionBody("cust-risk-xsrc-inject")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/services/cust-risk-xsrc-inject/query")
                        .param("cust_level", "VIP3' OR '1'='1")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }
}
