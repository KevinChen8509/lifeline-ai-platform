package com.project.subscription.repository;

import com.project.subscription.model.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {
    List<WebhookEndpoint> findByTenantIdAndUserId(Long tenantId, Long userId);
    long countByTenantIdAndUserId(Long tenantId, Long userId);
    long countByTenantId(Long tenantId);
    Optional<WebhookEndpoint> findByIdAndTenantId(Long id, Long tenantId);
}
