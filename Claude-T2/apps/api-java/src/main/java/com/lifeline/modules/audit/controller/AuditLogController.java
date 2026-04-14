package com.lifeline.modules.audit.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.audit.entity.AuditLog;
import com.lifeline.modules.audit.service.AuditLogService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @RequirePermission(action = "read", subject = "AuditLog")
    public PageResponse<AuditLog> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String operatorId) {
        return auditLogService.findAll(page, pageSize, action, targetType, operatorId);
    }

    @GetMapping("/export")
    @RequirePermission(action = "read", subject = "AuditLog")
    public void exportCsv(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String operatorId,
            HttpServletResponse response) throws IOException {

        String csv = auditLogService.exportCsv(action, targetType, operatorId);

        String filename = "audit-logs-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".csv";
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.getWriter().write(csv);
        response.getWriter().flush();
    }
}
