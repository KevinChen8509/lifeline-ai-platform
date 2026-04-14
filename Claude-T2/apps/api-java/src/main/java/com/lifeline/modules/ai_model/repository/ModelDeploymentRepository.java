package com.lifeline.modules.ai_model.repository;

import com.lifeline.modules.ai_model.entity.ModelDeployment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelDeploymentRepository extends JpaRepository<ModelDeployment, String> {

    Page<ModelDeployment> findByModelId(String modelId, Pageable pageable);

    Page<ModelDeployment> findByModelIdAndStatus(String modelId, String status, Pageable pageable);
}
