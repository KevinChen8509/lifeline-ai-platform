package com.lifeline.modules.dashboard.repository;

import com.lifeline.modules.dashboard.entity.ScheduledReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, String> {

    List<ScheduledReport> findAllByOrderByCreatedAtDesc();
}
