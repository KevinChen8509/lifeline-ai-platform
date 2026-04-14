package com.lifeline.modules.telemetry.repository;

import com.lifeline.modules.telemetry.entity.BackupConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackupConfigRepository extends JpaRepository<BackupConfig, String> {
}
