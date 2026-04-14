package com.lifeline.modules.alert.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.alert.entity.*;
import com.lifeline.modules.alert.entity.enums.WorkOrderStatus;
import com.lifeline.modules.alert.repository.AlertRepository;
import com.lifeline.modules.alert.repository.AlertStatusHistoryRepository;
import com.lifeline.modules.alert.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertStatusHistoryRepository statusHistoryRepository;
    private final WorkOrderRepository workOrderRepository;

    private static final Map<AlertLevel, Integer> OVERDUE_MINUTES = Map.of(
            AlertLevel.critical, 30,
            AlertLevel.high, 120,
            AlertLevel.medium, 480,
            AlertLevel.low, 1440
    );

    private static final Map<AlertStatus, Integer> PROGRESS_MAP = Map.of(
            AlertStatus.pending, 0,
            AlertStatus.acknowledged, 25,
            AlertStatus.in_progress, 50,
            AlertStatus.resolved, 75,
            AlertStatus.closed, 100
    );

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
        for (AlertLevel level : AlertLevel.values()) {
            stats.putIfAbsent(level.name(), 0L);
        }
        return stats;
    }

    public Map<String, Object> getStatsSummary(String projectId) {
        Map<String, Object> result = new LinkedHashMap<>();
        // Count by level for non-closed alerts
        List<Alert> alerts;
        if (projectId != null) {
            alerts = alertRepository.findByProjectIdAndStatusNot(projectId, AlertStatus.closed);
        } else {
            alerts = alertRepository.findByStatusNot(AlertStatus.closed);
        }

        Map<String, Long> byLevel = new LinkedHashMap<>();
        long unacknowledged = 0;
        for (AlertLevel level : AlertLevel.values()) {
            byLevel.put(level.name(), 0L);
        }
        for (Alert alert : alerts) {
            byLevel.merge(alert.getLevel().name(), 1L, Long::sum);
            if (alert.getStatus() == AlertStatus.pending) {
                unacknowledged++;
            }
        }
        result.putAll(byLevel);
        result.put("unacknowledged", unacknowledged);
        return result;
    }

    public Map<String, Object> getTimeline(String alertId) {
        Alert alert = findOne(alertId);
        List<AlertStatusHistory> nodes = statusHistoryRepository.findByAlertIdOrderByCreatedAtAsc(alertId);
        List<WorkOrder> workOrders = workOrderRepository.findByAlertIdOrderByCreatedAtDesc(alertId);

        int progress = PROGRESS_MAP.getOrDefault(alert.getStatus(), 0);
        boolean isOverdue = checkOverdue(alert);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodes", nodes);
        result.put("progress", progress);
        result.put("isOverdue", isOverdue);
        result.put("workOrders", workOrders);
        return result;
    }

    @Transactional
    public WorkOrder createWorkOrder(String alertId, String title, String description,
                                      String assigneeId, AlertLevel priority, LocalDateTime dueDate) {
        Alert alert = findOne(alertId);
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        // Generate WO number: WO-YYYYMMDD-NNNN
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "WO-" + dateStr + "-";
        String maxNo = workOrderRepository.findMaxWorkOrderNoByPrefix(prefix + "%");
        int nextNum = 1;
        if (maxNo != null) {
            String numPart = maxNo.substring(prefix.length());
            nextNum = Integer.parseInt(numPart) + 1;
        }
        String workOrderNo = prefix + String.format("%04d", nextNum);

        WorkOrder wo = new WorkOrder();
        wo.setWorkOrderNo(workOrderNo);
        wo.setAlertId(alertId);
        wo.setTitle(title);
        wo.setDescription(description);
        wo.setAssigneeId(assigneeId);
        wo.setPriority(priority != null ? priority : alert.getLevel());
        wo.setDueDate(dueDate);
        wo.setStatus(WorkOrderStatus.pending);
        workOrderRepository.save(wo);

        // Auto-transition alert to IN_PROGRESS if PENDING or ACKNOWLEDGED
        if (alert.getStatus() == AlertStatus.pending || alert.getStatus() == AlertStatus.acknowledged) {
            AlertStatus oldStatus = alert.getStatus();
            alert.setStatus(AlertStatus.in_progress);
            alertRepository.save(alert);
            recordStatusChange(alertId, oldStatus, AlertStatus.in_progress, userId,
                    "生成工单: " + workOrderNo);
        }

        return wo;
    }

    @Transactional
    public Alert acknowledge(String id) {
        Alert alert = findOne(id);
        if (alert.getStatus() != AlertStatus.pending) {
            throw BusinessException.badRequest("只能确认待处理的预警");
        }
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        AlertStatus oldStatus = alert.getStatus();
        alert.setStatus(AlertStatus.acknowledged);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alert.setAcknowledgedBy(userId);
        alertRepository.save(alert);
        recordStatusChange(id, oldStatus, AlertStatus.acknowledged, userId, null);
        return alert;
    }

    @Transactional
    public Alert process(String id, String description) {
        Alert alert = findOne(id);
        if (alert.getStatus() != AlertStatus.acknowledged && alert.getStatus() != AlertStatus.pending) {
            throw BusinessException.badRequest("只能处置已确认或待处理的预警");
        }
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        AlertStatus oldStatus = alert.getStatus();
        alert.setStatus(AlertStatus.in_progress);
        alertRepository.save(alert);
        recordStatusChange(id, oldStatus, AlertStatus.in_progress, userId, description);
        return alert;
    }

    @Transactional
    public Alert close(String id, String resolution, String rootCause) {
        Alert alert = findOne(id);
        if (alert.getStatus() == AlertStatus.closed) {
            throw BusinessException.badRequest("预警已关闭");
        }
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        AlertStatus oldStatus = alert.getStatus();
        alert.setStatus(AlertStatus.closed);
        alert.setClosedAt(LocalDateTime.now());
        alert.setClosedBy(userId);
        alert.setResolution(resolution);
        alert.setRootCause(rootCause);
        alertRepository.save(alert);
        recordStatusChange(id, oldStatus, AlertStatus.closed, userId, resolution);
        return alert;
    }

    private boolean checkOverdue(Alert alert) {
        if (alert.getStatus() == AlertStatus.closed || alert.getStatus() == AlertStatus.resolved) {
            return false;
        }
        Integer thresholdMinutes = OVERDUE_MINUTES.get(alert.getLevel());
        if (thresholdMinutes == null) return false;
        return Duration.between(alert.getCreatedAt(), LocalDateTime.now()).toMinutes() > thresholdMinutes;
    }

    private void recordStatusChange(String alertId, AlertStatus oldStatus, AlertStatus newStatus,
                                     String operatorId, String note) {
        AlertStatusHistory history = new AlertStatusHistory();
        history.setAlertId(alertId);
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setOperatorId(operatorId);
        history.setNote(note);
        statusHistoryRepository.save(history);
    }
}
