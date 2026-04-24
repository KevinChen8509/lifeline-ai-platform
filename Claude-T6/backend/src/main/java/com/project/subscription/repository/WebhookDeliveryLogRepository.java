package com.project.subscription.repository;

import com.project.subscription.model.entity.WebhookDeliveryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long> {
    Page<WebhookDeliveryLog> findByConfigId(Long configId, Pageable pageable);
    Page<WebhookDeliveryLog> findBySubscriptionId(Long subscriptionId, Pageable pageable);
    Optional<WebhookDeliveryLog> findByEventId(String eventId);

    @Query("SELECT l FROM WebhookDeliveryLog l WHERE l.status IN :statuses AND l.nextRetryAt <= :now ORDER BY l.nextRetryAt ASC")
    List<WebhookDeliveryLog> findPendingRetries(@Param("statuses") List<String> statuses, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT COUNT(l) FROM WebhookDeliveryLog l WHERE l.configId = :configId AND l.createdAt >= :since")
    long countRecentByEndpoint(@Param("configId") Long configId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(l) FROM WebhookDeliveryLog l WHERE l.configId = :configId AND l.status = 'SUCCESS' AND l.createdAt >= :since")
    long countSuccessByEndpoint(@Param("configId") Long configId, @Param("since") LocalDateTime since);
}
