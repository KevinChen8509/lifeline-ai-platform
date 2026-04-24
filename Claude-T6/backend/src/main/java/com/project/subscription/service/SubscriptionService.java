package com.project.subscription.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.subscription.model.dto.RuleDto;
import com.project.subscription.model.dto.SubscriptionDto;
import com.project.subscription.model.entity.SubscriptionRule;
import com.project.subscription.model.entity.WebhookSubscription;
import com.project.subscription.repository.SubscriptionRuleRepository;
import com.project.subscription.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final SubscriptionRuleRepository ruleRepository;
    private final ObjectMapper objectMapper;

    @Value("${webhook.quota.max-subscriptions-per-user:100}")
    private int maxSubscriptionsPerUser;

    @Transactional
    public SubscriptionDto.Response create(Long tenantId, Long userId, SubscriptionDto.CreateRequest req) {
        // 配额检查
        long count = subscriptionRepository.countByUserIdAndStatusNot(userId, (short) 2);
        if (count >= maxSubscriptionsPerUser) {
            throw new RuntimeException("已达订阅上限（" + count + "/" + maxSubscriptionsPerUser + "）");
        }

        WebhookSubscription sub = new WebhookSubscription();
        sub.setTenantId(tenantId);
        sub.setUserId(userId);
        sub.setEndpointId(req.getEndpointId());
        sub.setName(req.getName());
        sub.setSubscriptionType(req.getSubscriptionType());
        sub.setTargetId(req.getTargetId());
        sub.setDataPointIds(toJson(req.getDataPointIds()));
        sub = subscriptionRepository.save(sub);

        // 保存规则
        List<SubscriptionRule> rules = Collections.emptyList();
        if (req.getRules() != null && !req.getRules().isEmpty()) {
            rules = req.getRules().stream().map(r -> {
                SubscriptionRule rule = new SubscriptionRule();
                rule.setSubscriptionId(sub.getId());
                rule.setDataPointId(r.getDataPointId());
                rule.setRuleType(r.getRuleType());
                rule.setConditionJson(r.getConditionJson());
                rule.setCooldownSeconds(r.getCooldownSeconds() != null ? r.getCooldownSeconds() : 300);
                rule.setPriority(r.getPriority() != null ? r.getPriority() : 1);
                rule.setEnabled(r.getEnabled() != null ? r.getEnabled() : true);
                return ruleRepository.save(rule);
            }).collect(Collectors.toList());
        }

        return toResponse(sub, rules);
    }

    public Page<WebhookSubscription> list(Long tenantId, Long userId, int page, int size) {
        return subscriptionRepository.findByTenantIdAndUserIdAndStatusNot(
            tenantId, userId, (short) 2, PageRequest.of(page, size));
    }

    public SubscriptionDto.Response getDetail(Long id) {
        WebhookSubscription sub = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("订阅不存在"));
        List<SubscriptionRule> rules = ruleRepository.findBySubscriptionId(id);
        return toResponse(sub, rules);
    }

    @Transactional
    public SubscriptionDto.Response update(Long id, SubscriptionDto.UpdateRequest req) {
        WebhookSubscription sub = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("订阅不存在"));

        if (req.getName() != null) sub.setName(req.getName());
        subscriptionRepository.save(sub);

        // 更新规则：先删后增
        List<SubscriptionRule> rules = Collections.emptyList();
        if (req.getRules() != null) {
            ruleRepository.deleteBySubscriptionId(id);
            rules = req.getRules().stream().map(r -> {
                SubscriptionRule rule = new SubscriptionRule();
                rule.setSubscriptionId(id);
                rule.setDataPointId(r.getDataPointId());
                rule.setRuleType(r.getRuleType());
                rule.setConditionJson(r.getConditionJson());
                rule.setCooldownSeconds(r.getCooldownSeconds() != null ? r.getCooldownSeconds() : 300);
                rule.setPriority(r.getPriority() != null ? r.getPriority() : 1);
                rule.setEnabled(r.getEnabled() != null ? r.getEnabled() : true);
                return ruleRepository.save(rule);
            }).collect(Collectors.toList());
        } else {
            rules = ruleRepository.findBySubscriptionId(id);
        }

        return toResponse(sub, rules);
    }

    @Transactional
    public void toggleStatus(Long id, Short status) {
        WebhookSubscription sub = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("订阅不存在"));
        // 幂等：重复设置相同状态不报错
        if (sub.getStatus().equals((short) 2)) {
            throw new RuntimeException("已删除的订阅无法变更状态");
        }
        sub.setStatus(status);
        subscriptionRepository.save(sub);
    }

    @Transactional
    public void batchToggleStatus(SubscriptionDto.BatchStatusRequest req) {
        for (Long id : req.getIds()) {
            try {
                toggleStatus(id, req.getStatus());
            } catch (RuntimeException ignored) {
                // 批量操作中单条失败不中断
            }
        }
    }

    @Transactional
    public void delete(Long id) {
        WebhookSubscription sub = subscriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("订阅不存在"));
        sub.setStatus((short) 2); // 软删除
        subscriptionRepository.save(sub);
    }

    public List<RuleDto> checkConflicts(Long deviceId, List<Long> dataPointIds, Long excludeSubscriptionId) {
        // 查找同设备的活跃订阅规则
        List<WebhookSubscription> activeSubs = subscriptionRepository.findActiveByTargetAndType(deviceId, (short) 0);
        return activeSubs.stream()
            .filter(s -> !s.getId().equals(excludeSubscriptionId))
            .flatMap(s -> ruleRepository.findBySubscriptionId(s.getId()).stream())
            .filter(r -> r.getDataPointId() != null && (dataPointIds == null || dataPointIds.contains(r.getDataPointId())))
            .map(this::toRuleDto)
            .collect(Collectors.toList());
    }

    // --- helpers ---

    private SubscriptionDto.Response toResponse(WebhookSubscription sub, List<SubscriptionRule> rules) {
        var r = new SubscriptionDto.Response();
        r.setId(sub.getId());
        r.setName(sub.getName());
        r.setEndpointId(sub.getEndpointId());
        r.setSubscriptionType(sub.getSubscriptionType());
        r.setTargetId(sub.getTargetId());
        r.setDataPointIds(fromJson(sub.getDataPointIds()));
        r.setStatus(sub.getStatus());
        r.setStatusLabel(switch (sub.getStatus().intValue()) {
            case 0 -> "启用";
            case 1 -> "暂停";
            case 2 -> "已删除";
            default -> "未知";
        });
        r.setRules(rules.stream().map(this::toRuleDto).collect(Collectors.toList()));
        r.setCreatedAt(sub.getCreatedAt() != null ? sub.getCreatedAt().toString() : null);
        r.setUpdatedAt(sub.getUpdatedAt() != null ? sub.getUpdatedAt().toString() : null);
        return r;
    }

    private RuleDto toRuleDto(SubscriptionRule rule) {
        var dto = new RuleDto();
        dto.setId(rule.getId());
        dto.setDataPointId(rule.getDataPointId());
        dto.setRuleType(rule.getRuleType());
        dto.setConditionJson(rule.getConditionJson());
        dto.setCooldownSeconds(rule.getCooldownSeconds());
        dto.setPriority(rule.getPriority());
        dto.setEnabled(rule.getEnabled());
        return dto;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return null; }
    }

    private List<Long> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException e) { return null; }
    }
}
