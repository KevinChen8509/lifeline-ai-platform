package com.lifeline.modules.dashboard.service;

import com.lifeline.modules.alert.repository.AlertRepository;
import com.lifeline.modules.device.repository.DeviceRepository;
import com.lifeline.modules.project.repository.ProjectRepository;
import com.lifeline.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DeviceRepository deviceRepository;
    private final AlertRepository alertRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalProjects", projectRepository.count());
        stats.put("totalDevices", deviceRepository.count());
        stats.put("totalUsers", userRepository.count());
        stats.put("pendingAlerts", alertRepository.count());
        return stats;
    }

    public Map<String, Object> getAlertDistribution() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : alertRepository.countByLevel()) {
            result.put(String.valueOf(entry.get("level")), entry.get("count"));
        }
        return result;
    }

    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new LinkedHashMap<>();

        Map<String, Object> services = new LinkedHashMap<>();
        services.put("api", Map.of("status", "UP", "uptime", ManagementFactory.getRuntimeMXBean().getUptime() / 1000));
        services.put("database", Map.of("status", "UP"));
        services.put("redis", Map.of("status", "UP"));
        status.put("services", services);

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMem = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
        long maxMem = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
        status.put("memory", Map.of("used", usedMem + "MB", "max", maxMem + "MB"));
        status.put("timestamp", java.time.LocalDateTime.now().toString());

        return status;
    }
}
