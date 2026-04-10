package com.lifeline.modules.open_api.repository;

import com.lifeline.modules.open_api.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    Optional<ApiKey> findByKey(String key);
}
