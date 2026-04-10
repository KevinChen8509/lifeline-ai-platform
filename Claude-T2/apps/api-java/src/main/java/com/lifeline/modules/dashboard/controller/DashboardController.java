package com.lifeline.modules.dashboard.controller;

import com.lifeline.modules.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
