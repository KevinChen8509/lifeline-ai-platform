package com.datafabric.dataservice.api;

import com.datafabric.dataservice.config.SecurityConfig;
import com.datafabric.dataservice.domain.CustomerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * W3 AgentController 鉴权 + 入参校验测试
 *
 * LLM 真实调用走集成测试（需 ARK_API_KEY），这里只覆盖：
 *   - 鉴权（B2 同样适用）
 *   - 入参校验（question 不能为空）
 *   - 端点路由可达
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "datafabric.security.api-key=test-api-key-fixed-for-ci"
})
class AgentControllerTest {

    private static final String TEST_API_KEY = "test-api-key-fixed-for-ci";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerProfileService service;

    @Test
    void insight_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/agent/insight")
                        .contentType("application/json")
                        .content("{\"question\":\"任何问题\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void insight_withApiKey_emptyQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/insight")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType("application/json")
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void raw_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/agent/raw")
                        .contentType("application/json")
                        .content("{\"question\":\"任何问题\"}"))
                .andExpect(status().isUnauthorized());
    }
}
