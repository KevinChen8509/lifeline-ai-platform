package com.lifeline.modules.alert.repository;

import com.lifeline.modules.alert.entity.AlertStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertStatusHistoryRepository extends JpaRepository<AlertStatusHistory, String> {

    List<AlertStatusHistory> findByAlertIdOrderByCreatedAtAsc(String alertId);
}
