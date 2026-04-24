package com.project.subscription.controller;

import com.project.subscription.model.dto.ApiResponse;
import com.project.subscription.model.dto.DeliveryLogDto;
import com.project.subscription.model.dto.PageResponse;
import com.project.subscription.model.entity.WebhookDeliveryLog;
import com.project.subscription.service.WebhookLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhook-logs")
@RequiredArgsConstructor
public class WebhookLogController {

    private final WebhookLogService logService;

    @GetMapping
    public ApiResponse<PageResponse<WebhookDeliveryLog>> list(DeliveryLogDto.QueryParams params) {
        Page<WebhookDeliveryLog> result = logService.listLogs(params);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<WebhookDeliveryLog> getDetail(@PathVariable Long id) {
        return ApiResponse.ok(logService.getDetail(id));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retry(@PathVariable Long id) {
        logService.retry(id);
        return ApiResponse.ok();
    }

    @GetMapping("/stats")
    public ApiResponse<DeliveryLogDto.StatsResponse> getStats(@RequestParam Long endpointId) {
        return ApiResponse.ok(logService.getStats(endpointId));
    }
}
