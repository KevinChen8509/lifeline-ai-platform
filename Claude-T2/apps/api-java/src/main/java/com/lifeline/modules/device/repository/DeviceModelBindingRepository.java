package com.lifeline.modules.device.repository;

import com.lifeline.modules.device.entity.DeviceModelBinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface DeviceModelBindingRepository extends JpaRepository<DeviceModelBinding, String> {

    Page<DeviceModelBinding> findByDeviceId(String deviceId, Pageable pageable);

    Optional<DeviceModelBinding> findByDeviceIdAndModelId(String deviceId, String modelId);

    void deleteByDeviceIdAndModelId(String deviceId, String modelId);

    @Query("SELECT b.modelId, COUNT(b) FROM DeviceModelBinding b WHERE b.modelId IN :modelIds GROUP BY b.modelId")
    java.util.List<Object[]> countByModelIds(@org.springframework.data.repository.query.Param("modelIds") java.util.List<String> modelIds);

    long countByModelId(String modelId);
}
