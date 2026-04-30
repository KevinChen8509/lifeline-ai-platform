package com.iot.platform.application;

import com.iot.platform.domain.alarm.dto.AlarmDto;
import com.iot.platform.domain.alarm.entity.AlarmRecord;
import com.iot.platform.domain.alarm.repository.AlarmRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlarmApplicationService {

    private final AlarmRecordRepository repository;

    public Page<AlarmDto.Response> query(AlarmDto.QueryParams params) {
        AlarmRecord.Severity severity = params.getSeverity() != null
            ? AlarmRecord.Severity.valueOf(params.getSeverity()) : null;
        AlarmRecord.AlarmStatus status = params.getStatus() != null
            ? AlarmRecord.AlarmStatus.valueOf(params.getStatus()) : null;

        return repository.queryAlarms(
            params.getProjectId(),
            params.getDeviceCode(),
            severity,
            status,
            PageRequest.of(params.getPage(), params.getSize())
        ).map(AlarmDto::toResponse);
    }

    public AlarmDto.Response get(String alarmNo) {
        return AlarmDto.toResponse(findOrThrow(alarmNo));
    }

    @Transactional
    public AlarmDto.Response acknowledge(String alarmNo, String acknowledgedBy) {
        AlarmRecord entity = findOrThrow(alarmNo);
        entity.setStatus(AlarmRecord.AlarmStatus.ACKNOWLEDGED);
        entity.setAcknowledgedAt(LocalDateTime.now());
        entity.setAcknowledgedBy(acknowledgedBy);
        return AlarmDto.toResponse(repository.save(entity));
    }

    @Transactional
    public AlarmDto.Response resolve(String alarmNo) {
        AlarmRecord entity = findOrThrow(alarmNo);
        entity.setStatus(AlarmRecord.AlarmStatus.RESOLVED);
        entity.setResolvedAt(LocalDateTime.now());
        return AlarmDto.toResponse(repository.save(entity));
    }

    // ── 统计方法 ──────────────────────────────────────

    public Map<String, Object> getSummary() {
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (Object[] row : repository.countBySeverity()) {
            bySeverity.put(((AlarmRecord.Severity) row[0]).name(), (Long) row[1]);
        }

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : repository.countByStatus()) {
            byStatus.put(((AlarmRecord.AlarmStatus) row[0]).name(), (Long) row[1]);
        }

        long total = repository.count();

        return Map.of(
            "total", total,
            "bySeverity", bySeverity,
            "byStatus", byStatus
        );
    }

    public List<Map<String, Object>> getTrend(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = repository.countByDay(since);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Object[] row : rows) {
            trend.add(Map.of("date", row[0].toString(), "count", row[1]));
        }
        return trend;
    }

    public List<Map<String, Object>> getByDeviceType() {
        List<Object[]> rows = repository.countByDeviceType();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(Map.of(
                "deviceType", ((com.iot.platform.domain.device.enums.DeviceType) row[0]).name(),
                "count", row[1]
            ));
        }
        return result;
    }

    public List<Map<String, Object>> getTopDevices(int limit) {
        List<Object[]> rows = repository.topDevices(PageRequest.of(0, limit));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceCode", row[0]);
            item.put("deviceName", row[1] != null ? row[1] : "");
            item.put("deviceType", row[2] != null ? row[2].toString() : "");
            item.put("alarmCount", row[3]);
            result.add(item);
        }
        return result;
    }

    private AlarmRecord findOrThrow(String alarmNo) {
        return repository.findByAlarmNo(alarmNo)
            .orElseThrow(() -> new IllegalArgumentException("报警记录不存在: " + alarmNo));
    }
}
