package com.project.subscription.repository;

import com.project.subscription.model.entity.WebhookSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, Long> {
    Page<WebhookSubscription> findByTenantIdAndUserIdAndStatusNot(Long tenantId, Long userId, Short status, Pageable pageable);
    List<WebhookSubscription> findByEndpointIdAndStatus(Long endpointId, Short status);
    long countByUserIdAndStatusNot(Long userId, Short status);

    @Query("SELECT s FROM WebhookSubscription s WHERE s.targetId = :targetId AND s.subscriptionType = :type AND s.status = 0")
    List<WebhookSubscription> findActiveByTargetAndType(@Param("targetId") Long targetId, @Param("type") Short type);
}
