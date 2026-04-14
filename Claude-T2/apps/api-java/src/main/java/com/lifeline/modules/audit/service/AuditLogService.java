package com.lifeline.modules.audit.service;

import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.audit.entity.AuditLog;
import com.lifeline.modules.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public PageResponse<AuditLog> findAll(int page, int pageSize, String action, String targetType, String operatorId) {
        Page<AuditLog> result = auditLogRepository.findAllWithFilters(
                action, targetType, operatorId,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    @Transactional
    public AuditLog create(AuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }

    public String exportCsv(String action, String targetType, String operatorId) {
        List<AuditLog> logs = auditLogRepository.findTop10000WithFilters(
                action, targetType, operatorId,
                PageRequest.of(0, 10000)
        );

        StringBuilder sb = new StringBuilder();
        // BOM for Excel Chinese compatibility
        sb.append("\uFEFF");
        sb.append("Time,Username,ActionType,TargetType,TargetID,IPAddress,Description\n");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (AuditLog log : logs) {
            sb.append(log.getCreatedAt() != null ? log.getCreatedAt().format(fmt) : "").append(",");
            sb.append("\"").append(extractUsername(log)).append("\",");
            sb.append(log.getAction()).append(",");
            sb.append(log.getTargetType()).append(",");
            sb.append(log.getTargetId() != null ? log.getTargetId() : "").append(",");
            sb.append(log.getIpAddress() != null ? log.getIpAddress() : "").append(",");
            sb.append("\"").append(log.getDescription() != null ? log.getDescription().replace("\"", "\"\"") : "").append("\"\n");
        }
        return sb.toString();
    }

    private String extractUsername(AuditLog log) {
        if (log.getOperator() != null && log.getOperator().contains("username")) {
            try {
                String op = log.getOperator();
                int idx = op.indexOf("\"username\"");
                if (idx >= 0) {
                    int start = op.indexOf(":", idx) + 1;
                    int end = op.indexOf(",", start);
                    if (end < 0) end = op.indexOf("}", start);
                    return op.substring(start, end).replaceAll("[\"\\s]", "");
                }
            } catch (Exception ignored) {}
        }
        return log.getOperatorId();
    }
}
