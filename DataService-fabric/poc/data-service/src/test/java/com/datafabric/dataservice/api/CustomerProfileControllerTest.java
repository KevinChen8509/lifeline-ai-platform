package com.datafabric.dataservice.api;

import com.datafabric.dataservice.config.SecurityConfig;
import com.datafabric.dataservice.domain.CustomerOverviewDto;
import com.datafabric.dataservice.domain.CustomerProfileDto;
import com.datafabric.dataservice.domain.CustomerProfileService;
import com.datafabric.dataservice.exception.CustomerNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller 层 MockMvc 测试 - 不依赖 Cube.dev / Trino
 *
 * W2.4 会用 Pact 做消费者-提供者契约测试，覆盖 Cube ↔ Service 真实交互。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "datafabric.security.api-key=test-api-key-fixed-for-ci"
})
class CustomerProfileControllerTest {

    /** 测试用的固定 API Key（覆盖 application.yml 默认值，避免 CI 环境变量缺失） */
    private static final String TEST_API_KEY = "test-api-key-fixed-for-ci";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerProfileService service;

    /** 给请求加 X-API-Key 头（B2 鉴权要求） */
    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY);
    }

    @Test
    void getProfile_returnsCustomer_whenFound() throws Exception {
        CustomerProfileDto dto = new CustomerProfileDto(
                "C0001", "李娜", "13800000001", "110101199001011234",
                "VIP3", "华东", 42L, 128000.0, "high", 88,
                "2023-01-15 10:30:00", "2026-07-20 14:00:00");

        when(service.findById(eq("C0001"))).thenReturn(Optional.of(dto));

        mockMvc.perform(authed(get("/api/v1/customers/C0001/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.custId").value("C0001"))
                .andExpect(jsonPath("$.custName").value("李娜"))
                .andExpect(jsonPath("$.customerLevel").value("VIP3"));
    }

    @Test
    void getProfile_returns404_whenNotFound() throws Exception {
        when(service.findById(eq("UNKNOWN"))).thenReturn(Optional.empty());

        mockMvc.perform(authed(get("/api/v1/customers/UNKNOWN/profile")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void searchCustomers_validatesSize() throws Exception {
        mockMvc.perform(authed(get("/api/v1/customers").param("size", "500")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void overview_returnsMetrics() throws Exception {
        when(service.overview()).thenReturn(new CustomerOverviewDto(
                3200.50, 50, 14, 21, 15));

        mockMvc.perform(authed(get("/api/v1/metrics/customer-overview")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vipCustomerCount").value(50))
                .andExpect(jsonPath("$.highRiskCustomerCount").value(14));
    }

    @Test
    void searchCustomers_returnsList() throws Exception {
        CustomerProfileDto dto = new CustomerProfileDto(
                "C0002", "张伟", null, null,
                "VIP3", "华东", 30L, 95000.0, "low", 25,
                null, null);
        when(service.search(eq("VIP3"), eq(0), eq(10))).thenReturn(List.of(dto));

        mockMvc.perform(authed(get("/api/v1/customers")
                        .param("level", "VIP3")
                        .param("page", "0")
                        .param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].custId").value("C0002"));
    }

    // ---- B4：异常脱敏（502 / 500 不得泄漏内部 URL / driver / stacktrace 片段）----

    @Test
    void cubeDown_returnsGenericMessageWithoutInternalUrl() throws Exception {
        // 真实 RestClientException 会含 "http://cube:4000/cubejs-api/v1/load?query=..."
        when(service.findById(eq("C0001")))
                .thenThrow(new RestClientException(
                        "I/O error on GET request for \"http://cube:4000/cubejs-api/v1/load\": Connection refused"));

        mockMvc.perform(authed(get("/api/v1/customers/C0001/profile")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("SEMANTIC_LAYER_UNAVAILABLE"))
                // 关键断言：响应体里不得出现内部 host / port / 路径
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("cube:4000"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("http://"))));
    }

    @Test
    void unexpectedException_returnsGenericMessageWithoutDetail() throws Exception {
        // 模拟 NPE 携带内部细节
        when(service.overview())
                .thenThrow(new RuntimeException("NullPointerException at com.datafabric.internal.CubeDriver.getRow(CubeDriver.java:128)"));

        mockMvc.perform(authed(get("/api/v1/metrics/customer-overview")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("CubeDriver"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(".java:"))));
    }
}
