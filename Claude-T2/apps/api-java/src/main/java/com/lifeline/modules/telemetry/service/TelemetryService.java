package com.lifeline.modules.telemetry.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.telemetry.entity.BackupConfig;
import com.lifeline.modules.telemetry.entity.BackupLog;
import com.lifeline.modules.telemetry.entity.DeviceTelemetry;
import com.lifeline.modules.telemetry.entity.enums.BackupStatus;
import com.lifeline.modules.telemetry.entity.enums.BackupType;
import com.lifeline.modules.telemetry.repository.BackupConfigRepository;
import com.lifeline.modules.telemetry.repository.BackupLogRepository;
import com.lifeline.modules.telemetry.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TelemetryService {

    private final TelemetryRepository telemetryRepository;
    private final BackupConfigRepository backupConfigRepository;
    private final BackupLogRepository backupLogRepository;

    public PageResponse<DeviceTelemetry> findAll(int page, int pageSize, String deviceId,
                                                  String startTime, String endTime) {
        Page<DeviceTelemetry> result = telemetryRepository.findAllWithFilters(
                deviceId, startTime, endTime,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    @Transactional
    public DeviceTelemetry writeTelemetry(String deviceId, String metrics, LocalDateTime timestamp) {
        DeviceTelemetry t = new DeviceTelemetry();
        t.setDeviceId(deviceId);
        t.setMetrics(metrics);
        t.setTimestamp(timestamp != null ? timestamp : LocalDateTime.now());
        return telemetryRepository.save(t);
    }

    public Map<String, Object> getChartData(String deviceId, String metrics, String interval,
                                             String startTime, String endTime) {
        LocalDateTime end = endTime != null ? LocalDateTime.parse(startTime) : LocalDateTime.now();
        LocalDateTime start;
        if (startTime != null) {
            start = LocalDateTime.parse(startTime);
            end = endTime != null ? LocalDateTime.parse(endTime) : LocalDateTime.now();
        } else {
            start = end.minusHours(24);
        }

        List<DeviceTelemetry> data = telemetryRepository
                .findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(deviceId, start, end);

        List<String> metricList = metrics != null
                ? Arrays.asList(metrics.split(",")) : List.of("level", "flow");

        if ("raw".equals(interval) || interval == null) {
            return Map.of("data", data, "metrics", metricList, "interval", interval != null ? interval : "raw");
        }

        // Aggregate by hour or day
        Map<String, List<DeviceTelemetry>> grouped;
        DateTimeFormatter formatter = "hour".equals(interval)
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")
                : DateTimeFormatter.ofPattern("yyyy-MM-dd");

        grouped = data.stream().collect(Collectors.groupingBy(
                t -> t.getTimestamp().format(formatter),
                LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> aggregated = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", entry.getKey());
            point.put("count", entry.getValue().size());
            aggregated.add(point);
        }

        return Map.of("data", aggregated, "metrics", metricList, "interval", interval);
    }

    // === Backup Management ===

    @Transactional
    public BackupConfig createBackupConfig(BackupType type, String schedule, Integer retentionDays,
                                            String storagePath) {
        BackupConfig config = new BackupConfig();
        config.setType(type);
        config.setSchedule(schedule != null ? schedule : "0 2 * * *");
        config.setRetentionDays(retentionDays != null ? retentionDays : 30);
        config.setStoragePath(storagePath);
        return backupConfigRepository.save(config);
    }

    public List<BackupConfig> listBackupConfigs() {
        return backupConfigRepository.findAll();
    }

    @Transactional
    public BackupLog executeBackup(String configId) {
        BackupConfig config = backupConfigRepository.findById(configId)
                .orElseThrow(() -> BusinessException.notFound("备份配置不存在: " + configId));

        BackupLog log = new BackupLog();
        log.setConfigId(configId);
        log.setType(config.getType());
        log.setStatus(BackupStatus.running);
        log.setStartedAt(LocalDateTime.now());
        backupLogRepository.save(log);

        // Simulate backup
        try {
            Thread.sleep(100);
            log.setStatus(BackupStatus.completed);
            log.setFilePath("/backup/" + configId + "/" + System.currentTimeMillis() + ".tar.gz");
            log.setFileSize((long) (Math.random() * 1024 * 1024 * 100));
            log.setDuration((int) (Math.random() * 300) + 10);
            log.setCompletedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.setStatus(BackupStatus.failed);
            log.setError(e.getMessage());
            log.setCompletedAt(LocalDateTime.now());
        }

        return backupLogRepository.save(log);
    }

    public List<BackupLog> listBackupLogs(String type, String status) {
        if (type != null && status != null) {
            return backupLogRepository.findByTypeAndStatusOrderByCreatedAtDesc(type, status);
        } else if (type != null) {
            return backupLogRepository.findByTypeOrderByCreatedAtDesc(type);
        } else if (status != null) {
            return backupLogRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return backupLogRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional
    public BackupLog restoreBackup(String backupId) {
        BackupLog log = backupLogRepository.findById(backupId)
                .orElseThrow(() -> BusinessException.notFound("备份记录不存在: " + backupId));
        if (log.getStatus() != BackupStatus.completed) {
            throw BusinessException.badRequest("只能从已完成的备份恢复");
        }
        // TODO: actual restore logic
        return log;
    }
}
