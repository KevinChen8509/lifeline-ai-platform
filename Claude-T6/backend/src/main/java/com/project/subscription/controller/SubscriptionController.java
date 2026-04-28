package com.project.subscription.controller;

import com.project.subscription.config.SecurityUtils;
import com.project.subscription.model.dto.ApiResponse;
import com.project.subscription.model.dto.PageResponse;
import com.project.subscription.model.dto.RuleDto;
import com.project.subscription.model.dto.SubscriptionDto;
import com.project.subscription.model.entity.WebhookSubscription;
import com.project.subscription.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ApiResponse<PageResponse<WebhookSubscription>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WebhookSubscription> result = subscriptionService.list(SecurityUtils.getTenantId(), SecurityUtils.getUserId(), page, size);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @PostMapping
    public ApiResponse<SubscriptionDto.Response> create(@Valid @RequestBody SubscriptionDto.CreateRequest req) {
        return ApiResponse.ok(subscriptionService.create(SecurityUtils.getTenantId(), SecurityUtils.getUserId(), req));
    }

    @GetMapping("/{id}")
    public ApiResponse<SubscriptionDto.Response> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(subscriptionService.getDetail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubscriptionDto.Response> update(@PathVariable Long id,
                                                         @RequestBody SubscriptionDto.UpdateRequest req) {
        return ApiResponse.ok(subscriptionService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> toggleStatus(@PathVariable Long id, @RequestParam Short status) {
        subscriptionService.toggleStatus(id, status);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        subscriptionService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/batch-status")
    public ApiResponse<Void> batchToggleStatus(@RequestBody SubscriptionDto.BatchStatusRequest req) {
        subscriptionService.batchToggleStatus(req);
        return ApiResponse.ok();
    }

    @GetMapping("/conflicts")
    public ApiResponse<List<RuleDto>> checkConflicts(
            @RequestParam Long deviceId,
            @RequestParam(required = false) List<Long> dataPointIds,
            @RequestParam(required = false) Long excludeSubscriptionId) {
        return ApiResponse.ok(subscriptionService.checkConflicts(deviceId, dataPointIds, excludeSubscriptionId));
    }
}
