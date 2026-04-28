package com.project.subscription.controller;

import com.project.subscription.config.SecurityUtils;
import com.project.subscription.model.dto.ApiResponse;
import com.project.subscription.model.dto.PageResponse;
import com.project.subscription.model.entity.Device;
import com.project.subscription.model.entity.DeviceDataPoint;
import com.project.subscription.service.DeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping("/devices")
    public ApiResponse<PageResponse<Device>> listDevices(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Device> result = deviceService.listDevices(SecurityUtils.getTenantId(), productId, page, size);
        return ApiResponse.ok(PageResponse.from(result));
    }

    @GetMapping("/devices/{id}")
    public ApiResponse<Device> getDevice(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.getDevice(id, SecurityUtils.getTenantId()));
    }

    @GetMapping("/devices/{id}/datapoints")
    public ApiResponse<List<DeviceDataPoint>> getDataPoints(@PathVariable Long id) {
        return ApiResponse.ok(deviceService.getDataPoints(id));
    }

    @GetMapping("/device-groups/tree")
    public ApiResponse<List<Map<String, Object>>> getGroupTree() {
        return ApiResponse.ok(deviceService.getGroupTree(SecurityUtils.getTenantId()));
    }
}
