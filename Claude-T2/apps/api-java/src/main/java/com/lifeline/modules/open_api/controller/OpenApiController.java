package com.lifeline.modules.open_api.controller;

import com.lifeline.common.permission.RequirePermission;
import com.lifeline.modules.open_api.entity.ApiKey;
import com.lifeline.modules.open_api.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api-keys")
@RequiredArgsConstructor
public class OpenApiController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    @RequirePermission(action = "read", subject = "ApiKey")
    public List<ApiKey> findAll(@RequestParam(required = false) String userId) {
        return apiKeyService.findAll(userId);
    }

    @GetMapping("/{id}")
    @RequirePermission(action = "read", subject = "ApiKey")
    public ApiKey findOne(@PathVariable String id) {
        return apiKeyService.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(action = "manage", subject = "ApiKey")
    public ApiKey create(@RequestBody Map<String, String> body) {
        String userId = SecurityContextHolder.getContext().getAuthentication().getName();
        return apiKeyService.create(
                body.get("name"),
                body.get("description"),
                userId,
                body.get("projectId"),
                body.get("permissions")
        );
    }

    @PutMapping("/{id}/disable")
    @RequirePermission(action = "manage", subject = "ApiKey")
    public ApiKey disable(@PathVariable String id) {
        apiKeyService.disable(id);
        return apiKeyService.findOne(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(action = "manage", subject = "ApiKey")
    public void remove(@PathVariable String id) {
        apiKeyService.remove(id);
    }
}
