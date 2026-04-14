package com.lifeline.modules.dashboard.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.dashboard.entity.Report;
import com.lifeline.modules.dashboard.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "Project")
    public Report generate(@RequestBody Map<String, Object> body) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return reportService.generateReport(
                (String) body.get("type"),
                (String) body.get("projectId"),
                (String) body.get("startDate"),
                (String) body.get("endDate"),
                (String) body.get("templateId"),
                userId);
    }

    @GetMapping
    @RequirePermission(action = "read", subject = "Project")
    public PageResponse<Report> list(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return reportService.listReports(projectId, type, page, pageSize);
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "read", subject = "Project")
    public Report get(@PathVariable String id) {
        return reportService.getReport(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(action = "manage", subject = "Project")
    public void delete(@PathVariable String id) {
        reportService.deleteReport(id);
    }

    @PostMapping("/{id}/export-pdf")
    @RequirePermission(action = "read", subject = "Project")
    public Map<String, Object> exportPdf(@PathVariable String id) {
        return reportService.exportPdf(id);
    }
}
