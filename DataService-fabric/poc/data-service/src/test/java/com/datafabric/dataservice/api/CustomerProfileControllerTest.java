package com.datafabric.dataservice.api;

import com.datafabric.dataservice.domain.CustomerOverviewDto;
import com.datafabric.dataservice.domain.CustomerProfileDto;
import com.datafabric.dataservice.domain.CustomerProfileService;
import com.datafabric.dataservice.exception.CustomerNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

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
class CustomerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerProfileService service;

    @Test
    void getProfile_returnsCustomer_whenFound() throws Exception {
        CustomerProfileDto dto = new CustomerProfileDto(
                "C0001", "李娜", "13800000001", "110101199001011234",
                "VIP3", "华东", 42L, 128000.0, "high", 88,
                "2023-01-15 10:30:00", "2026-07-20 14:00:00");

        when(service.findById(eq("C0001"))).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/api/v1/customers/C0001/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.custId").value("C0001"))
                .andExpect(jsonPath("$.custName").value("李娜"))
                .andExpect(jsonPath("$.customerLevel").value("VIP3"));
    }

    @Test
    void getProfile_returns404_whenNotFound() throws Exception {
        when(service.findById(eq("UNKNOWN"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/customers/UNKNOWN/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void searchCustomers_validatesSize() throws Exception {
        mockMvc.perform(get("/api/v1/customers").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    void overview_returnsMetrics() throws Exception {
        when(service.overview()).thenReturn(new CustomerOverviewDto(
                3200.50, 50, 14, 21, 15));

        mockMvc.perform(get("/api/v1/metrics/customer-overview"))
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

        mockMvc.perform(get("/api/v1/customers")
                        .param("level", "VIP3")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].custId").value("C0002"));
    }
}
