package com.lifeline.modules.telemetry.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.telemetry.entity.BackupConfig;
import com.lifeline.modules.telemetry.entity.BackupLog;
import com.lifeline.modules.telemetry.entity.DeviceTelemetry;
import com.lifeline.modules.telemetry.entity.enums.BackupType;
import com.lifeline.modules.telemetry.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    // === Telemetry Data ===

    @GetMapping("/telemetry")
    @RequirePermission(action = "read", subject = "Telemetry")
    public PageResponse<DeviceTelemetry> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return telemetryService.findAll(page, pageSize, deviceId, startTime, endTime);
    }

    @PostMapping("/devices/{deviceId}/telemetry")
    @RequirePermission(action = "manage", subject = "Device")
    public DeviceTelemetry writeTelemetry(@PathVariable String deviceId, @RequestBody Map<String, Object> body) {
        String metrics = body.get("metrics") != null ? body.get("metrics").toString() : "{}";
        LocalDateTime timestamp = body.get("timestamp") != null
                ? LocalDateTime.parse((String) body.get("timestamp")) : null;
        return telemetryService.writeTelemetry(deviceId, metrics, timestamp);
    }

    @GetMapping("/devices/{deviceId}/telemetry")
    @RequirePermission(action = "read", subject = "Device")
    public PageResponse<DeviceTelemetry> getDeviceTelemetry(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return telemetryService.findAll(page, pageSize, deviceId, startTime, endTime);
    }

    @GetMapping("/devices/{deviceId}/telemetry/chart")
    @RequirePermission(action = "read", subject = "Device")
    public Map<String, Object> getChartData(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "level,flow") String metrics,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return telemetryService.getChartData(deviceId, metrics, interval, startTime, endTime);
    }

    // === Backup Management ===

    @PostMapping("/backup/configs")
    @RequirePermission(action = "manage", subject = "Device")
    public BackupConfig createBackupConfig(@RequestBody Map<String, Object> body) {
        BackupType type = BackupType.valueOf((String) body.get("type"));
        String schedule = (String) body.get("schedule");
        Integer retentionDays = body.get("retentionDays") != null
                ? ((Number) body.get("retentionDays")).intValue() : null;
        String storagePath = (String) body.get("storagePath");
        return telemetryService.createBackupConfig(type, schedule, retentionDays, storagePath);
    }

    @GetMapping("/backup/configs")
    @RequirePermission(action = "read", subject = "Device")
    public List<BackupConfig> listBackupConfigs() {
        return telemetryService.listBackupConfigs();
    }

    @PostMapping("/backup/configs/{configId}/execute")
    @RequirePermission(action = "manage", subject = "Device")
    public BackupLog executeBackup(@PathVariable String configId) {
        return telemetryService.executeBackup(configId);
    }

    @GetMapping("/backup/logs")
    @RequirePermission(action = "read", subject = "Device")
    public List<BackupLog> listBackupLogs(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return telemetryService.listBackupLogs(type, status);
    }

    @PostMapping("/backup/{backupId}/restore")
    @RequirePermission(action = "manage", subject = "Device")
    public BackupLog restoreBackup(@PathVariable String backupId) {
        return telemetryService.restoreBackup(backupId);
    }
}
