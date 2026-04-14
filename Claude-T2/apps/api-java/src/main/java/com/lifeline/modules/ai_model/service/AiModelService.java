package com.lifeline.modules.ai_model.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.ai_model.dto.CreateAiModelDto;
import com.lifeline.modules.ai_model.dto.UpdateAiModelDto;
import com.lifeline.modules.ai_model.entity.*;
import com.lifeline.modules.ai_model.entity.enums.DeploymentStatus;
import com.lifeline.modules.ai_model.entity.enums.DeviceDeploymentStatus;
import com.lifeline.modules.ai_model.entity.enums.ModelVersionStatus;
import com.lifeline.modules.ai_model.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelRepository aiModelRepository;
    private final AiModelVersionRepository versionRepository;
    private final ModelDeploymentRepository deploymentRepository;
    private final DeviceDeploymentRepository deviceDeploymentRepository;

    // === Model CRUD ===

    public PageResponse<AiModel> findAll(int page, int pageSize, AiModelStatus status,
                                         AiModelType type, String search) {
        String statusStr = status != null ? status.name() : null;
        String typeStr = type != null ? type.name() : null;
        Page<AiModel> result = aiModelRepository.findAllWithFilters(
                statusStr, typeStr, search,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    public AiModel findOne(String id) {
        return aiModelRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("模型不存在: " + id));
    }

    @Transactional
    public AiModel create(CreateAiModelDto dto) {
        AiModel model = new AiModel();
        model.setName(dto.getName());
        model.setCode(dto.getCode());
        model.setVersion(dto.getVersion());
        model.setType(AiModelType.valueOf(dto.getType()));
        model.setDescription(dto.getDescription());
        model.setFileUrl(dto.getFileUrl());
        model.setFileSize(dto.getFileSize());
        model.setChecksum(dto.getChecksum());
        model.setSpecs(dto.getSpecs());
        model.setApplicableDeviceTypes(dto.getApplicableDeviceTypes());
        model.setStatus(AiModelStatus.draft);
        return aiModelRepository.save(model);
    }

    @Transactional
    public AiModel update(String id, UpdateAiModelDto dto) {
        AiModel model = findOne(id);
        if (dto.getName() != null) model.setName(dto.getName());
        if (dto.getVersion() != null) model.setVersion(dto.getVersion());
        if (dto.getDescription() != null) model.setDescription(dto.getDescription());
        if (dto.getFileUrl() != null) model.setFileUrl(dto.getFileUrl());
        if (dto.getFileSize() != null) model.setFileSize(dto.getFileSize());
        if (dto.getChecksum() != null) model.setChecksum(dto.getChecksum());
        if (dto.getSpecs() != null) model.setSpecs(dto.getSpecs());
        if (dto.getApplicableDeviceTypes() != null) model.setApplicableDeviceTypes(dto.getApplicableDeviceTypes());
        return aiModelRepository.save(model);
    }

    @Transactional
    public AiModel publish(String id) {
        AiModel model = findOne(id);
        model.setStatus(AiModelStatus.published);
        return aiModelRepository.save(model);
    }

    @Transactional
    public AiModel deprecate(String id) {
        AiModel model = findOne(id);
        model.setStatus(AiModelStatus.deprecated);
        return aiModelRepository.save(model);
    }

    @Transactional
    public void remove(String id) {
        aiModelRepository.delete(findOne(id));
    }

    // === Version Management ===

    public List<AiModelVersion> listVersions(String modelId, String status) {
        if (status != null) {
            return versionRepository.findByModelIdAndStatusOrderByCreatedAtDesc(modelId, status);
        }
        return versionRepository.findByModelIdOrderByCreatedAtDesc(modelId);
    }

    public AiModelVersion getCurrentVersion(String modelId) {
        return versionRepository.findByModelIdAndIsCurrentTrue(modelId).orElse(null);
    }

    public AiModelVersion getVersion(String modelId, String versionId) {
        return versionRepository.findById(versionId)
                .orElseThrow(() -> BusinessException.notFound("版本不存在: " + versionId));
    }

    @Transactional
    public AiModelVersion createVersion(String modelId, String version, String fileUrl,
                                         Integer fileSize, String checksum, String signature,
                                         String changeLog, String specs) {
        findOne(modelId); // verify model exists
        versionRepository.findByModelIdAndVersion(modelId, version).ifPresent(v -> {
            throw BusinessException.badRequest("版本号已存在: " + version);
        });

        AiModelVersion v = new AiModelVersion();
        v.setModelId(modelId);
        v.setVersion(version);
        v.setFileUrl(fileUrl);
        v.setFileSize(fileSize);
        v.setChecksum(checksum);
        v.setSignature(signature);
        v.setChangeLog(changeLog);
        v.setSpecs(specs);
        v.setStatus(ModelVersionStatus.draft);
        v.setIsCurrent(false);
        return versionRepository.save(v);
    }

    @Transactional
    public AiModelVersion updateVersion(String modelId, String versionId, String fileUrl,
                                         Integer fileSize, String checksum, String signature,
                                         String changeLog, String specs) {
        AiModelVersion v = getVersion(modelId, versionId);
        if (v.getStatus() == ModelVersionStatus.published) {
            throw BusinessException.badRequest("已发布版本不可修改");
        }
        if (fileUrl != null) v.setFileUrl(fileUrl);
        if (fileSize != null) v.setFileSize(fileSize);
        if (checksum != null) v.setChecksum(checksum);
        if (signature != null) v.setSignature(signature);
        if (changeLog != null) v.setChangeLog(changeLog);
        if (specs != null) v.setSpecs(specs);
        return versionRepository.save(v);
    }

    @Transactional
    public AiModelVersion publishVersion(String modelId, String versionId) {
        AiModelVersion v = getVersion(modelId, versionId);
        if (v.getStatus() == ModelVersionStatus.published) {
            throw BusinessException.badRequest("版本已发布");
        }
        if (v.getFileUrl() == null || v.getChecksum() == null) {
            throw BusinessException.badRequest("发布版本需要提供文件URL和校验和");
        }

        // Unset previous current version
        versionRepository.findByModelIdAndIsCurrentTrue(modelId).ifPresent(prev -> {
            prev.setIsCurrent(false);
            versionRepository.save(prev);
        });

        // Publish new version
        v.setStatus(ModelVersionStatus.published);
        v.setIsCurrent(true);
        v.setPublishedAt(LocalDateTime.now());
        versionRepository.save(v);

        // Update parent model
        AiModel model = findOne(modelId);
        model.setVersion(v.getVersion());
        model.setFileUrl(v.getFileUrl());
        model.setFileSize(v.getFileSize());
        model.setChecksum(v.getChecksum());
        model.setStatus(AiModelStatus.published);
        aiModelRepository.save(model);

        return v;
    }

    @Transactional
    public AiModelVersion deprecateVersion(String modelId, String versionId) {
        AiModelVersion v = getVersion(modelId, versionId);
        if (v.getStatus() != ModelVersionStatus.published) {
            throw BusinessException.badRequest("只能废弃已发布的版本");
        }
        if (Boolean.TRUE.equals(v.getIsCurrent())) {
            throw BusinessException.badRequest("不能废弃当前版本，请先发布其他版本");
        }
        v.setStatus(ModelVersionStatus.deprecated);
        return versionRepository.save(v);
    }

    // === Deployment Management ===

    @Transactional
    public ModelDeployment createDeployment(String modelId, String targetVersion,
                                             List<String> deviceIds, String description, String createdBy) {
        AiModel model = findOne(modelId);

        // Resolve target version
        String version = targetVersion;
        if (version == null) {
            AiModelVersion current = getCurrentVersion(modelId);
            if (current == null) {
                throw BusinessException.badRequest("模型没有已发布的当前版本");
            }
            version = current.getVersion();
        }

        ModelDeployment deployment = new ModelDeployment();
        deployment.setModelId(modelId);
        deployment.setTargetVersion(version);
        deployment.setTotalDevices(deviceIds.size());
        deployment.setPendingCount(deviceIds.size());
        deployment.setCreatedBy(createdBy);
        deployment.setDescription(description);
        deployment.setStatus(DeploymentStatus.pending);
        deploymentRepository.save(deployment);

        // Create per-device deployment records
        for (String deviceId : deviceIds) {
            DeviceDeployment dd = new DeviceDeployment();
            dd.setDeploymentId(deployment.getId());
            dd.setDeviceId(deviceId);
            dd.setStatus(DeviceDeploymentStatus.pending);
            dd.setProgress(0);
            dd.setRetryCount(0);
            deviceDeploymentRepository.save(dd);
        }

        return deployment;
    }

    public PageResponse<ModelDeployment> listDeployments(String modelId, String status, int page, int pageSize) {
        Page<ModelDeployment> result;
        if (status != null) {
            result = deploymentRepository.findByModelIdAndStatus(modelId, status,
                    PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        } else {
            result = deploymentRepository.findByModelId(modelId,
                    PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        }
        return PageResponse.of(result.getContent(), result.getTotalElements(), page, pageSize);
    }

    public Map<String, Object> getDeploymentDetail(String modelId, String deploymentId) {
        ModelDeployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> BusinessException.notFound("部署任务不存在: " + deploymentId));

        List<DeviceDeployment> deviceDeployments = deviceDeploymentRepository.findByDeploymentId(deploymentId);

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("total", deployment.getTotalDevices());
        progress.put("success", deployment.getSuccessCount());
        progress.put("failed", deployment.getFailedCount());
        progress.put("pending", deployment.getPendingCount());
        progress.put("downloading", 0);
        progress.put("installing", 0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deployment", deployment);
        result.put("deviceDeployments", deviceDeployments);
        result.put("progress", progress);
        return result;
    }

    @Transactional
    public Map<String, Object> retryDeployment(String modelId, String deploymentId) {
        List<DeviceDeployment> failed = deviceDeploymentRepository
                .findByDeploymentIdAndStatus(deploymentId, DeviceDeploymentStatus.failed);

        for (DeviceDeployment dd : failed) {
            dd.setStatus(DeviceDeploymentStatus.pending);
            dd.setProgress(0);
            dd.setError(null);
            dd.setFailureReason(null);
            dd.setRetryCount(dd.getRetryCount() + 1);
            deviceDeploymentRepository.save(dd);
        }

        ModelDeployment deployment = deploymentRepository.findById(deploymentId).orElseThrow();
        deployment.setStatus(DeploymentStatus.in_progress);
        deploymentRepository.save(deployment);

        return Map.of("success", true, "retryCount", failed.size());
    }

    @Transactional
    public ModelDeployment cancelDeployment(String modelId, String deploymentId) {
        ModelDeployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> BusinessException.notFound("部署任务不存在"));

        if (deployment.getStatus() == DeploymentStatus.completed || deployment.getStatus() == DeploymentStatus.cancelled) {
            throw BusinessException.badRequest("不能取消已完成或已取消的部署");
        }

        // Set all pending device deployments to skipped
        List<DeviceDeployment> pending = deviceDeploymentRepository
                .findByDeploymentIdAndStatus(deploymentId, DeviceDeploymentStatus.pending);
        for (DeviceDeployment dd : pending) {
            dd.setStatus(DeviceDeploymentStatus.skipped);
            deviceDeploymentRepository.save(dd);
        }

        deployment.setStatus(DeploymentStatus.cancelled);
        return deploymentRepository.save(deployment);
    }
}
