package com.lifeline.modules.telemetry.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.telemetry.entity.DeviceTelemetry;
import com.lifeline.modules.telemetry.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @GetMapping
    @RequirePermission(action = "read", subject = "Telemetry")
    public PageResponse<DeviceTelemetry> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return telemetryService.findAll(page, pageSize, deviceId, startTime, endTime);
    }
}
