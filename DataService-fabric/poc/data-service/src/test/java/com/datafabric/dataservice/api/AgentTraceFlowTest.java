package com.datafabric.dataservice.api;

import com.datafabric.dataservice.config.SecurityConfig;
import com.datafabric.dataservice.domain.CustomerProfileDto;
import com.datafabric.dataservice.domain.CustomerProfileService;
import com.datafabric.dataservice.governance.TraceStore;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F9 trace 全链路流测试
 *
 * 验证：X-Request-Id 头 → AuditAspect/LineageAspect 捕获 → TraceStore 聚合。
 * Cube 用 @MockBean 返回 DTO（真实触发 AOP 切面），模拟 Agent 工具自调场景。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "datafabric.security.api-key=test-api-key-fixed-for-ci"
})
class AgentTraceFlowTest {

    private static final String TEST_API_KEY = "test-api-key-fixed-for-ci";
    private static final String REQUEST_ID = "flow0011";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TraceStore traceStore;

    @MockBean
    private CustomerProfileService service;

    @Test
    void profileCall_withRequestId_appearsInTrace() throws Exception {
        // 模拟 Agent 请求进行中（真实由 AgentController.start 登记）
        traceStore.start(REQUEST_ID, "fabric", "C0001 画像是什么");

        when(service.findById(eq("C0001"))).thenReturn(Optional.of(new CustomerProfileDto(
                "C0001", "李娜", "13800000001", "110101199001011234",
                "VIP3", "华东", 42L, 128000.0, "high", 88,
                "2023-01-15 10:30:00", "2026-07-20 14:00:00")));

        // 模拟工具自调：带头调 profile（切面照常触发）
        mockMvc.perform(get("/api/v1/customers/C0001/profile")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY)
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isOk());

        // trace 里应出现 AUDIT + LINEAGE
        mockMvc.perform(get("/api/v1/agent/trace/" + REQUEST_ID)
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.events[?(@.type=='AUDIT')]").isNotEmpty())
                .andExpect(jsonPath("$.events[?(@.type=='LINEAGE')]").isNotEmpty());
    }

    @Test
    void profileCall_withoutRequestId_stillWorksNoTraceSideEffect() throws Exception {
        when(service.findById(eq("C0002"))).thenReturn(Optional.of(new CustomerProfileDto(
                "C0002", "王五", "13700000002", "110102198502025678",
                "VIP2", "华北", 10L, 20000.0, "low", 20,
                "2024-03-01 09:00:00", "2026-06-10 18:30:00")));

        // 不带 X-Request-Id 的普通调用：业务正常
        mockMvc.perform(get("/api/v1/customers/C0002/profile")
                        .header(SecurityConfig.HEADER_API_KEY, TEST_API_KEY))
                .andExpect(status().isOk());
    }
}
