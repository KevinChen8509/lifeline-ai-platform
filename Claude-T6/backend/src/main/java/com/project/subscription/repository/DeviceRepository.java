package com.project.subscription.repository;

import com.project.subscription.model.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {
    Page<Device> findByTenantId(Long tenantId, Pageable pageable);
    Page<Device> findByTenantIdAndProductId(Long tenantId, Long productId, Pageable pageable);
    List<Device> findByProductId(Long productId);
    Optional<Device> findByDeviceKey(String deviceKey);
    Optional<Device> findByIdAndTenantId(Long id, Long tenantId);
}
