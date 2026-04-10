package com.lifeline.modules.alert.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.alert.entity.Alert;
import com.lifeline.modules.alert.entity.AlertLevel;
import com.lifeline.modules.alert.entity.AlertStatus;
import com.lifeline.modules.alert.entity.AlertType;
import com.lifeline.modules.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    @GetMapping
    @RequirePermission(action = "read", subject = "Alert")
    public PageResponse<Alert> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertType type,
            @RequestParam(required = false) AlertLevel level,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String search) {
        return alertService.findAll(page, pageSize, status, type, level, projectId, deviceId, search);
    }

    @GetMapping("/stats")
    @RequirePermission(action = "read", subject = "Alert")
    public Map<String, Long> getStats() {
        return alertService.getStats();
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "read", subject = "Alert")
    public Alert findOne(@PathVariable String id) {
        return alertService.findOne(id);
    }

    @PostMapping("/{id}/acknowledge")
    @RequirePermission(action = "update", subject = "Alert")
    public Alert acknowledge(@PathVariable String id) {
        return alertService.acknowledge(id);
    }

    @PostMapping("/{id}/process")
    @RequirePermission(action = "update", subject = "Alert")
    public Alert process(@PathVariable String id) {
        return alertService.process(id);
    }

    @PostMapping("/{id}/close")
    @RequirePermission(action = "update", subject = "Alert")
    public Alert close(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String resolution = body != null ? body.get("resolution") : null;
        String rootCause = body != null ? body.get("rootCause") : null;
        return alertService.close(id, resolution, rootCause);
    }
}
