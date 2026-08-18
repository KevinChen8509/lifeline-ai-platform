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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    // --- F5 SSE 流式端点（鉴权 + 入参校验） ---
    // 真实 token 流转由集成测试（curl + Ark API）覆盖；这里只覆盖 pre-stream 校验。

    @Test
    void insightStream_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/agent/insight/stream")
                        .contentType("application/json")
                        .content("{\"question\":\"任何问题\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void insightStream_withApiKey_emptyQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/agent/insight/stream")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .contentType("application/json")
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void rawStream_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/agent/raw/stream")
                        .contentType("application/json")
                        .content("{\"question\":\"任何问题\"}"))
                .andExpect(status().isUnauthorized());
    }

    // --- F9 trace 追溯端点 ---

    @Test
    void trace_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/agent/trace/abcd1234"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void trace_unknownRequestId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/agent/trace/ghost0000")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TRACE_NOT_FOUND"));
    }

    // --- F6 用量与成本端点 ---

    @Test
    void usage_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/agent/usage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usage_returnsTotalsAndPricingEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/agent/usage")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals").exists())
                .andExpect(jsonPath("$.totals.llmCalls").exists())
                .andExpect(jsonPath("$.byPath").exists())
                .andExpect(jsonPath("$.costEstimateCny.total").exists())
                .andExpect(jsonPath("$.pricing.inputPerMillion").exists())
                .andExpect(jsonPath("$.recent").isArray());
    }
}
