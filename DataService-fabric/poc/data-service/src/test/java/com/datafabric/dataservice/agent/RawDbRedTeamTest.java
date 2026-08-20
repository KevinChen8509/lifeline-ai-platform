package com.datafabric.dataservice.agent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F4 红队演示（确定性部分，CI 可跑）：B 路工具对 SQL 注入的防御对比。
 *
 * 用 H2（MODE=MySQL）替身 MySQL 池 —— 同一批注入 payload：
 *   - 走 RawDbTools（PreparedStatement 参数化）→ 0 行、表完好
 *   - 走测试内拼接 SQL（仅演示，生产代码永不出现）→ 全表泄露
 *
 * 结论即红队演示的核心信息：参数化把注入堵死，但 B 路对**合法**查询
 * 仍原样吐 PII 且零审计 —— 真正的暴露不是注入，是治理缺失（见
 * scripts/red-team-b-path.sh 与 poc/RED-TEAM-DEMO.md 的活体对照）。
 *
 * clickhouse / postgres 池保持懒启动不触发（F8），本测试只碰 MySQL 工具。
 */
@SpringBootTest
@TestPropertySource(properties = {
        // H2 内存库替身 MySQL（保持懒启动 URL 语义，由 RawDbPoolConfig 包装成池）
        "datafabric.raw-db.mysql-url=jdbc:h2:mem:f4redteam;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "datafabric.raw-db.mysql-user=sa",
        "datafabric.raw-db.mysql-password=",
        "datafabric.security.api-key=test-api-key-fixed-for-ci",
})
class RawDbRedTeamTest {

    private static final String URL =
            "jdbc:h2:mem:f4redteam;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    /** 经典 OR 恒真注入：拼进 SQL 即 WHERE cust_id='C0001' OR '1'='1' → 全表 */
    private static final String OR_PAYLOAD = "C0001' OR '1'='1";
    /** 堆叠语句注入：拼进 SQL 即 DROP TABLE */
    private static final String DROP_PAYLOAD = "C0001'; DROP TABLE customer; --";

    @Autowired
    private RawDbTools rawDbTools;

    @BeforeAll
    static void seedFakeCustomers() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
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

    @Test
    @DisplayName("对照：合法 ID 正常返回（证明查询链路本身是通的）")
    void control_validId_returnsRowWithPii() {
        String out = rawDbTools.getCustomerRaw("C0001");
        assertThat(out).contains("C0001").contains("13800000001").contains("110101199001011234");
    }

    @Test
    @DisplayName("OR 恒真注入 → 参数化按字面量处理，0 行，无泄露")
    void orInjection_treatedAsLiteral_returnsNoRows() {
        String out = rawDbTools.getCustomerRaw(OR_PAYLOAD);
        // payload 整串被当作一个 cust_id 字面量 → 查无此人
        assertThat(out).isEqualTo("[]");
        // 关键断言：没有任何其他客户的数据被带出
        assertThat(out).doesNotContain("C0002").doesNotContain("C0003").doesNotContain("13900000002");
    }

    @Test
    @DisplayName("堆叠 DROP 注入 → 0 行且表完好（随后合法查询仍可用）")
    void stackedDropInjection_tableRemainsIntact() {
        String out = rawDbTools.getCustomerRaw(DROP_PAYLOAD);
        assertThat(out).isEqualTo("[]");

        // 表没被删：合法 ID 仍能查到
        String after = rawDbTools.getCustomerRaw("C0001");
        assertThat(after).contains("C0001").contains("张伟");
    }

    @Test
    @DisplayName("红队对照（仅测试内演示）：同一 payload 拼进 SQL → 全表 3 行泄露")
    void vulnerableConcat_contrast_returnsAllRows() throws Exception {
        // 故意脆弱的拼接写法 —— 只存在于本演示方法，证明 payload 本身是"活的"：
        // 参数化测试返回 0 行不是因为 payload 无效，而是因为 PreparedStatement。
        String vulnerableSql = "SELECT cust_id FROM customer WHERE cust_id = '" + OR_PAYLOAD + "'";
        int leaked = 0;
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(vulnerableSql)) {
            while (rs.next()) {
                leaked++;
            }
        }
        assertThat(leaked).as("拼接 SQL 下同一 payload 泄露全表").isEqualTo(3);
    }
}
