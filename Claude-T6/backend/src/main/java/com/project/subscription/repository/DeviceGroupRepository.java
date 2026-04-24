package com.project.subscription.repository;

import com.project.subscription.model.entity.DeviceGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceGroupRepository extends JpaRepository<DeviceGroup, Long> {
    List<DeviceGroup> findByTenantIdAndParentIdIsNull(Long tenantId);
    List<DeviceGroup> findByTenantIdAndParentId(Long tenantId, Long parentId);
}
