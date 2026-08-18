package com.datafabric.dataservice.api;

import com.datafabric.dataservice.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F8 连接池监控端点测试
 *
 * 池懒启动（首个 getConnection 才启动），B 路工具未触发过查询时
 * 端点输出 NOT_STARTED + 配置——应用启动零 JDBC 驱动依赖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "datafabric.security.api-key=test-api-key-fixed-for-ci"
})
class RawDbPoolControllerTest {

    private static final String TEST_API_KEY = "test-api-key-fixed-for-ci";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pools_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/raw-db/pools"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pools_returnsThreeSourcesWithConfigAndState() throws Exception {
        mockMvc.perform(get("/api/v1/raw-db/pools")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mysql.config.maxPoolSize").value(4))
                .andExpect(jsonPath("$.mysql.config.connectionTimeoutMs").value(3000))
                .andExpect(jsonPath("$.clickhouse.config.maxPoolSize").value(4))
                .andExpect(jsonPath("$.postgres.config.minIdle").value(1))
                // 懒启动：B 路工具未触发过查询 → 池未启动，端点仍可用且报告 NOT_STARTED
                .andExpect(jsonPath("$.mysql.state").value("NOT_STARTED"))
                .andExpect(jsonPath("$.clickhouse.state").value("NOT_STARTED"))
                .andExpect(jsonPath("$.postgres.state").value("NOT_STARTED"));
    }
}
