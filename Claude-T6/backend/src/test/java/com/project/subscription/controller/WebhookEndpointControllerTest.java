package com.project.subscription.controller;

import com.project.subscription.model.entity.WebhookEndpoint;
import com.project.subscription.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookEndpointControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookEndpointRepository endpointRepository;

    @Test
    void listEndpoints_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/v1/webhook-endpoints"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void createEndpoint_shouldReturnSecret() throws Exception {
        mockMvc.perform(post("/api/v1/webhook-endpoints")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Endpoint\",\"url\":\"https://httpbin.org/post\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.secret").exists());
    }

    @Test
    void getQuota_shouldReturnQuotaInfo() throws Exception {
        mockMvc.perform(get("/api/v1/webhook-endpoints/quota"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.used").exists())
            .andExpect(jsonPath("$.data.limit").exists());
    }
}
