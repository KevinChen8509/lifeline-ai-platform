package com.project.subscription.controller;

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

    // TODO: 从认证上下文获取，暂用请求头模拟
    private Long tenantId = 1L;
    private Long userId = 1L;

    @PostMapping
    public ApiResponse<EndpointDto.SecretResponse> create(@Valid @RequestBody EndpointDto.CreateRequest req) {
        return ApiResponse.ok(endpointService.create(tenantId, userId, req));
    }

    @GetMapping
    public ApiResponse<List<EndpointDto.Response>> list() {
        return ApiResponse.ok(endpointService.listByUser(tenantId, userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<EndpointDto.Response> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(endpointService.getById(id, tenantId));
    }

    @PutMapping("/{id}")
    public ApiResponse<EndpointDto.Response> update(@PathVariable Long id,
                                                     @RequestBody EndpointDto.UpdateRequest req) {
        return ApiResponse.ok(endpointService.update(id, tenantId, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        endpointService.delete(id, tenantId);
        return ApiResponse.ok();
    }

    @GetMapping("/quota")
    public ApiResponse<EndpointDto.QuotaInfo> getQuota() {
        return ApiResponse.ok(endpointService.getQuota(tenantId, userId));
    }
}
