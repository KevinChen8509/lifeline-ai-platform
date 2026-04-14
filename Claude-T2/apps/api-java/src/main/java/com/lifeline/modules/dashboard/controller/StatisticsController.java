package com.lifeline.modules.dashboard.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.modules.dashboard.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/alert-type-distribution")
    @RequirePermission(action = "read", subject = "Alert")
    public Map<String, Object> getAlertTypeDistribution(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return statisticsService.getAlertTypeDistribution(projectId, startDate, endDate);
    }

    @GetMapping("/alert-handling-efficiency")
    @RequirePermission(action = "read", subject = "Alert")
    public Map<String, Object> getAlertHandlingEfficiency(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return statisticsService.getAlertHandlingEfficiency(projectId, startDate, endDate);
    }
}
