package com.project.subscription.controller;

import com.project.subscription.model.dto.ApiResponse;
import com.project.subscription.service.EndpointHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/endpoints")
@RequiredArgsConstructor
public class HealthController {

    private final EndpointHealthService healthService;

    @GetMapping("/{id}/health")
    public ApiResponse<Map<String, Object>> getHealth(@PathVariable Long id) {
        return ApiResponse.ok(healthService.getEndpointHealth(id));
    }
}
