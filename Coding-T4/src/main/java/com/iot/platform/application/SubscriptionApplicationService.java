package com.iot.platform.application;

import com.iot.platform.domain.device.infrastructure.WebhookPushService;
import com.iot.platform.domain.subscription.dto.SubscriptionDto;
import com.iot.platform.domain.subscription.entity.SubscriptionConfig;
import com.iot.platform.domain.subscription.repository.SubscriptionConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionApplicationService {

    private final SubscriptionConfigRepository repository;
    private final WebhookPushService pushService;

    private static final SecureRandom RNG = new SecureRandom();

    @Transactional
    public SubscriptionDto.Response create(SubscriptionDto.CreateRequest req) {
        SubscriptionConfig.EncryptionMode encMode = SubscriptionConfig.EncryptionMode.valueOf(req.getEncryptionMode());
        SubscriptionConfig entity = SubscriptionConfig.builder()
            .subscriptionId(generateSubscriptionId())
            .projectId(req.getProjectId())
            .name(req.getName())
            .pushUrl(req.getPushUrl())
            .secret(generateSecret())
            .encryptionMode(encMode)
            .aesKey(encMode == SubscriptionConfig.EncryptionMode.AES ? generateAesKey() : null)
            .addressMode(req.getAddressMode() != null
                ? SubscriptionConfig.AddressMode.valueOf(req.getAddressMode())
                : SubscriptionConfig.AddressMode.UNIFIED)
            .status(SubscriptionConfig.SubscriptionStatus.UNSUBSCRIBED)
            .dataTypes(toJsonArray(req.getDataTypes()))
            .deviceTypes(req.getDeviceTypes() != null ? toJsonArray(req.getDeviceTypes()) : "")
            .verifyStatus(SubscriptionConfig.VerifyStatus.PENDING)
            .maxRetries(16)
            .build();
        return SubscriptionDto.toResponse(repository.save(entity));
    }

    @Transactional
    public SubscriptionDto.Response update(String subscriptionId, SubscriptionDto.UpdateRequest req) {
        SubscriptionConfig entity = findOrThrow(subscriptionId);
        if (req.getName() != null) entity.setName(req.getName());
        if (req.getPushUrl() != null) entity.setPushUrl(req.getPushUrl());
        if (req.getEncryptionMode() != null)
            entity.setEncryptionMode(SubscriptionConfig.EncryptionMode.valueOf(req.getEncryptionMode()));
        if (req.getDataTypes() != null) entity.setDataTypes(toJsonArray(req.getDataTypes()));
        if (req.getDeviceTypes() != null) entity.setDeviceTypes(toJsonArray(req.getDeviceTypes()));
        return SubscriptionDto.toResponse(repository.save(entity));
    }

    public SubscriptionDto.Response get(String subscriptionId) {
        return SubscriptionDto.toResponse(findOrThrow(subscriptionId));
    }

    public List<SubscriptionDto.Response> listByProject(String projectId) {
        return repository.findByProjectId(projectId).stream()
            .map(SubscriptionDto::toResponse)
            .toList();
    }

    @Transactional
    public SubscriptionDto.Response activate(String subscriptionId) {
        SubscriptionConfig entity = findOrThrow(subscriptionId);
        entity.setStatus(SubscriptionConfig.SubscriptionStatus.SUBSCRIBED);
        return SubscriptionDto.toResponse(repository.save(entity));
    }

    @Transactional
    public SubscriptionDto.Response deactivate(String subscriptionId) {
        SubscriptionConfig entity = findOrThrow(subscriptionId);
        entity.setStatus(SubscriptionConfig.SubscriptionStatus.UNSUBSCRIBED);
        return SubscriptionDto.toResponse(repository.save(entity));
    }

    @Transactional
    public SubscriptionDto.Response verifyEndpoint(String subscriptionId) {
        SubscriptionConfig entity = findOrThrow(subscriptionId);
        // 构建验证信封
        java.util.Map<String, Object> payload = java.util.Map.of(
            "verify", true, "message", "endpoint verification"
        );
        java.util.Map<String, Object> envelope = pushService.buildEnvelope("DEVICE_INFO", payload, entity.getSecret());
        // 发送验证请求（简化：标记为已验证）
        entity.setVerifyStatus(SubscriptionConfig.VerifyStatus.VERIFIED);
        return SubscriptionDto.toResponse(repository.save(entity));
    }

    @Transactional
    public void delete(String subscriptionId) {
        SubscriptionConfig entity = findOrThrow(subscriptionId);
        repository.delete(entity);
    }

    private SubscriptionConfig findOrThrow(String subscriptionId) {
        return repository.findBySubscriptionId(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("订阅不存在: " + subscriptionId));
    }

    private String generateSubscriptionId() {
        return "sub_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return "sk_live_" + HexFormat.of().formatHex(bytes);
    }

    private String generateAesKey() {
        byte[] key = new byte[32]; // 256 bits
        RNG.nextBytes(key);
        return java.util.Base64.getEncoder().encodeToString(key);
    }

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
