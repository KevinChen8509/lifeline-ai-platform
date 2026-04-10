package com.lifeline.modules.open_api.service;

import com.lifeline.common.exception.BusinessException;
import com.lifeline.modules.open_api.entity.ApiKey;
import com.lifeline.modules.open_api.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    public List<ApiKey> findAll(String userId) {
        if (userId != null) {
            return apiKeyRepository.findAll().stream()
                    .filter(k -> k.getUserId().equals(userId))
                    .toList();
        }
        return apiKeyRepository.findAll();
    }

    public ApiKey findOne(String id) {
        return apiKeyRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("API Key不存在: " + id));
    }

    @Transactional
    public ApiKey create(String name, String description, String userId, String projectId, String permissions) {
        ApiKey apiKey = new ApiKey();
        apiKey.setKey("lk_live_" + UUID.randomUUID().toString().replace("-", ""));
        apiKey.setName(name);
        apiKey.setSecret(UUID.randomUUID().toString().replace("-", ""));
        apiKey.setDescription(description);
        apiKey.setUserId(userId);
        apiKey.setProjectId(projectId);
        apiKey.setPermissions(permissions);
        return apiKeyRepository.save(apiKey);
    }

    @Transactional
    public void disable(String id) {
        ApiKey apiKey = findOne(id);
        apiKey.setStatus(com.lifeline.modules.open_api.entity.ApiKeyStatus.DISABLED);
        apiKeyRepository.save(apiKey);
    }

    @Transactional
    public void remove(String id) {
        apiKeyRepository.delete(findOne(id));
    }
}
