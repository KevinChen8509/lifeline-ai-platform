package com.project.subscription.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.subscription.model.dto.EndpointDto;
import com.project.subscription.model.entity.WebhookEndpoint;
import com.project.subscription.model.entity.WebhookSubscription;
import com.project.subscription.repository.WebhookEndpointRepository;
import com.project.subscription.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebhookEndpointService {

    private final WebhookEndpointRepository endpointRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final SecretEncryptor secretEncryptor;
    private final SsrfValidator ssrfValidator;
    private final ObjectMapper objectMapper;

    @Value("${webhook.quota.max-endpoints-per-tenant:50}")
    private int maxEndpointsPerTenant;

    @Transactional
    public EndpointDto.SecretResponse create(Long tenantId, Long userId, EndpointDto.CreateRequest req) {
        // 配额检查
        long count = endpointRepository.countByTenantIdAndUserId(tenantId, userId);
        if (count >= maxEndpointsPerTenant) {
            throw new RuntimeException("已达端点上限（" + count + "/" + maxEndpointsPerTenant + "）");
        }

        // SSRF 校验
        var validation = ssrfValidator.validate(req.getUrl());
        if (!validation.valid()) {
            throw new RuntimeException(validation.errorMessage());
        }

        // 生成签名密钥
        String secret = generateSecret();
        var encrypted = secretEncryptor.encrypt(secret);

        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setTenantId(tenantId);
        endpoint.setUserId(userId);
        endpoint.setName(req.getName());
        endpoint.setUrl(req.getUrl());
        endpoint.setSecretEncrypted(encrypted.encrypted());
        endpoint.setSecretIv(encrypted.iv());
        endpoint.setCustomHeaders(toJson(req.getCustomHeaders()));

        endpoint = endpointRepository.save(endpoint);

        var resp = new EndpointDto.SecretResponse();
        resp.setEndpointId(endpoint.getId());
        resp.setSecret("whsec_" + secret);
        return resp;
    }

    public List<EndpointDto.Response> listByUser(Long tenantId, Long userId) {
        return endpointRepository.findByTenantIdAndUserId(tenantId, userId).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public EndpointDto.Response getById(Long id, Long tenantId) {
        WebhookEndpoint ep = endpointRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("端点不存在"));
        return toResponse(ep);
    }

    @Transactional
    public EndpointDto.Response update(Long id, Long tenantId, EndpointDto.UpdateRequest req) {
        WebhookEndpoint ep = endpointRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("端点不存在"));

        if (req.getName() != null) ep.setName(req.getName());
        if (req.getUrl() != null) {
            var validation = ssrfValidator.validate(req.getUrl());
            if (!validation.valid()) throw new RuntimeException(validation.errorMessage());
            ep.setUrl(req.getUrl());
        }
        if (req.getCustomHeaders() != null) ep.setCustomHeaders(toJson(req.getCustomHeaders()));

        return toResponse(endpointRepository.save(ep));
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        WebhookEndpoint ep = endpointRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new RuntimeException("端点不存在"));

        // 删除保护：检查关联活跃订阅
        List<WebhookSubscription> activeSubs = subscriptionRepository.findByEndpointIdAndStatus(ep.getId(), (short) 0);
        if (!activeSubs.isEmpty()) {
            String names = activeSubs.stream().map(WebhookSubscription::getName).limit(5).collect(Collectors.joining(", "));
            throw new RuntimeException("该端点有 " + activeSubs.size() + " 个活跃订阅（" + names + "），请先停用或删除关联订阅");
        }

        // 停用所有非活跃订阅后删除
        endpointRepository.delete(ep);
    }

    public EndpointDto.QuotaInfo getQuota(Long tenantId, Long userId) {
        long used = endpointRepository.countByTenantIdAndUserId(tenantId, userId);
        var info = new EndpointDto.QuotaInfo();
        info.setUsed((int) used);
        info.setLimit(maxEndpointsPerTenant);
        info.setPercentage((int) (used * 100 / maxEndpointsPerTenant));
        return info;
    }

    public String decryptSecret(WebhookEndpoint endpoint) {
        return secretEncryptor.decrypt(endpoint.getSecretEncrypted(), endpoint.getSecretIv());
    }

    // --- helpers ---

    private String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private EndpointDto.Response toResponse(WebhookEndpoint ep) {
        var r = new EndpointDto.Response();
        r.setId(ep.getId());
        r.setName(ep.getName());
        r.setUrl(ep.getUrl());
        r.setCustomHeaders(fromJson(ep.getCustomHeaders()));
        r.setStatus(ep.getStatus());
        r.setConsecutiveFailures(ep.getConsecutiveFailures());
        r.setLastPushAt(ep.getLastPushAt() != null ? ep.getLastPushAt().toString() : null);
        r.setLastSuccessAt(ep.getLastSuccessAt() != null ? ep.getLastSuccessAt().toString() : null);
        r.setCreatedAt(ep.getCreatedAt() != null ? ep.getCreatedAt().toString() : null);
        return r;
    }

    private String toJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(map); }
        catch (JsonProcessingException e) { return null; }
    }

    private Map<String, String> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException e) { return null; }
    }
}
