package com.project.subscription.repository;

import com.project.subscription.model.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByTenantId(Long tenantId);
    Optional<Product> findByProductKey(String productKey);
}
