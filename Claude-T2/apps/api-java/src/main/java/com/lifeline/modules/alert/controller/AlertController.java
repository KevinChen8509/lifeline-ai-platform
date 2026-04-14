package com.lifeline.modules.alert.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.alert.entity.Alert;
import com.lifeline.modules.alert.entity.AlertLevel;
import com.lifeline.modules.alert.entity.AlertStatus;
import com.lifeline.modules.alert.entity.AlertType;
import com.lifeline.modules.alert.entity.WorkOrder;
import com.lifeline.modules.alert.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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

    @GetMapping("/stats/summary")
    @RequirePermission(action = "read", subject = "Alert")
    public Map<String, Object> getStatsSummary(@RequestParam(required = false) String projectId) {
        return alertService.getStatsSummary(projectId);
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "read", subject = "Alert")
    public Alert findOne(@PathVariable String id) {
        return alertService.findOne(id);
    }

    @GetMapping("/{id}/timeline")
    @RequirePermission(action = "read", subject = "Alert")
    public Map<String, Object> getTimeline(@PathVariable String id) {
        return alertService.getTimeline(id);
    }

    @PostMapping("/{id}/acknowledge")
    @RequirePermission(action = "manage", subject = "Alert")
    public Alert acknowledge(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return alertService.acknowledge(id);
    }

    @PostMapping("/{id}/process")
    @RequirePermission(action = "manage", subject = "Alert")
    public Alert process(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String description = body != null ? body.get("description") : null;
        return alertService.process(id, description);
    }

    @PostMapping("/{id}/close")
    @RequirePermission(action = "manage", subject = "Alert")
    public Alert close(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        String resolution = body != null ? body.get("resolution") : null;
        String rootCause = body != null ? body.get("rootCause") : null;
        return alertService.close(id, resolution, rootCause);
    }

    @PostMapping("/{id}/work-order")
    @RequirePermission(action = "manage", subject = "Alert")
    public WorkOrder createWorkOrder(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        String assigneeId = (String) body.get("assigneeId");
        AlertLevel priority = body.get("priority") != null
                ? AlertLevel.valueOf((String) body.get("priority")) : null;
        LocalDateTime dueDate = body.get("dueDate") != null
                ? LocalDateTime.parse((String) body.get("dueDate")) : null;
        return alertService.createWorkOrder(id, title, description, assigneeId, priority, dueDate);
    }
}
