package com.lifeline.modules.alert.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.alert.entity.Alert;
import com.lifeline.modules.alert.entity.AlertLevel;
import com.lifeline.modules.alert.entity.AlertStatus;
import com.lifeline.modules.alert.entity.AlertType;
import com.lifeline.modules.alert.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    public PageResponse<Alert> findAll(int page, int pageSize, AlertStatus status, AlertType type,
                                       AlertLevel level, String projectId, String deviceId, String search) {
        String statusStr = status != null ? status.name() : null;
        String typeStr = type != null ? type.name() : null;
        String levelStr = level != null ? level.name() : null;
        Page<Alert> result = alertRepository.findAllWithFilters(
                statusStr, typeStr, levelStr, projectId, deviceId, search,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    public Alert findOne(String id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("预警不存在: " + id));
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        for (var entry : alertRepository.countByLevel()) {
            stats.put(String.valueOf(entry.get("level")), ((Number) entry.get("count")).longValue());
        }
        // Ensure all levels present
        for (AlertLevel level : AlertLevel.values()) {
            stats.putIfAbsent(level.name(), 0L);
        }
        return stats;
    }

    @Transactional
    public Alert acknowledge(String id) {
        Alert alert = findOne(id);
        if (alert.getStatus() != AlertStatus.pending) {
            throw BusinessException.badRequest("只能确认待处理的预警");
        }
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        alert.setStatus(AlertStatus.acknowledged);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedBy(userId);
        return alertRepository.save(alert);
    }

    @Transactional
    public Alert process(String id) {
        Alert alert = findOne(id);
        if (alert.getStatus() != AlertStatus.acknowledged) {
            throw BusinessException.badRequest("只能处置已确认的预警");
        }
        alert.setStatus(AlertStatus.in_progress);
        return alertRepository.save(alert);
    }

    @Transactional
    public Alert close(String id, String resolution, String rootCause) {
        Alert alert = findOne(id);
        if (alert.getStatus() == AlertStatus.closed) {
            throw BusinessException.badRequest("预警已关闭");
        }
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        alert.setStatus(AlertStatus.closed);
        alert.setClosedAt(LocalDateTime.now());
        alert.setClosedBy(userId);
        alert.setResolution(resolution);
        alert.setRootCause(rootCause);
        return alertRepository.save(alert);
    }
}
