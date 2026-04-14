package com.lifeline.modules.alert.repository;

import com.lifeline.modules.alert.entity.Alert;
import com.lifeline.modules.alert.entity.AlertLevel;
import com.lifeline.modules.alert.entity.AlertStatus;
import com.lifeline.modules.alert.entity.AlertType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface AlertRepository extends JpaRepository<Alert, String> {

    @Query("SELECT a FROM Alert a WHERE " +
           "(:status IS NULL OR a.status = :status) " +
           "AND (:type IS NULL OR a.type = :type) " +
           "AND (:level IS NULL OR a.level = :level) " +
           "AND (:projectId IS NULL OR a.projectId = :projectId) " +
           "AND (:deviceId IS NULL OR a.deviceId = :deviceId) " +
           "AND (:search IS NULL OR LOWER(a.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Alert> findAllWithFilters(@Param("status") String status,
                                   @Param("type") String type,
                                   @Param("level") String level,
                                   @Param("projectId") String projectId,
                                   @Param("deviceId") String deviceId,
                                   @Param("search") String search,
                                   Pageable pageable);

    @Query("SELECT a.level as level, COUNT(a) as count FROM Alert a WHERE a.status <> 'closed' GROUP BY a.level")
    List<Map<String, Object>> countByLevel();

    long countByStatus(AlertStatus status);

    List<Alert> findByStatusNot(AlertStatus status);

    List<Alert> findByProjectIdAndStatusNot(String projectId, AlertStatus status);
}
