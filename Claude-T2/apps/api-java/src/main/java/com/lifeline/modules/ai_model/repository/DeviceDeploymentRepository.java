package com.lifeline.modules.ai_model.repository;

import com.lifeline.modules.ai_model.entity.DeviceDeployment;
import com.lifeline.modules.ai_model.entity.enums.DeviceDeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceDeploymentRepository extends JpaRepository<DeviceDeployment, String> {

    List<DeviceDeployment> findByDeploymentId(String deploymentId);

    List<DeviceDeployment> findByDeploymentIdAndStatus(String deploymentId, DeviceDeploymentStatus status);
}
