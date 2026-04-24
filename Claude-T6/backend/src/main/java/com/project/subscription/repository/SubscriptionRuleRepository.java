package com.project.subscription.repository;

import com.project.subscription.model.entity.SubscriptionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionRuleRepository extends JpaRepository<SubscriptionRule, Long> {
    List<SubscriptionRule> findBySubscriptionId(Long subscriptionId);
    void deleteBySubscriptionId(Long subscriptionId);
}
