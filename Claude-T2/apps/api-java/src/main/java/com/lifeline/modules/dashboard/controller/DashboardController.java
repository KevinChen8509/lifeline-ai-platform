package com.lifeline.modules.dashboard.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.modules.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return dashboardService.getStats();
    }

    @GetMapping("/alert-distribution")
    public Map<String, Object> getAlertDistribution() {
        return dashboardService.getAlertDistribution();
    }

    @GetMapping("/system-status")
    public Map<String, Object> getSystemStatus() {
        return dashboardService.getSystemStatus();
    }

    // === Per-Project Dashboard ===

    @GetMapping("/projects/{projectId}/device-stats")
    @RequirePermission(action = "read", subject = "Device")
    public Map<String, Object> getProjectDeviceStats(@PathVariable String projectId) {
        return dashboardService.getProjectDeviceStats(projectId);
    }

    @GetMapping("/projects/{projectId}/alert-stats")
    @RequirePermission(action = "read", subject = "Alert")
    public Map<String, Object> getProjectAlertStats(@PathVariable String projectId) {
        return dashboardService.getProjectAlertStats(projectId);
    }

    @GetMapping("/projects/{projectId}/kpi")
    @RequirePermission(action = "read", subject = "Project")
    public Map<String, Object> getProjectKpi(@PathVariable String projectId) {
        return dashboardService.getProjectKpi(projectId);
    }
}
