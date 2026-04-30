package com.iot.platform.interfaces.http;

import com.iot.platform.application.AlarmApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alarm-stats")
@RequiredArgsConstructor
public class AlarmStatsController {

    private final AlarmApplicationService service;

    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary() {
        return ApiResult.ok(service.getSummary());
    }

    @GetMapping("/trend")
    public ApiResult<List<Map<String, Object>>> trend(
            @RequestParam(defaultValue = "7") int days) {
        return ApiResult.ok(service.getTrend(days));
    }

    @GetMapping("/by-device-type")
    public ApiResult<List<Map<String, Object>>> byDeviceType() {
        return ApiResult.ok(service.getByDeviceType());
    }

    @GetMapping("/top-devices")
    public ApiResult<List<Map<String, Object>>> topDevices(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResult.ok(service.getTopDevices(limit));
    }
}
