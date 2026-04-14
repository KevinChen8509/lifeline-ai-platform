package com.lifeline.modules.telemetry.repository;

import com.lifeline.modules.telemetry.entity.DeviceTelemetry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface TelemetryRepository extends JpaRepository<DeviceTelemetry, String> {

    @Query("SELECT t FROM DeviceTelemetry t WHERE " +
           "(:deviceId IS NULL OR t.deviceId = :deviceId) " +
           "AND (:startTime IS NULL OR t.timestamp >= :startTime) " +
           "AND (:endTime IS NULL OR t.timestamp <= :endTime)")
    Page<DeviceTelemetry> findAllWithFilters(@Param("deviceId") String deviceId,
                                             @Param("startTime") String startTime,
                                             @Param("endTime") String endTime,
                                             Pageable pageable);

    List<DeviceTelemetry> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
            String deviceId, LocalDateTime start, LocalDateTime end);
}
