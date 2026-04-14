package com.lifeline.modules.dashboard.controller;

import com.lifeline.common.permission.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    @GetMapping("/status")
    @RequirePermission(action = "read", subject = "System")
    public Map<String, Object> getStatus() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("api", Map.of("status", "UP", "uptime",
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000));
        services.put("database", Map.of("status", "UP"));
        services.put("redis", Map.of("status", "UP"));
        services.put("mqtt", Map.of("status", "UP"));
        result.put("services", services);
        result.put("availability30d", 99.95);
        result.put("onlineUsers", 1);
        result.put("todayApiCalls", 0);
        result.put("timestamp", LocalDateTime.now().toString());

        return result;
    }

    @GetMapping("/resources")
    @RequirePermission(action = "read", subject = "System")
    public Map<String, Object> getResources() {
        Map<String, Object> result = new LinkedHashMap<>();

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

        double cpuUsage = 0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            cpuUsage = sunOs.getCpuLoad() * 100;
        }

        long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMem = totalMem - freeMem;
        double memUsage = totalMem > 0 ? (double) usedMem / totalMem * 100 : 0;

        result.put("cpu", Map.of("usage", Math.round(cpuUsage * 100.0) / 100.0,
                "cores", osBean.getAvailableProcessors(),
                "warning", cpuUsage > 80));
        result.put("memory", Map.of("total", totalMem + "MB", "used", usedMem + "MB",
                "free", freeMem + "MB", "usagePercent", Math.round(memUsage * 100.0) / 100.0,
                "warning", memUsage > 85));
        result.put("disk", Map.of("usage", "N/A", "warning", false));
        result.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        result.put("timestamp", LocalDateTime.now().toString());

        return result;
    }
}
