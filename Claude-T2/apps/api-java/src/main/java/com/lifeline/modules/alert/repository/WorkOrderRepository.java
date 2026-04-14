package com.lifeline.modules.alert.repository;

import com.lifeline.modules.alert.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, String> {

    List<WorkOrder> findByAlertIdOrderByCreatedAtDesc(String alertId);

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT MAX(w.workOrderNo) FROM WorkOrder w WHERE w.workOrderNo LIKE :prefix")
    String findMaxWorkOrderNoByPrefix(String prefix);
}
