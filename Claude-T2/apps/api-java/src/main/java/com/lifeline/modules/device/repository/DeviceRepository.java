package com.lifeline.modules.device.repository;

import com.lifeline.modules.device.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, String> {

    @Query("SELECT d FROM Device d WHERE " +
           "(:status IS NULL OR d.status = :status) " +
           "AND (:projectId IS NULL OR d.projectId = :projectId) " +
           "AND (:protocol IS NULL OR d.protocol = :protocol) " +
           "AND (:search IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(d.serialNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Device> findAllWithFilters(@Param("status") String status,
                                    @Param("projectId") String projectId,
                                    @Param("protocol") String protocol,
                                    @Param("search") String search,
                                    Pageable pageable);
}
