package com.lifeline.modules.dashboard.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.dashboard.entity.*;
import com.lifeline.modules.dashboard.entity.enums.*;
import com.lifeline.modules.dashboard.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportTemplateRepository templateRepository;
    private final ReportRepository reportRepository;
    private final ScheduledReportRepository scheduledReportRepository;
    private final ReportDeliveryLogRepository deliveryLogRepository;

    // === Report Templates ===

    @Transactional
    public ReportTemplate createTemplate(String name, ReportType type, String sections,
                                          Boolean isDefault, String projectId) {
        ReportTemplate template = new ReportTemplate();
        template.setName(name);
        template.setType(type);
        template.setSections(sections);
        template.setIsDefault(isDefault != null ? isDefault : true);
        template.setProjectId(projectId);
        return templateRepository.save(template);
    }

    public List<ReportTemplate> listTemplates(String projectId) {
        if (projectId != null) {
            List<ReportTemplate> result = new ArrayList<>();
            result.addAll(templateRepository.findByProjectIdOrderByCreatedAtDesc(projectId));
            result.addAll(templateRepository.findByProjectIdIsNullOrderByCreatedAtDesc());
            return result;
        }
        return templateRepository.findAllByOrderByCreatedAtDesc();
    }

    public ReportTemplate getTemplate(String id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("报告模板不存在: " + id));
    }

    @Transactional
    public ReportTemplate updateTemplate(String id, String name, String sections) {
        ReportTemplate template = getTemplate(id);
        if (name != null) template.setName(name);
        if (sections != null) template.setSections(sections);
        return templateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(String id) {
        ReportTemplate template = getTemplate(id);
        if (Boolean.TRUE.equals(template.getIsDefault())) {
            throw BusinessException.badRequest("不能删除默认模板");
        }
        templateRepository.delete(template);
    }

    // === Reports ===

    @Transactional
    public Report generateReport(String type, String projectId, String startDate, String endDate,
                                  String templateId, String generatedBy) {
        ReportType reportType = ReportType.valueOf(type);
        ReportTemplate template = getTemplate(templateId);

        Report report = new Report();
        report.setTitle(reportType.name() + " Report - " + projectId);
        report.setType(reportType);
        report.setProjectId(projectId);
        report.setTemplateId(templateId);
        report.setStatus(ReportStatus.GENERATING);
        report.setStartDate(LocalDateTime.parse(startDate));
        report.setEndDate(LocalDateTime.parse(endDate));
        report.setGeneratedBy(generatedBy);
        reportRepository.save(report);

        // Simulate report generation
        report.setStatus(ReportStatus.COMPLETED);
        report.setGeneratedAt(LocalDateTime.now());
        report.setData("{}");
        return reportRepository.save(report);
    }

    public PageResponse<Report> listReports(String projectId, String type, int page, int pageSize) {
        Page<Report> result;
        if (projectId != null && type != null) {
            result = reportRepository.findByProjectIdAndTypeOrderByCreatedAtDesc(projectId, type,
                    PageRequest.of(page - 1, pageSize));
        } else if (projectId != null) {
            result = reportRepository.findByProjectIdOrderByCreatedAtDesc(projectId,
                    PageRequest.of(page - 1, pageSize));
        } else {
            result = reportRepository.findAllByOrderByCreatedAtDesc(
                    PageRequest.of(page - 1, pageSize));
        }
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    public Report getReport(String id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("报告不存在: " + id));
    }

    @Transactional
    public void deleteReport(String id) {
        reportRepository.delete(getReport(id));
    }

    public Map<String, Object> exportPdf(String id) {
        Report report = getReport(id);
        if (report.getStatus() != ReportStatus.COMPLETED) {
            throw BusinessException.badRequest("只能导出已完成的报告");
        }
        // TODO: actual PDF generation
        return Map.of("filePath", report.getFilePath() != null ? report.getFilePath() : "",
                "fileSize", report.getFileSize() != null ? report.getFileSize() : 0);
    }

    // === Scheduled Reports ===

    @Transactional
    public ScheduledReport createScheduledReport(String name, ReportType type, String projectIds,
                                                  String recipients, String templateId, String createdBy) {
        ScheduledReport sr = new ScheduledReport();
        sr.setName(name);
        sr.setType(type);
        sr.setProjectIds(projectIds);
        sr.setRecipients(recipients);
        sr.setTemplateId(templateId);
        sr.setCreatedBy(createdBy);
        sr.setStatus(ScheduleStatus.ACTIVE);

        // Auto-generate cron from type
        String cron = switch (type) {
            case DAILY -> "0 8 * * *";
            case WEEKLY -> "0 8 * * 1";
            case MONTHLY -> "0 8 1 * *";
            default -> "0 8 * * *";
        };
        sr.setCron(cron);

        return scheduledReportRepository.save(sr);
    }

    public List<ScheduledReport> listScheduledReports() {
        return scheduledReportRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public ScheduledReport updateScheduledReport(String id, String name, String projectIds,
                                                   String recipients, ScheduleStatus status) {
        ScheduledReport sr = scheduledReportRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("定时报告不存在: " + id));
        if (name != null) sr.setName(name);
        if (projectIds != null) sr.setProjectIds(projectIds);
        if (recipients != null) sr.setRecipients(recipients);
        if (status != null) sr.setStatus(status);
        return scheduledReportRepository.save(sr);
    }

    @Transactional
    public void deleteScheduledReport(String id) {
        ScheduledReport sr = scheduledReportRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("定时报告不存在: " + id));
        scheduledReportRepository.delete(sr);
    }

    @Transactional
    public Report executeScheduledReport(String id) {
        ScheduledReport sr = scheduledReportRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("定时报告不存在: " + id));

        // Calculate date range based on type
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = switch (sr.getType()) {
            case DAILY -> endDate.minusHours(24);
            case WEEKLY -> endDate.minusDays(7);
            case MONTHLY -> endDate.minusDays(30);
            default -> endDate.minusHours(24);
        };

        // Generate report for first project (simplified)
        Report report = generateReport(sr.getType().name(), "default",
                startDate.toString(), endDate.toString(), sr.getTemplateId(), sr.getCreatedBy());

        sr.setLastRunAt(LocalDateTime.now());
        scheduledReportRepository.save(sr);

        return report;
    }
}
