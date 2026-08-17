package com.datafabric.dataservice.pact;

import com.datafabric.dataservice.agent.CustomerInsightAgent;
import com.datafabric.dataservice.agent.RawDbAgent;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * F10 Pact provider 验证：consumer (dashboard) ↔ provider (data-service)
 *
 * 契约文件：src/test/resources/pacts/dashboard-data-service.json（手写，
 * 代表前端 dashboard 消费方锁定的响应信封）。
 *
 * 覆盖 5 个交互：
 *   - POST /agent/insight 200（fabric 信封 + 动态字段 matcher）
 *   - POST /agent/raw    200（raw 信封）
 *   - POST /agent/insight 401（缺 X-API-Key 错误信封）
 *   - POST /agent/insight 400（空 question 错误信封）
 *   - GET  /agent/trace/{id} 404（TRACE_NOT_FOUND）
 *
 * Agent 用 @MockBean 桩掉（provider 验证只关心 HTTP 契约，不调 LLM）；
 * 鉴权 / 校验 / TraceStore 走真实实现。
 */
@Provider("data-service")
@PactFolder("pacts")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "datafabric.security.api-key=test-api-key-fixed-for-ci"
})
class AgentProviderPactTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerInsightAgent insightAgent;

    @MockBean
    private RawDbAgent rawDbAgent;

    @BeforeEach
    void setUp(PactVerificationContext context) {
        when(insightAgent.answer(anyString())).thenReturn("契约桩回答");
        when(rawDbAgent.answer(anyString())).thenReturn("契约桩回答");

        MockMvcTestTarget target = new MockMvcTestTarget();
        target.setMockMvc(mockMvc);
        context.setTarget(target);
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }
}
