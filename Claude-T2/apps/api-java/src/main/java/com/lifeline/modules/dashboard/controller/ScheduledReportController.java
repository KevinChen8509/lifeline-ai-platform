package com.lifeline.modules.dashboard.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.modules.dashboard.entity.Report;
import com.lifeline.modules.dashboard.entity.ScheduledReport;
import com.lifeline.modules.dashboard.entity.enums.ReportType;
import com.lifeline.modules.dashboard.entity.enums.ScheduleStatus;
import com.lifeline.modules.dashboard.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/scheduled-reports")
@RequiredArgsConstructor
public class ScheduledReportController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "Project")
    public ScheduledReport create(@RequestBody Map<String, Object> body) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return reportService.createScheduledReport(
                (String) body.get("name"),
                ReportType.valueOf((String) body.get("type")),
                body.get("projectIds") != null ? body.get("projectIds").toString() : null,
                body.get("recipients") != null ? body.get("recipients").toString() : null,
                (String) body.get("templateId"),
                userId);
    }

    @GetMapping
    @RequirePermission(action = "read", subject = "Project")
    public List<ScheduledReport> list() {
        return reportService.listScheduledReports();
    }

    @PutMapping("/{id}")
    @RequirePermission(action = "manage", subject = "Project")
    public ScheduledReport update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        ScheduleStatus status = body.get("status") != null
                ? ScheduleStatus.valueOf((String) body.get("status")) : null;
        return reportService.updateScheduledReport(id,
                (String) body.get("name"),
                body.get("projectIds") != null ? body.get("projectIds").toString() : null,
                body.get("recipients") != null ? body.get("recipients").toString() : null,
                status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(action = "manage", subject = "Project")
    public void delete(@PathVariable String id) {
        reportService.deleteScheduledReport(id);
    }

    @PostMapping("/{id}/execute")
    @RequirePermission(action = "manage", subject = "Project")
    public Report execute(@PathVariable String id) {
        return reportService.executeScheduledReport(id);
    }
}
