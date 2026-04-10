package com.lifeline.modules.ai_model.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.common.response.PageResponse;
import com.lifeline.modules.ai_model.dto.CreateAiModelDto;
import com.lifeline.modules.ai_model.dto.UpdateAiModelDto;
import com.lifeline.modules.ai_model.entity.AiModel;
import com.lifeline.modules.ai_model.entity.AiModelStatus;
import com.lifeline.modules.ai_model.entity.AiModelType;
import com.lifeline.modules.ai_model.service.AiModelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai-models")
@RequiredArgsConstructor
public class AiModelController {

    private final AiModelService aiModelService;

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
}
