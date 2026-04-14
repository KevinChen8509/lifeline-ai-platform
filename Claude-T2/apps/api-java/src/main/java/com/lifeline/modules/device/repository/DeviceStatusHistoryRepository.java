package com.lifeline.modules.device.repository;

import com.lifeline.modules.device.entity.DeviceStatusHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceStatusHistoryRepository extends JpaRepository<DeviceStatusHistory, String> {

    @Query("SELECT h FROM DeviceStatusHistory h WHERE h.deviceId = :deviceId " +
           "AND (:startDate IS NULL OR h.timestamp >= :startDate) " +
           "AND (:endDate IS NULL OR h.timestamp <= :endDate)")
    Page<DeviceStatusHistory> findByDeviceIdWithDateFilter(
            @Param("deviceId") String deviceId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            Pageable pageable);
}
