package com.lifeline.modules.dashboard.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.modules.dashboard.entity.ReportTemplate;
import com.lifeline.modules.dashboard.entity.enums.ReportType;
import com.lifeline.modules.dashboard.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/report-templates")
@RequiredArgsConstructor
public class ReportTemplateController {

    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "Project")
    public ReportTemplate create(@RequestBody Map<String, Object> body) {
        return reportService.createTemplate(
                (String) body.get("name"),
                ReportType.valueOf((String) body.get("type")),
                body.get("sections") != null ? body.get("sections").toString() : null,
                (Boolean) body.get("isDefault"),
                (String) body.get("projectId"));
    }

    @GetMapping
    @RequirePermission(action = "read", subject = "Project")
    public List<ReportTemplate> list(@RequestParam(required = false) String projectId) {
        return reportService.listTemplates(projectId);
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "read", subject = "Project")
    public ReportTemplate get(@PathVariable String id) {
        return reportService.getTemplate(id);
    }

    @PutMapping("/{id}")
    @RequirePermission(action = "manage", subject = "Project")
    public ReportTemplate update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return reportService.updateTemplate(id,
                (String) body.get("name"),
                body.get("sections") != null ? body.get("sections").toString() : null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(action = "manage", subject = "Project")
    public void delete(@PathVariable String id) {
        reportService.deleteTemplate(id);
    }
}
