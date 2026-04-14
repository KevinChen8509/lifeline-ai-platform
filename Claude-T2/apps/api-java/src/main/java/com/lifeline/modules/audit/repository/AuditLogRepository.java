package com.lifeline.modules.audit.repository;

import com.lifeline.modules.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:action IS NULL OR a.action = :action) " +
           "AND (:targetType IS NULL OR a.targetType = :targetType) " +
           "AND (:operatorId IS NULL OR a.operatorId = :operatorId)")
    Page<AuditLog> findAllWithFilters(@Param("action") String action,
                                      @Param("targetType") String targetType,
                                      @Param("operatorId") String operatorId,
                                      Pageable pageable);

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:action IS NULL OR a.action = :action) " +
           "AND (:targetType IS NULL OR a.targetType = :targetType) " +
           "AND (:operatorId IS NULL OR a.operatorId = :operatorId) " +
           "ORDER BY a.createdAt DESC")
    java.util.List<AuditLog> findTop10000WithFilters(@Param("action") String action,
                                                       @Param("targetType") String targetType,
                                                       @Param("operatorId") String operatorId,
                                                       org.springframework.data.domain.Pageable pageable);
}
