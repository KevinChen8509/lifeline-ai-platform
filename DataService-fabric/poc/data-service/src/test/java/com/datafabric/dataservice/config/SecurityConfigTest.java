package com.datafabric.dataservice.config;

import com.datafabric.dataservice.domain.CustomerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * B2 鉴权测试 - 验证 X-API-Key 守门逻辑
 *
 * 4 个场景：缺 key / 错 key / 对 key / actuator/health 不鉴权
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "datafabric.security.api-key=test-api-key-fixed-for-ci"
})
class SecurityConfigTest {

    private static final String CORRECT_KEY = "test-api-key-fixed-for-ci";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerProfileService service;

    @Test
    void apiRequest_withoutApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/customers/C0001/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void apiRequest_withWrongApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/customers/C0001/profile")
                        .header(SecurityConfig.HEADER_API_KEY, "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    void apiRequest_withCorrectApiKey_passes() throws Exception {
        // service 返回空 → controller 会抛 CustomerNotFound → 404
        // 404 ≠ 401，证明 key 被接受了（请求通过鉴权进入 controller）
        when(service.findById(eq("C0001"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/customers/C0001/profile")
                        .header(SecurityConfig.HEADER_API_KEY, CORRECT_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void healthEndpoint_withoutApiKey_permitted() throws Exception {
        // /actuator/health 是 permitAll，无 key 也应 200
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void nonApiPath_returns404_or_denied() throws Exception {
        // 任何未明确放行的路径（非 /api/v1/** 非 /actuator/health）应被拒
        mockMvc.perform(get("/random-unknown-path")
                        .header(SecurityConfig.HEADER_API_KEY, CORRECT_KEY))
                .andExpect(status().isForbidden());
    }
}
