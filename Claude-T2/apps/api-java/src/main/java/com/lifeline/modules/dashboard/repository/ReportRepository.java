package com.lifeline.modules.dashboard.repository;

import com.lifeline.modules.dashboard.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, String> {

    Page<Report> findByProjectIdOrderByCreatedAtDesc(String projectId, Pageable pageable);

    Page<Report> findByProjectIdAndTypeOrderByCreatedAtDesc(String projectId, String type, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
