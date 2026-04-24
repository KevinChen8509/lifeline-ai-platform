package com.project.subscription.repository;

import com.project.subscription.model.entity.DeviceDataPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceDataPointRepository extends JpaRepository<DeviceDataPoint, Long> {
    List<DeviceDataPoint> findByDeviceId(Long deviceId);
}
