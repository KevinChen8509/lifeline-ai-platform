package com.lifeline.modules.ai_model.repository;

import com.lifeline.modules.ai_model.entity.AiModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiModelVersionRepository extends JpaRepository<AiModelVersion, String> {

    List<AiModelVersion> findByModelIdOrderByCreatedAtDesc(String modelId);

    List<AiModelVersion> findByModelIdAndStatusOrderByCreatedAtDesc(String modelId, String status);

    Optional<AiModelVersion> findByModelIdAndIsCurrentTrue(String modelId);

    Optional<AiModelVersion> findByModelIdAndVersion(String modelId, String version);

    long countByModelId(String modelId);
}
