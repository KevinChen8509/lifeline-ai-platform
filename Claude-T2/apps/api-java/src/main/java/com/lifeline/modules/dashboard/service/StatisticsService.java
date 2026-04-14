package com.lifeline.modules.dashboard.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StatisticsService {

    public Map<String, Object> getAlertTypeDistribution(String projectId, String startDate, String endDate) {
        // Placeholder: 5 alert types
        List<Map<String, Object>> distribution = new ArrayList<>();
        String[] types = {"MIXED_CONNECTION", "SILT", "OVERFLOW", "FULL_PIPE", "THRESHOLD_EXCEEDED"};
        for (String type : types) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("count", 0);
            item.put("percentage", 0.0);
            distribution.add(item);
        }
        return Map.of("distribution", distribution, "total", 0);
    }

    public Map<String, Object> getAlertHandlingEfficiency(String projectId, String startDate, String endDate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avgResponseTime", 0);
        result.put("avgHandleTime", 0);
        result.put("timelinessRate", 0.0);
        result.put("distribution", Map.of("lt1h", 0, "1to4h", 0, "4to24h", 0, "gt24h", 0));
        result.put("slaThresholds", Map.of("CRITICAL", 4, "HIGH", 8, "MEDIUM", 24, "LOW", 72));
        return result;
    }
}
