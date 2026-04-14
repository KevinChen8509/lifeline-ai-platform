package com.lifeline.modules.telemetry.repository;

import com.lifeline.modules.telemetry.entity.BackupLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BackupLogRepository extends JpaRepository<BackupLog, String> {

    List<BackupLog> findTop50ByOrderByCreatedAtDesc();

    List<BackupLog> findByTypeOrderByCreatedAtDesc(String type);

    List<BackupLog> findByStatusOrderByCreatedAtDesc(String status);

    List<BackupLog> findByTypeAndStatusOrderByCreatedAtDesc(String type, String status);
}
