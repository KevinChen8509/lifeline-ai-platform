package com.lifeline.modules.ai_model.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.ai_model.dto.CreateAiModelDto;
import com.lifeline.modules.ai_model.dto.UpdateAiModelDto;
import com.lifeline.modules.ai_model.entity.AiModel;
import com.lifeline.modules.ai_model.entity.AiModelStatus;
import com.lifeline.modules.ai_model.entity.AiModelType;
import com.lifeline.modules.ai_model.entity.AiModelVersion;
import com.lifeline.modules.ai_model.entity.ModelDeployment;
import com.lifeline.modules.ai_model.service.AiModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai-models")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService aiModelService;

    // === Model CRUD ===

    @GetMapping
    @RequirePermission(action = "read", subject = "Model")
    public PageResponse<AiModel> findAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) AiModelStatus status,
            @RequestParam(required = false) AiModelType type,
            @RequestParam(required = false) String search) {
        return aiModelService.findAll(page, pageSize, status, type, search);
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "read", subject = "Model")
    public AiModel findOne(@PathVariable String id) {
        return aiModelService.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "create", subject = "Model")
    public AiModel create(@Valid @RequestBody CreateAiModelDto dto) {
        return aiModelService.create(dto);
    }

    @PutMapping("/{id}")
    @RequirePermission(action = "update", subject = "Model")
    public AiModel update(@PathVariable String id, @Valid @RequestBody UpdateAiModelDto dto) {
        return aiModelService.update(id, dto);
    }

    @PutMapping("/{id}/publish")
    @RequirePermission(action = "update", subject = "Model")
    public AiModel publish(@PathVariable String id) {
        return aiModelService.publish(id);
    }

    @PutMapping("/{id}/deprecate")
    @RequirePermission(action = "update", subject = "Model")
    public AiModel deprecate(@PathVariable String id) {
        return aiModelService.deprecate(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(action = "delete", subject = "Model")
    public void remove(@PathVariable String id) {
        aiModelService.remove(id);
    }

    // === Version Management ===

    @GetMapping("/{id}/versions")
    @RequirePermission(action = "read", subject = "Model")
    public List<AiModelVersion> listVersions(
            @PathVariable String id,
            @RequestParam(required = false) String status) {
        return aiModelService.listVersions(id, status);
    }

    @GetMapping("/{id}/versions/current")
    @RequirePermission(action = "read", subject = "Model")
    public AiModelVersion getCurrentVersion(@PathVariable String id) {
        return aiModelService.getCurrentVersion(id);
    }

    @GetMapping("/{id}/versions/{versionId}")
    @RequirePermission(action = "read", subject = "Model")
    public AiModelVersion getVersion(@PathVariable String id, @PathVariable String versionId) {
        return aiModelService.getVersion(id, versionId);
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "Model")
    public AiModelVersion createVersion(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return aiModelService.createVersion(id,
                (String) body.get("version"),
                (String) body.get("fileUrl"),
                body.get("fileSize") != null ? ((Number) body.get("fileSize")).intValue() : null,
                (String) body.get("checksum"),
                (String) body.get("signature"),
                (String) body.get("changeLog"),
                body.get("specs") != null ? body.get("specs").toString() : null);
    }

    @PutMapping("/{id}/versions/{versionId}")
    @RequirePermission(action = "manage", subject = "Model")
    public AiModelVersion updateVersion(@PathVariable String id, @PathVariable String versionId,
                                         @RequestBody Map<String, Object> body) {
        return aiModelService.updateVersion(id, versionId,
                (String) body.get("fileUrl"),
                body.get("fileSize") != null ? ((Number) body.get("fileSize")).intValue() : null,
                (String) body.get("checksum"),
                (String) body.get("signature"),
                (String) body.get("changeLog"),
                body.get("specs") != null ? body.get("specs").toString() : null);
    }

    @PutMapping("/{id}/versions/{versionId}/publish")
    @RequirePermission(action = "manage", subject = "Model")
    public AiModelVersion publishVersion(@PathVariable String id, @PathVariable String versionId) {
        return aiModelService.publishVersion(id, versionId);
    }

    @PutMapping("/{id}/versions/{versionId}/deprecate")
    @RequirePermission(action = "manage", subject = "Model")
    public AiModelVersion deprecateVersion(@PathVariable String id, @PathVariable String versionId) {
        return aiModelService.deprecateVersion(id, versionId);
    }

    // === Deployment Management ===

    @PostMapping("/{id}/deploy")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "Model")
    public ModelDeployment createDeployment(@PathVariable String id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> deviceIds = (List<String>) body.get("deviceIds");
        String version = (String) body.get("version");
        String description = (String) body.get("description");
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return aiModelService.createDeployment(id, version, deviceIds, description, userId);
    }

    @GetMapping("/{id}/deployments")
    @RequirePermission(action = "read", subject = "Model")
    public PageResponse<ModelDeployment> listDeployments(
            @PathVariable String id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return aiModelService.listDeployments(id, status, page, pageSize);
    }

    @GetMapping("/{id}/deployments/{deploymentId}")
    @RequirePermission(action = "read", subject = "Model")
    public Map<String, Object> getDeploymentDetail(@PathVariable String id, @PathVariable String deploymentId) {
        return aiModelService.getDeploymentDetail(id, deploymentId);
    }

    @PostMapping("/{id}/deployments/{deploymentId}/retry")
    @RequirePermission(action = "manage", subject = "Model")
    public Map<String, Object> retryDeployment(@PathVariable String id, @PathVariable String deploymentId) {
        return aiModelService.retryDeployment(id, deploymentId);
    }

    @PostMapping("/{id}/deployments/{deploymentId}/cancel")
    @RequirePermission(action = "manage", subject = "Model")
    public ModelDeployment cancelDeployment(@PathVariable String id, @PathVariable String deploymentId) {
        return aiModelService.cancelDeployment(id, deploymentId);
    }
}
