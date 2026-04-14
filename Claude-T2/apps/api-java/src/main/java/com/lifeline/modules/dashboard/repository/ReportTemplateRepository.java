package com.lifeline.modules.dashboard.repository;

import com.lifeline.modules.dashboard.entity.ReportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportTemplateRepository extends JpaRepository<ReportTemplate, String> {

    List<ReportTemplate> findByProjectIdOrderByCreatedAtDesc(String projectId);

    List<ReportTemplate> findByProjectIdIsNullOrderByCreatedAtDesc();

    List<ReportTemplate> findAllByOrderByCreatedAtDesc();
}
