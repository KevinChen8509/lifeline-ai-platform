package com.project.subscription.controller;

import com.project.subscription.config.SecurityUtils;
import com.project.subscription.model.dto.ApiResponse;
import com.project.subscription.model.dto.EndpointDto;
import com.project.subscription.service.WebhookEndpointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/webhook-endpoints")
@RequiredArgsConstructor
public class WebhookEndpointController {

    private final WebhookEndpointService endpointService;

    @PostMapping
    public ApiResponse<EndpointDto.SecretResponse> create(@Valid @RequestBody EndpointDto.CreateRequest req) {
        return ApiResponse.ok(endpointService.create(SecurityUtils.getTenantId(), SecurityUtils.getUserId(), req));
    }

    @GetMapping
    public ApiResponse<List<EndpointDto.Response>> list() {
        return ApiResponse.ok(endpointService.listByUser(SecurityUtils.getTenantId(), SecurityUtils.getUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<EndpointDto.Response> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(endpointService.getById(id, SecurityUtils.getTenantId()));
    }

    @PutMapping("/{id}")
    public ApiResponse<EndpointDto.Response> update(@PathVariable Long id,
                                                     @RequestBody EndpointDto.UpdateRequest req) {
        return ApiResponse.ok(endpointService.update(id, SecurityUtils.getTenantId(), req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        endpointService.delete(id, SecurityUtils.getTenantId());
        return ApiResponse.ok();
    }

    @GetMapping("/quota")
    public ApiResponse<EndpointDto.QuotaInfo> getQuota() {
        return ApiResponse.ok(endpointService.getQuota(SecurityUtils.getTenantId(), SecurityUtils.getUserId()));
    }
}
